package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssArticleDetailViewModel(private val app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var article by mutableStateOf<RssArticle?>(null)
        private set
    var isFavorite by mutableStateOf(false)
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
    val translatedParagraphs = mutableStateMapOf<Int, String>()
    val translatingParagraphs = mutableStateMapOf<Int, Boolean>()

    val rssFontSize = app.settingsManager.rssFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 17f)

    private var initializedArticleId: Long? = null
    private var favoriteTouched = false

    fun ensureLoaded(articleId: Long) {
        if (initializedArticleId == articleId) return
        viewModelScope.launch {
            val loaded = repository.getArticleById(articleId) ?: return@launch
            initializedArticleId = articleId
            article = loaded
            if (!favoriteTouched) {
                isFavorite = loaded.isFavorite
            }
            if (!loaded.isRead) {
                repository.markAsRead(articleId)
            }
            if (loaded.content.isBlank()) {
                isLoadingContent = true
                val content = RssParser.fetchFullContent(loaded.link)
                repository.updateArticleContent(articleId, content)
                fullContent = content
                isLoadingContent = false
            } else {
                fullContent = loaded.content
            }
        }
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
            isTranslatingAll = false
            translateProgress = ""
            return
        }
        if (paragraphs.isEmpty()) return
        isTranslatingAll = true
        translateProgress = "Translating..."
        viewModelScope.launch {
            val settings = app.settingsManager.getAllSettings()
            if (settings["ai_api_key"].orEmpty().isBlank()) {
                isTranslatingAll = false
                translateProgress = ""
                return@launch
            }
            paragraphs.forEachIndexed { index, para ->
                if (!isTranslatingAll) return@launch
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
            isTranslatingAll = false
            translateProgress = ""
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
}
