package com.mlingofeed.ui.screens

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
import com.mlingofeed.webview.clearPageTranslations
import com.mlingofeed.webview.injectTranslationStyles
import com.mlingofeed.webview.prepareTranslationParagraphs
import com.mlingofeed.webview.updateParagraphTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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

    val currentTab: ReaderTab? get() = tabs.getOrNull(selectedIndex)

    fun ensureInitialTab(url: String) {
        if (tabs.isEmpty()) {
            tabs.add(ReaderTab(initialUrl = url))
            selectedIndex = 0
        }
    }

    val fontSize = app.settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, 100)
    val apiUrl = app.settingsManager.aiApiUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey = app.settingsManager.aiApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val model = app.settingsManager.aiModel.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val targetLang = app.settingsManager.translateTargetLang.stateIn(viewModelScope, SharingStarted.Eagerly, "Chinese")

    fun selectTab(index: Int) {
        selectedIndex = index
    }

    fun addTab(url: String) {
        tabs.add(ReaderTab(initialUrl = url))
        selectedIndex = tabs.size - 1
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.getOrNull(index)?.webView?.destroy()
        tabs.removeAt(index)
        if (selectedIndex >= tabs.size) selectedIndex = tabs.size - 1
        if (selectedIndex < 0) selectedIndex = 0
    }

    fun openDictionary(word: String) {
        selectedWord = word
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
            isTranslating = false
            translateProgress = ""
            clearPageTranslations(currentTab?.webView)
            return
        }
        if (apiKey.value.isBlank()) return
        isTranslating = true
        translateProgress = "Preparing..."

        viewModelScope.launch {
            val wv = currentTab?.webView
            if (wv == null) {
                isTranslating = false
                translateProgress = ""
                return@launch
            }
            val paraCount = withContext(Dispatchers.Main) {
                injectTranslationStyles(wv)
                suspendCancellableCoroutine<Int> { cont ->
                    prepareTranslationParagraphs(wv) { count ->
                        if (cont.isActive) cont.resume(count)
                    }
                }
            }
            if (paraCount == 0 || !isTranslating) {
                isTranslating = false
                withContext(Dispatchers.Main) {
                    wv.evaluateJavascript(
                        """
                        (function() {
                            var loadings = document.querySelectorAll('.__wr-translation-loading');
                            loadings.forEach(function(el) { el.remove(); });
                        })();
                        """.trimIndent(), null
                    )
                }
                translateProgress = ""
                return@launch
            }
            var currentIndex = 0
            while (isTranslating && isActive && currentIndex < paraCount) {
                val text = withContext(Dispatchers.Main) {
                    getTextByIndex(wv, currentIndex)
                }
                if (text == null) { currentIndex++; continue }
                translateProgress = "Translating ${currentIndex + 1}/$paraCount..."
                val translation = withContext(Dispatchers.IO) {
                    try {
                        app.chatRepository.translate(text, targetLang.value, apiUrl.value, apiKey.value, model.value)
                    } catch (e: Exception) { "Error: ${e.message}" }
                }
                withContext(Dispatchers.Main) {
                    updateParagraphTranslation(wv, currentIndex, translation)
                }
                currentIndex++
                kotlinx.coroutines.delay(50)
            }
            isTranslating = false
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(
                    """
                    (function() {
                        var loadings = document.querySelectorAll('.__wr-translation-loading');
                        loadings.forEach(function(el) { el.remove(); });
                    })();
                    """.trimIndent(), null
                )
            }
            translateProgress = ""
        }
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
