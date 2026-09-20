package com.mlingofeed.ui.screens

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.database.RssTag
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.mlingofeed.R

@OptIn(ExperimentalCoroutinesApi::class)
class RssArticleDetailViewModel(private val app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var article by mutableStateOf<RssArticle?>(null)
        private set
    var isFavorite by mutableStateOf(false)
        private set
    var isSaved by mutableStateOf(false)
        private set
    var isLoadingContent by mutableStateOf(false)
        private set
    var fullContent by mutableStateOf<String?>(null)
        private set

    var showDictionary by mutableStateOf(false)
        private set
    var showTranslation by mutableStateOf(false)
        private set
    var showChat by mutableStateOf(false)
        private set
    var selectedWord by mutableStateOf("")
        private set
    var selectedSentence by mutableStateOf("")
        private set

    var isTranslatingAll by mutableStateOf(false)
        private set
    var translateProgress by mutableStateOf("")
        private set
    var articleNotFound by mutableStateOf(false)
        private set
    var showAiPanel by mutableStateOf(false)
        private set
    var aiPanelTitle by mutableStateOf("")
        private set
    var aiPanelContent by mutableStateOf("")
        private set
    var isAnalyzing by mutableStateOf(false)
        private set
    var summary by mutableStateOf("")
        private set
    var isSummarizing by mutableStateOf(false)
        private set
    val translatedParagraphs = mutableStateMapOf<Int, String>()
    val translatingParagraphs = mutableStateMapOf<Int, Boolean>()

    val rssFontSize = app.settingsManager.rssFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 17f)

    private val currentArticleId = MutableStateFlow(0L)

    val allTags = repository.allTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val articleTags = currentArticleId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.tagsForArticle(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var initializedArticleId: Long? = null
    private var favoriteTouched = false
    private var savedTouched = false
    private var translationGeneration = 0

    fun ensureLoaded(articleId: Long) {
        if (initializedArticleId == articleId) return
        currentArticleId.value = articleId
        viewModelScope.launch {
            val loaded = repository.getArticleById(articleId)
            if (loaded == null) {
                initializedArticleId = articleId
                articleNotFound = true
                return@launch
            }
            initializedArticleId = articleId
            articleNotFound = false
            article = loaded
            if (!favoriteTouched) {
                isFavorite = loaded.isFavorite
            }
            if (!savedTouched) {
                isSaved = loaded.isSaved
            }
            if (!loaded.isRead) {
                repository.markAsRead(articleId)
            }
            if (loaded.content.isBlank()) {
                fetchFullContent(articleId, loaded.link)
            } else {
                fullContent = loaded.content
            }
        }
    }

    /** Re-fetches the body, e.g. after the extractor improved or when the page markup changed. */
    fun reloadContent() {
        val current = article ?: return
        if (isLoadingContent) return
        viewModelScope.launch { fetchFullContent(current.id, current.link) }
    }

    private suspend fun fetchFullContent(articleId: Long, link: String) {
        isLoadingContent = true
        try {
            val content = RssParser.fetchFullContent(link)
            if (content.isNotBlank()) {
                repository.updateArticleContent(articleId, content)
                fullContent = content
            } else {
                Toast.makeText(app, app.getString(R.string.content_reload_failed), Toast.LENGTH_SHORT).show()
            }
        } finally {
            isLoadingContent = false
        }
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

    fun attachTag(tagId: Long) {
        val articleId = currentArticleId.value
        if (articleId == 0L) return
        viewModelScope.launch { repository.addTagToArticle(articleId, tagId) }
    }

    fun detachTag(tagId: Long) {
        val articleId = currentArticleId.value
        if (articleId == 0L) return
        viewModelScope.launch { repository.removeTagFromArticle(articleId, tagId) }
    }

    fun createTag(name: String) {
        val articleId = currentArticleId.value
        val trimmed = name.trim()
        if (articleId == 0L || trimmed.isBlank()) return
        viewModelScope.launch {
            val tagId = repository.addTag(trimmed)
            if (tagId > 0) {
                repository.addTagToArticle(articleId, tagId)
            }
        }
    }

    fun toggleFavorite(articleId: Long) {
        favoriteTouched = true
        viewModelScope.launch {
            repository.toggleFavorite(articleId)
            val updated = repository.getArticleById(articleId)
            if (updated != null) {
                article = updated
                isFavorite = updated.isFavorite
            } else {
                isFavorite = !isFavorite
            }
        }
    }

    fun toggleSaved(articleId: Long) {
        savedTouched = true
        val newValue = !isSaved
        isSaved = newValue
        article = article?.copy(isSaved = newValue)
        viewModelScope.launch { repository.setSaved(articleId, newValue) }
    }

    fun translateParagraph(index: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.length < 3) return
        if (translatingParagraphs[index] == true) return
        translatingParagraphs[index] = true
        viewModelScope.launch {
            val translation = translateText(trimmed)
            if (translation == null) {
                translatingParagraphs.remove(index)
                return@launch
            }
            translatedParagraphs[index] = translation
            translatingParagraphs.remove(index)
        }
    }

    fun translateAll(paragraphs: List<String>) {
        if (isTranslatingAll) {
            translationGeneration++
            isTranslatingAll = false
            translateProgress = ""
            return
        }
        if (paragraphs.isEmpty()) return
        isTranslatingAll = true
        translateProgress = "Translating..."
        val generation = ++translationGeneration
        viewModelScope.launch {
            val settings = app.settingsManager.getAllSettings()
            if (settings["ai_api_key"].orEmpty().isBlank()) {
                if (generation == translationGeneration) {
                    isTranslatingAll = false
                    translateProgress = ""
                }
                return@launch
            }
            paragraphs.forEachIndexed { index, para ->
                if (generation != translationGeneration || !isTranslatingAll) return@launch
                val trimmed = para.trim()
                if (trimmed.length < 3) return@forEachIndexed
                if (translatingParagraphs[index] == true) return@forEachIndexed
                translateProgress = "Translating ${index + 1}/${paragraphs.size}..."
                translatingParagraphs[index] = true
                val translation = translateText(trimmed, settings)
                if (translation != null) {
                    translatedParagraphs[index] = translation
                }
                translatingParagraphs.remove(index)
                kotlinx.coroutines.delay(50)
            }
            if (generation == translationGeneration) {
                isTranslatingAll = false
                translateProgress = ""
            }
        }
    }

    private suspend fun translateText(
        text: String,
        settings: Map<String, String>? = null
    ): String? {
        val values = settings ?: app.settingsManager.getAllSettings()
        val apiKey = values["ai_api_key"].orEmpty()
        if (apiKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                app.chatRepository.translate(
                    text = text,
                    targetLang = values["translate_target_lang"] ?: "Chinese",
                    apiUrl = values["ai_api_url"].orEmpty(),
                    apiKey = apiKey,
                    model = values["ai_model"].orEmpty()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    fun analyzeDifficulty() {
        runAiTool(
            title = app.getString(R.string.difficulty_analysis),
            format = ::formatDifficulty
        ) { text, apiUrl, apiKey, model ->
            app.chatRepository.analyzeDifficulty(text, apiUrl, apiKey, model)
        }
    }

    fun extractCollocations() {
        runAiTool(
            title = app.getString(R.string.collocations_idioms),
            format = ::formatCollocations
        ) { text, apiUrl, apiKey, model ->
            app.chatRepository.detectCollocations(text, apiUrl, apiKey, model)
        }
    }

    fun dismissAiPanel() {
        showAiPanel = false
    }

    fun summarizeArticle() {
        if (isSummarizing) return
        val text = (fullContent?.takeIf { it.isNotBlank() } ?: article?.description.orEmpty()).trim()
        if (text.isBlank()) return
        isSummarizing = true
        summary = ""
        viewModelScope.launch {
            val settings = app.settingsManager.getAllSettings()
            val apiKey = settings["ai_api_key"].orEmpty()
            if (apiKey.isBlank()) {
                summary = app.getString(R.string.please_configure_ai_api_key_in_settings)
                isSummarizing = false
                return@launch
            }
            summary = withContext(Dispatchers.IO) {
                try {
                    app.chatRepository.summarize(
                        text = text,
                        apiUrl = settings["ai_api_url"].orEmpty(),
                        apiKey = apiKey,
                        model = settings["ai_model"].orEmpty()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            isSummarizing = false
        }
    }

    private fun runAiTool(
        title: String,
        format: (String) -> String,
        call: suspend (text: String, apiUrl: String, apiKey: String, model: String) -> String
    ) {
        val text = (fullContent?.takeIf { it.isNotBlank() } ?: article?.description.orEmpty()).trim()
        if (text.isBlank()) return
        aiPanelTitle = title
        aiPanelContent = ""
        isAnalyzing = true
        showAiPanel = true
        viewModelScope.launch {
            val settings = app.settingsManager.getAllSettings()
            val apiKey = settings["ai_api_key"].orEmpty()
            if (apiKey.isBlank()) {
                aiPanelContent = "Please configure AI API Key in Settings"
                isAnalyzing = false
                return@launch
            }
            val raw = withContext(Dispatchers.IO) {
                try {
                    call(
                        text,
                        settings["ai_api_url"].orEmpty(),
                        apiKey,
                        settings["ai_model"].orEmpty()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            aiPanelContent = format(raw)
            isAnalyzing = false
        }
    }

    private fun formatDifficulty(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            buildString {
                append("CEFR: ").append(obj.optString("cefrLevel", "-"))
                append("  ·  ").append(obj.optString("difficulty", "-")).append('\n')
                append("Words: ").append(obj.optString("wordCount", "-"))
                append("  ·  Avg sentence: ").append(obj.optString("avgSentenceLength", "-")).append('\n')
                val suggestions = obj.optJSONArray("suggestions")
                if (suggestions != null && suggestions.length() > 0) {
                    append('\n').append("Suggestions:").append('\n')
                    for (i in 0 until suggestions.length()) {
                        append("• ").append(suggestions.optString(i)).append('\n')
                    }
                }
                val vocabulary = obj.optJSONArray("keyVocabulary")
                if (vocabulary != null && vocabulary.length() > 0) {
                    append('\n').append("Key vocabulary:").append('\n')
                    for (i in 0 until vocabulary.length()) {
                        append("• ").append(renderVocabularyItem(vocabulary.opt(i))).append('\n')
                    }
                }
            }.trim()
        } catch (_: Exception) {
            raw
        }
    }

    private fun renderVocabularyItem(item: Any?): String = when (item) {
        null -> ""
        is JSONObject -> {
            val word = item.optString("word").ifBlank { item.optString("term") }
            val meaning = item.optString("definition").ifBlank { item.optString("meaning") }
            listOf(word, meaning).filter { it.isNotBlank() }.joinToString(" — ")
        }
        else -> item.toString()
    }

    private fun formatCollocations(raw: String): String {
        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) return raw
            buildString {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val phrase = obj.optString("phrase")
                    if (phrase.isBlank()) continue
                    append("• ").append(phrase)
                    val type = obj.optString("type")
                    if (type.isNotBlank()) append("  [").append(type).append(']')
                    append('\n')
                    val meaning = obj.optString("meaning")
                    if (meaning.isNotBlank()) append("   ").append(meaning).append('\n')
                    val example = obj.optString("example")
                    if (example.isNotBlank()) append("   e.g. ").append(example).append('\n')
                    if (i < array.length() - 1) append('\n')
                }
            }.trim()
        } catch (_: Exception) {
            raw
        }
    }
}
