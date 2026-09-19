package com.mlingofeed.data.repository

import androidx.room.withTransaction
import com.mlingofeed.data.database.AppDatabase
import com.mlingofeed.data.database.Bookmark
import com.mlingofeed.data.database.BookmarkDao
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao, private val database: AppDatabase) {
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    suspend fun getBookmarkByUrl(url: String): Bookmark? {
        return bookmarkDao.getBookmarkByUrl(url)
    }

    suspend fun insert(bookmark: Bookmark): Long {
        return bookmarkDao.insert(bookmark)
    }

    suspend fun update(bookmark: Bookmark) {
        bookmarkDao.update(bookmark)
    }

    suspend fun delete(bookmark: Bookmark) {
        bookmarkDao.delete(bookmark)
    }

    suspend fun replaceAll(bookmarks: List<Bookmark>) {
        database.withTransaction {
            bookmarkDao.deleteAll()
            if (bookmarks.isNotEmpty()) {
                bookmarkDao.insertAll(bookmarks)
            }
        }
    }

    suspend fun deleteByUrl(url: String) {
        bookmarkDao.deleteByUrl(url)
    }

    suspend fun updatePositions(bookmarks: List<Bookmark>) {
        database.withTransaction {
            bookmarks.forEachIndexed { index, bookmark ->
                bookmarkDao.updatePosition(bookmark.id, index)
            }
        }
    }

    suspend fun updateScrollPosition(url: String, scrollPosition: Int) {
        bookmarkDao.updateScrollPosition(url, scrollPosition)
    }

    fun getCategories(): kotlinx.coroutines.flow.Flow<List<String>> = bookmarkDao.getCategories()
}