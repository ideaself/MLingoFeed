package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class HomeViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.bookmarkRepository

    val bookmarks = repository.allBookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = repository.getCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var showAddDialog by mutableStateOf(false)
        private set
    var selectedCategory by mutableStateOf("")
        private set

    val orderedBookmarks = mutableStateListOf<Bookmark>()
    var hasReordered by mutableStateOf(false)
        private set

    var bookmarkToDelete by mutableStateOf<Bookmark?>(null)
        private set
    var bookmarkToCategoryChange by mutableStateOf<Bookmark?>(null)
        private set

    fun syncOrdered(list: List<Bookmark>) {
        orderedBookmarks.clear()
        orderedBookmarks.addAll(list)
    }

    fun moveBookmark(from: Int, to: Int) {
        if (from !in orderedBookmarks.indices || to !in orderedBookmarks.indices) return
        orderedBookmarks.add(to, orderedBookmarks.removeAt(from))
        hasReordered = true
    }

    fun saveOrder() {
        viewModelScope.launch {
            repository.updatePositions(orderedBookmarks.toList())
            hasReordered = false
        }
    }

    fun selectCategory(category: String) {
        selectedCategory = if (selectedCategory == category) "" else category
    }

    fun clearCategory() {
        selectedCategory = ""
    }

    fun openAddDialog() {
        showAddDialog = true
    }

    fun dismissAddDialog() {
        showAddDialog = false
    }

    fun addBookmark(url: String, title: String, category: String) {
        viewModelScope.launch {
            val finalTitle = title.ifBlank { url }
            val resolvedTitle = if (finalTitle.isBlank()) fetchPageTitle(url) else finalTitle
            repository.insert(
                Bookmark(
                    title = resolvedTitle,
                    url = url,
                    position = orderedBookmarks.size,
                    category = category
                )
            )
        }
    }

    fun requestDelete(bookmark: Bookmark) {
        bookmarkToDelete = bookmark
    }

    fun cancelDelete() {
        bookmarkToDelete = null
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.delete(bookmark) }
        bookmarkToDelete = null
    }

    fun requestCategoryChange(bookmark: Bookmark) {
        bookmarkToCategoryChange = bookmark
    }

    fun cancelCategoryChange() {
        bookmarkToCategoryChange = null
    }

    fun changeCategory(bookmark: Bookmark, category: String) {
        viewModelScope.launch { repository.update(bookmark.copy(category = category)) }
        bookmarkToCategoryChange = null
    }
}

private suspend fun fetchPageTitle(pageUrl: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext pageUrl
        val doc = Jsoup.parse(body)
        doc.title()?.ifBlank { null } ?: pageUrl
    } catch (_: Exception) {
        pageUrl
    }
}
