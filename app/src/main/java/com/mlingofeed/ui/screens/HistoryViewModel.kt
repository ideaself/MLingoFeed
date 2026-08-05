package com.mlingofeed.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.historyRepository

    val history = repository.allHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
