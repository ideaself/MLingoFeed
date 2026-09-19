package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.api.HttpClient
import com.mlingofeed.data.database.Bookmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class HomeViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.bookmarkRepository

    val bookmarks = repository.allBookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.getCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        if (!hasReordered) {
            syncInPlace(list)
            return
        }
        val ids = list.map { it.id }.toSet()
        orderedBookmarks.removeAll { it.id !in ids }
        val positions = orderedBookmarks.withIndex().associate { it.value.id to it.index }
        list.forEach { updated ->
            val index = positions[updated.id]
            if (index != null) {
                orderedBookmarks[index] = updated
            } else {
                orderedBookmarks.add(updated)
            }
        }
    }

    /**
     * Replaces only the entries whose data changed, instead of clear()+addAll(), so unrelated
     * bookmark-table writes (e.g. saved scroll positions) do not churn the whole list.
     */
    private fun syncInPlace(list: List<Bookmark>) {
        val sameOrder = orderedBookmarks.size == list.size &&
            orderedBookmarks.indices.all { orderedBookmarks[it].id == list[it].id }
        if (!sameOrder) {
            orderedBookmarks.clear()
            orderedBookmarks.addAll(list)
            return
        }
        list.forEachIndexed { index, bookmark ->
            if (orderedBookmarks[index] != bookmark) {
                orderedBookmarks[index] = bookmark
            }
        }
    }

    fun moveBookmark(fromId: Long, toId: Long) {
        val from = orderedBookmarks.indexOfFirst { it.id == fromId }
        val to = orderedBookmarks.indexOfFirst { it.id == toId }
        if (from == -1 || to == -1 || from == to) return
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
            val resolvedTitle = title.ifBlank { fetchPageTitle(url) }
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
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        HttpClient.shared.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use pageUrl
            Jsoup.parse(body).title().ifBlank { pageUrl }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        pageUrl
    }
}
