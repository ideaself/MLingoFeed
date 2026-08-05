package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ArticleFilterMode { ALL, UNREAD, FAVORITES }

class RssArticlesViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var isRefreshing by mutableStateOf(false)
        private set
    var filterMode by mutableStateOf(ArticleFilterMode.ALL)
        private set
    var showFilterMenu by mutableStateOf(false)
        private set

    fun updateFilterMode(mode: ArticleFilterMode) {
        filterMode = mode
        showFilterMenu = false
    }

    fun toggleFilterMenu() {
        showFilterMenu = !showFilterMenu
    }

    fun dismissFilterMenu() {
        showFilterMenu = false
    }

    fun markAllAsRead(subscriptionId: Long) {
        viewModelScope.launch { repository.markAllAsRead(subscriptionId) }
    }

    fun refresh(subscriptionId: Long) {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            try {
                val sub = repository.allSubscriptions.first().find { it.id == subscriptionId }
                if (sub != null) {
                    val newArticles = RssParser.parse(sub.id, sub.url)
                    repository.insertArticles(newArticles)
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun toggleReadStatus(articleId: Long) {
        viewModelScope.launch { repository.toggleReadStatus(articleId) }
    }

    fun toggleFavorite(articleId: Long) {
        viewModelScope.launch { repository.toggleFavorite(articleId) }
    }
}
