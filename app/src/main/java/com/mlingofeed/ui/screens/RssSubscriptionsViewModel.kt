package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssSubscription
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssSubscriptionsViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.allFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalUnread = repository.totalUnreadCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val unreadCounts = repository.unreadCountsBySubscription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var isRefreshing by mutableStateOf(false)
        private set
    var showAddDialog by mutableStateOf(false)
        private set
    var showDeleteDialog by mutableStateOf<RssSubscription?>(null)
        private set
    var editingSub by mutableStateOf<RssSubscription?>(null)
        private set
    var expandedFolders by mutableStateOf(setOf<Long>())
        private set

    fun openAddDialog() {
        showAddDialog = true
    }

    fun closeAddDialog() {
        showAddDialog = false
    }

    fun addSubscription(title: String, url: String, folderId: Long?) {
        viewModelScope.launch {
            val feedUrl = repository.resolveFeedUrl(url)
            val id = repository.addSubscription(title.ifBlank { feedUrl }, feedUrl, folderId)
            val articles = RssParser.parse(id, feedUrl)
            repository.insertArticles(articles)
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            try {
                repository.refreshAll()
                repository.cleanupOldArticles()
            } finally {
                isRefreshing = false
            }
        }
    }

    fun requestDelete(subscription: RssSubscription) {
        showDeleteDialog = subscription
    }

    fun cancelDelete() {
        showDeleteDialog = null
    }

    fun deleteSubscription(id: Long) {
        viewModelScope.launch {
            repository.deleteSubscription(id)
        }
        showDeleteDialog = null
    }

    fun requestEdit(subscription: RssSubscription) {
        editingSub = subscription
    }

    fun cancelEdit() {
        editingSub = null
    }

    fun updateSubscription(sub: RssSubscription, title: String, url: String, folderId: Long?) {
        viewModelScope.launch {
            val newUrl = repository.resolveFeedUrl(url)
            repository.updateSubscription(sub.id, title.trim(), newUrl, folderId)
            if (newUrl != sub.url) {
                val articles = RssParser.parse(sub.id, newUrl)
                repository.insertArticles(articles)
            }
        }
        editingSub = null
    }

    fun toggleFolder(id: Long) {
        expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id
    }

    fun markFolderAsRead(folderId: Long) {
        viewModelScope.launch { repository.markAllAsReadInFolder(folderId) }
    }
}
