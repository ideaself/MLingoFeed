package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.WordBookEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WordBookViewModel(private val app: WebReaderApp) : ViewModel() {

    private val repository = app.wordBookRepository
    private val queryFlow = MutableStateFlow("")

    val allWords = repository.allWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueWords = repository.dueWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val masteredWords = repository.masteredWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults: StateFlow<List<WordBookEntry>> = queryFlow
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.searchWords(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedTab by mutableIntStateOf(0)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var expandedWord by mutableStateOf<String?>(null)
        private set
    var showExportDialog by mutableStateOf(false)
        private set
    var importResult by mutableStateOf<Int?>(null)
        private set

    fun selectTab(index: Int) {
        selectedTab = index
    }

    fun onQueryChange(value: String) {
        searchQuery = value
        queryFlow.value = value
    }

    fun clearQuery() {
        searchQuery = ""
        queryFlow.value = ""
    }

    fun toggleExpanded(word: String) {
        expandedWord = if (expandedWord == word) null else word
    }

    fun openExportDialog() {
        showExportDialog = true
    }

    fun dismissExportDialog() {
        showExportDialog = false
    }

    fun deleteWord(word: String) {
        viewModelScope.launch { repository.deleteWord(word) }
    }

    fun importWords(uri: Uri) {
        viewModelScope.launch {
            val imported = try {
                withContext(Dispatchers.IO) {
                    val content = app.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: return@withContext -1
                    repository.importWords(content)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                -1
            }
            importResult = imported
        }
    }

    fun consumeImportResult() {
        importResult = null
    }

    fun toggleMastered(entry: WordBookEntry) {
        viewModelScope.launch { repository.toggleMastered(entry.word) }
    }
}
