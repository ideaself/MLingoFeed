package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.repository.RssParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ArticleFilterMode { ALL, UNREAD, FAVORITES }

@OptIn(ExperimentalCoroutinesApi::class)
class RssArticlesViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val subscriptions = repository.allSubscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isRefreshing by mutableStateOf(false)
        private set
    var filterMode by mutableStateOf(ArticleFilterMode.ALL)
        private set
    var showFilterMenu by mutableStateOf(false)
        private set

    private val subscriptionId = MutableStateFlow(0L)
    private val filter = MutableStateFlow(ArticleFilterMode.ALL)

    val articles: Flow<PagingData<RssArticle>> = combine(subscriptionId, filter) { id, mode -> id to mode }
        .flatMapLatest { (id, mode) ->
            if (id == 0L) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false),
                    pagingSourceFactory = {
                        repository.articlesPaged(
                            subscriptionId = id,
                            unreadOnly = mode == ArticleFilterMode.UNREAD,
                            favoritesOnly = mode == ArticleFilterMode.FAVORITES
                        )
                    }
                ).flow.cachedIn(viewModelScope)
            }
        }

    fun ensureInitialized(id: Long) {
        subscriptionId.value = id
    }

    fun updateFilterMode(mode: ArticleFilterMode) {
        filterMode = mode
        filter.value = mode
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
                val sub = repository.getSubscriptionById(subscriptionId)
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
