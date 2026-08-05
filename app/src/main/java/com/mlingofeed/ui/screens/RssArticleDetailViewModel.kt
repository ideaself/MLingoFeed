package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssArticleDetailViewModel(private val app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val apiUrl = app.settingsManager.aiApiUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey = app.settingsManager.aiApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val model = app.settingsManager.aiModel.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val targetLang = app.settingsManager.translateTargetLang.stateIn(viewModelScope, SharingStarted.Eagerly, "Chinese")

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

    var fontSize by mutableFloatStateOf(17f)
        private set

    private var initializedArticleId: Long? = null

    fun ensureLoaded(articleId: Long) {
        if (initializedArticleId == articleId) return
        initializedArticleId = articleId
        viewModelScope.launch {
            val loaded = repository.getArticleById(articleId)
            article = loaded
            isFavorite = loaded?.isFavorite ?: false
            if (loaded != null && !loaded.isRead) {
                repository.markAsRead(articleId)
            }
            if (loaded != null && loaded.content.isBlank()) {
                isLoadingContent = true
                val content = RssParser.fetchFullContent(loaded.link)
                repository.updateArticleContent(articleId, content)
                fullContent = content
                isLoadingContent = false
            } else if (loaded != null) {
                fullContent = loaded.content
            }
        }
    }

    fun updateFontSize(value: Float) {
        fontSize = value
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
        viewModelScope.launch {
            repository.toggleFavorite(articleId)
            isFavorite = !isFavorite
        }
    }

    fun translateParagraph(index: Int, text: String) {
        if (apiKey.value.isBlank()) return
        val trimmed = text.trim()
        if (trimmed.length < 3) return
        if (translatingParagraphs[index] == true) return
        translatingParagraphs[index] = true
        viewModelScope.launch {
            val translation = withContext(Dispatchers.IO) {
                try {
                    app.chatRepository.translate(trimmed, targetLang.value, apiUrl.value, apiKey.value, model.value)
                } catch (e: Exception) { "Error: ${e.message}" }
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
        if (apiKey.value.isBlank() || paragraphs.isEmpty()) return
        isTranslatingAll = true
        translateProgress = "Translating..."
        viewModelScope.launch {
            paragraphs.forEachIndexed { index, para ->
                if (!isTranslatingAll) return@launch
                val trimmed = para.trim()
                if (trimmed.length < 3) return@forEachIndexed
                translateProgress = "Translating ${index + 1}/${paragraphs.size}..."
                translatingParagraphs[index] = true
                val translation = withContext(Dispatchers.IO) {
                    try {
                        app.chatRepository.translate(trimmed, targetLang.value, apiUrl.value, apiKey.value, model.value)
                    } catch (e: Exception) { "Error: ${e.message}" }
                }
                translatedParagraphs[index] = translation
                translatingParagraphs.remove(index)
                kotlinx.coroutines.delay(50)
            }
            isTranslatingAll = false
            translateProgress = ""
        }
    }
}
