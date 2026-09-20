package com.mlingofeed.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.Bookmark
import com.mlingofeed.webview.ReaderTab
import com.mlingofeed.webview.applyReadingAppearance
import com.mlingofeed.webview.clearPageTranslations
import com.mlingofeed.webview.clearSentenceHighlight
import com.mlingofeed.webview.clearTranslationPlaceholders
import com.mlingofeed.webview.highlightSavedWords
import com.mlingofeed.webview.highlightSentence
import com.mlingofeed.webview.injectTranslationStyles
import com.mlingofeed.webview.prepareTranslationParagraphs
import com.mlingofeed.webview.updateParagraphTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference
import java.util.Locale

class ReaderViewModel(
    private val app: WebReaderApp
) : ViewModel() {

    val tabs = mutableStateListOf<ReaderTab>()
    var selectedIndex by mutableIntStateOf(0)
        private set
    var showDictionary by mutableStateOf(false)
        private set
    var showTranslation by mutableStateOf(false)
        private set
    var showChat by mutableStateOf(false)
        private set
    var isTranslating by mutableStateOf(false)
        private set
    var translateProgress by mutableStateOf("")
        private set
    var selectedWord by mutableStateOf("")
        private set
    var selectedSentence by mutableStateOf("")
        private set
    var wordSelectionEnabled by mutableStateOf(true)
        private set

    val currentTab: ReaderTab? get() = tabs.getOrNull(selectedIndex)

    fun ensureInitialTab(url: String) {
        if (tabs.isNotEmpty()) return
        viewModelScope.launch {
            val restored = loadPersistedTabs()
            if (restored.isNotEmpty()) {
                tabs.addAll(restored)
                val existingIndex = restored.indexOfFirst { it.url == url }
                selectedIndex = if (existingIndex >= 0) {
                    existingIndex
                } else {
                    tabs.add(ReaderTab(initialUrl = url))
                    tabs.size - 1
                }
            } else {
                tabs.add(ReaderTab(initialUrl = url))
                selectedIndex = 0
            }
            persistTabs()
        }
    }

    private suspend fun loadPersistedTabs(): List<ReaderTab> {
        return try {
            val (json, _) = app.settingsManager.getReaderTabs()
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val tabUrl = obj.optString("url")
                if (tabUrl.isBlank() || tabUrl == "about:blank") {
                    null
                } else {
                    ReaderTab(
                        initialUrl = tabUrl,
                        initialTitle = obj.optString("title").ifBlank { "Loading..." }
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private var persistJob: Job? = null

    private fun persistTabs() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            val array = JSONArray()
            tabs.forEach { tab ->
                if (tab.url.isNotBlank() && tab.url != "about:blank") {
                    array.put(
                        JSONObject().apply {
                            put("url", tab.url)
                            put("title", tab.title)
                        }
                    )
                }
            }
            app.settingsManager.setReaderTabs(array.toString(), selectedIndex)
        }
    }

    val fontSize = app.settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)
    val desktopMode = app.settingsManager.readerDesktopMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val blockImages = app.settingsManager.readerBlockImages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val highlightWords = app.settingsManager.readerHighlightWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lineHeight = app.settingsManager.readerLineHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.6f)
    val serifFont = app.settingsManager.readerSerifFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val ttsSpeed = app.settingsManager.readerTtsSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)
    val ttsVoice = app.settingsManager.readerTtsVoice.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "us")
    val darkWeb = app.settingsManager.readerDarkWeb.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val purify = app.settingsManager.readerPurify.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var recordedUrls = mutableMapOf<Long, String>()
    private val restoredScrollKeys = mutableSetOf<String>()
    private val urlsWithRestoredScroll = mutableSetOf<String>()
    private var hostRef: WeakReference<Any>? = null
    private var translationGeneration = 0

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    @Volatile
    private var currentSentences: List<String> = emptyList()
    var isSpeaking by mutableStateOf(false)
        private set
    var linkMenuUrl by mutableStateOf<String?>(null)
        private set

    /**
     * Called with the current Activity. WebViews are bound to the Activity that created them, so
     * when the host is recreated (rotation, theme change) they must be dropped instead of being
     * re-parented into the new Activity. The screen recreates them from [ReaderTab.url].
     */
    fun attachHost(host: Any) {
        val previous = hostRef?.get()
        if (previous === host) return
        if (previous != null) {
            tabs.forEach { tab ->
                saveScrollPosition(tab)
                tab.webView?.destroy()
                tab.webView = null
            }
        }
        hostRef = WeakReference(host)
    }

    fun pauseWebViews() {
        tabs.forEach { it.webView?.onPause() }
    }

    fun resumeWebViews() {
        tabs.forEach { it.webView?.onResume() }
    }

    fun selectTab(index: Int) {
        selectedIndex = index
        persistTabs()
    }

    fun addTab(url: String) {
        tabs.add(ReaderTab(initialUrl = url))
        selectedIndex = tabs.size - 1
        persistTabs()
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.getOrNull(index)?.let { tab ->
            saveScrollPosition(tab)
            tab.webView?.destroy()
            tab.webView = null
            recordedUrls.remove(tab.id)
            restoredScrollKeys.removeAll { it.startsWith("${tab.id}:") }
        }
        tabs.removeAt(index)
        if (selectedIndex >= tabs.size) selectedIndex = tabs.size - 1
        if (selectedIndex < 0) selectedIndex = 0
        persistTabs()
    }

    fun onPageLoaded(tab: ReaderTab, url: String?, title: String?) {
        // In-page navigation (link clicks, redirects) changes the WebView URL; keep the tab in
        // sync so history, bookmarks and scroll positions refer to the page actually shown.
        val loadedUrl = url?.takeIf { it.isNotBlank() && it != "about:blank" }
        if (loadedUrl != null && loadedUrl != tab.url) {
            tab.url = loadedUrl
        }
        val currentUrl = loadedUrl ?: tab.url
        restoreScrollPosition(tab, currentUrl)
        if (title.isNullOrBlank() || title == "Loading...") return
        if (recordedUrls[tab.id] == currentUrl && tab.title == title) return
        recordedUrls[tab.id] = currentUrl
        tab.title = title
        viewModelScope.launch { app.historyRepository.recordVisit(title, currentUrl) }
        persistTabs()
        applyReadingAppearance(tab.webView, lineHeight.value, serifFont.value, darkWeb.value, purify.value)
        if (highlightWords.value) {
            viewModelScope.launch {
                val words = app.wordBookRepository.getWordTexts()
                highlightSavedWords(tab.webView, words, true)
            }
        }
    }

    /** Restores the saved reading position the first time a bookmarked URL loads in a tab. */
    private fun restoreScrollPosition(tab: ReaderTab, url: String) {
        if (url.isBlank() || url == "about:blank") return
        if (!restoredScrollKeys.add("${tab.id}:$url")) return
        viewModelScope.launch {
            val position = app.bookmarkRepository.getScrollPosition(url)
            if (position == null || position <= 0) return@launch
            urlsWithRestoredScroll.add(url)
            tab.webView?.postDelayed({
                if (tab.url == url) {
                    tab.webView?.scrollTo(0, position)
                }
            }, 250)
        }
    }

    private fun saveScrollPosition(tab: ReaderTab) {
        val webView = tab.webView ?: return
        val scrollY = webView.scrollY
        val url = tab.url
        // Writes are only useful for bookmarked pages; skip pages the user never scrolled unless
        // a position was restored for this URL, so scrolling back to the top clears it.
        if (scrollY <= 0 && url !in urlsWithRestoredScroll) return
        app.applicationScope.launch { app.bookmarkRepository.updateScrollPosition(url, scrollY) }
    }

    override fun onCleared() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        tabs.forEach { tab ->
            saveScrollPosition(tab)
            tab.webView?.destroy()
            tab.webView = null
        }
        tabs.clear()
        super.onCleared()
    }

    fun openDictionary(word: String, sentence: String = "") {
        selectedWord = word
        if (sentence.isNotBlank()) {
            selectedSentence = sentence
        }
        showDictionary = true
    }

    fun openTranslation(sentence: String) {
        selectedSentence = sentence
        showTranslation = true
    }

    fun openChat(text: String) {
        selectedSentence = text
        showChat = true
    }

    fun dismissDictionary() {
        showDictionary = false
    }

    fun dismissTranslation() {
        showTranslation = false
    }

    fun dismissChat() {
        showChat = false
    }

    fun toggleWordSelection() {
        wordSelectionEnabled = !wordSelectionEnabled
    }

    fun setDesktopMode(enabled: Boolean) {
        viewModelScope.launch { app.settingsManager.setReaderDesktopMode(enabled) }
    }

    fun setBlockImages(enabled: Boolean) {
        viewModelScope.launch { app.settingsManager.setReaderBlockImages(enabled) }
    }

    fun setHighlightWords(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.setReaderHighlightWords(enabled)
            currentTab?.webView?.let { webView ->
                val words = if (enabled) app.wordBookRepository.getWordTexts() else emptyList()
                highlightSavedWords(webView, words, enabled)
            }
        }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch {
            app.settingsManager.setReaderLineHeight(value)
            currentTab?.webView?.let { applyReadingAppearance(it, value, serifFont.value, darkWeb.value, purify.value) }
        }
    }

    fun setSerifFont(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.setReaderSerifFont(enabled)
            currentTab?.webView?.let { applyReadingAppearance(it, lineHeight.value, enabled, darkWeb.value, purify.value) }
        }
    }

    fun showLinkMenu(url: String) {
        if (url.isNotBlank()) {
            linkMenuUrl = url
        }
    }

    fun dismissLinkMenu() {
        linkMenuUrl = null
    }

    fun openLinkInNewTab(url: String) {
        linkMenuUrl = null
        addTab(url)
    }

    fun setDarkWeb(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.setReaderDarkWeb(enabled)
            currentTab?.webView?.let { applyReadingAppearance(it, lineHeight.value, serifFont.value, enabled, purify.value) }
        }
    }

    fun setPurify(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.setReaderPurify(enabled)
            currentTab?.webView?.let { applyReadingAppearance(it, lineHeight.value, serifFont.value, darkWeb.value, enabled) }
        }
    }

    fun toggleReadAloud() {
        if (isSpeaking) {
            textToSpeech?.stop()
            isSpeaking = false
            clearSentenceHighlight(currentTab?.webView)
            return
        }
        val webView = currentTab?.webView ?: return
        webView.evaluateJavascript("document.body ? document.body.innerText : ''") { value ->
            val text = try {
                (JSONTokener(value).nextValue() as? String).orEmpty()
            } catch (_: Exception) {
                ""
            }.trim()
            if (text.isNotEmpty()) {
                speak(text)
            }
        }
    }

    private fun speak(text: String) {
        val engine = ensureTts()
        if (engine == null) {
            // Engine still initializing; speak as soon as it is ready.
            pendingSpeech = text
            return
        }
        engine.stop()
        clearSentenceHighlight(currentTab?.webView)
        engine.setSpeechRate(ttsSpeed.value)
        engine.language = if (ttsVoice.value == "uk") Locale.UK else Locale.US
        isSpeaking = true
        currentSentences = splitSentences(text)
        if (currentSentences.isEmpty()) {
            isSpeaking = false
            return
        }
        currentSentences.forEachIndexed { index, sentence ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(sentence.take(3500), mode, null, "reader_${index}_${currentSentences.lastIndex}")
        }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?。！？])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length > 1 }

    private fun ensureTts(): TextToSpeech? {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(app) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    textToSpeech?.language = Locale.getDefault()
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            val parts = utteranceId?.split("_").orEmpty()
                            val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                            val sentence = currentSentences.getOrNull(index) ?: return
                            val webView = currentTab?.webView ?: return
                            viewModelScope.launch(Dispatchers.Main) {
                                highlightSentence(webView, sentence)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val parts = utteranceId?.split("_").orEmpty()
                            if (parts.size == 3 && parts[1] == parts[2]) {
                                isSpeaking = false
                                val webView = currentTab?.webView
                                viewModelScope.launch(Dispatchers.Main) {
                                    clearSentenceHighlight(webView)
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            isSpeaking = false
                        }
                    })
                    pendingSpeech?.let { pending ->
                        pendingSpeech = null
                        speak(pending)
                    }
                }
            }
        }
        return if (ttsReady) textToSpeech else null
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val tab = currentTab ?: return@launch
            val url = tab.url
            val isBookmarked = app.bookmarkRepository.getBookmarkByUrl(url) != null
            if (isBookmarked) {
                app.bookmarkRepository.deleteByUrl(url)
            } else {
                app.bookmarkRepository.insert(Bookmark(title = tab.title, url = url))
            }
        }
    }

    fun startTranslation() {
        if (isTranslating) {
            translationGeneration++
            isTranslating = false
            translateProgress = ""
            clearPageTranslations(currentTab?.webView)
            return
        }
        isTranslating = true
        translateProgress = "Preparing..."
        val generation = ++translationGeneration

        viewModelScope.launch {
            try {
                val wv = currentTab?.webView ?: return@launch
                val settings = app.settingsManager.getAllSettings()
                val apiKey = settings["ai_api_key"].orEmpty()
                if (apiKey.isBlank()) {
                    translateProgress = "Configure AI API Key in Settings"
                    return@launch
                }
                val apiUrl = settings["ai_api_url"].orEmpty()
                val model = settings["ai_model"].orEmpty()
                val targetLang = settings["translate_target_lang"] ?: "Chinese"

                val paraCount = withContext(Dispatchers.Main) {
                    injectTranslationStyles(wv)
                    withTimeoutOrNull(JS_CALLBACK_TIMEOUT_MS) {
                        suspendCancellableCoroutine<Int> { cont ->
                            prepareTranslationParagraphs(wv) { count ->
                                if (cont.isActive) cont.resume(count)
                            }
                        }
                    } ?: 0
                }
                if (paraCount == 0 || !isTranslating || generation != translationGeneration) return@launch
                var currentIndex = 0
                while (isTranslating && isActive && currentIndex < paraCount && generation == translationGeneration) {
                    val text = withContext(Dispatchers.Main) {
                        withTimeoutOrNull(JS_CALLBACK_TIMEOUT_MS) { getTextByIndex(wv, currentIndex) }
                    }
                    if (text == null) { currentIndex++; continue }
                    translateProgress = "Translating ${currentIndex + 1}/$paraCount..."
                    val translation = withContext(Dispatchers.IO) {
                        try {
                            app.chatRepository.translate(text, targetLang, apiUrl, apiKey, model)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    }
                    withContext(Dispatchers.Main) {
                        updateParagraphTranslation(wv, currentIndex, translation)
                    }
                    currentIndex++
                    kotlinx.coroutines.delay(50)
                }
            } finally {
                if (generation == translationGeneration) {
                    isTranslating = false
                    translateProgress = ""
                    withContext(NonCancellable + Dispatchers.Main) {
                        clearTranslationPlaceholders(currentTab?.webView)
                    }
                }
            }
        }
    }

    private companion object {
        const val JS_CALLBACK_TIMEOUT_MS = 5_000L
    }
}

private suspend fun getTextByIndex(webView: WebView?, index: Int): String? {
    if (webView == null) return null
    return suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(
            """
            (function() {
                var texts = window.__wrTexts || [];
                var text = texts[${index}];
                if (!text || text.length === 0) return null;
                if (text.length > 2000) text = text.substring(0, 2000);
                return JSON.stringify(text);
            })();
            """.trimIndent()
        ) { value ->
            if (!cont.isActive) return@evaluateJavascript
            if (value != null && value != "null") {
                try {
                    val cleaned = value.trim('"').replace("\\\"", "\"").replace("\\n", "\n")
                    cont.resume(cleaned)
                } catch (_: Exception) {
                    cont.resume(null)
                }
            } else {
                cont.resume(null)
            }
        }
    }
}
