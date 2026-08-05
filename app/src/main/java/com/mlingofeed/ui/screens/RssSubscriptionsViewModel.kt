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

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders = repository.allFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val totalUnread = repository.totalUnreadCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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

    init {
        viewModelScope.launch {
            repository.cleanupDuplicates()
            repository.initDefaultSubscriptions()
        }
    }

    fun openAddDialog() {
        showAddDialog = true
    }

    fun closeAddDialog() {
        showAddDialog = false
    }

    fun addSubscription(title: String, url: String, folderId: Long?) {
        viewModelScope.launch {
            val id = repository.addSubscription(title.ifBlank { url }, url, folderId)
            val articles = RssParser.parse(id, url)
            repository.insertArticles(articles)
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            repository.refreshAll()
            repository.cleanupOldArticles()
            isRefreshing = false
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
            repository.updateSubscription(sub.id, title.trim(), url.trim(), folderId)
            val articles = RssParser.parse(sub.id, url.trim())
            repository.insertArticles(articles)
        }
        editingSub = null
    }

    fun toggleFolder(id: Long) {
        expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id
    }
}
