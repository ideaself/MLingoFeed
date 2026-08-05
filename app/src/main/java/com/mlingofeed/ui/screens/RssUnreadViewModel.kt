package com.mlingofeed.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssUnreadViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val unreadArticles = repository.unreadArticles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }
}
