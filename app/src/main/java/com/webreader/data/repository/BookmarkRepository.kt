package com.webreader.data.repository

import com.webreader.data.database.Bookmark
import com.webreader.data.database.BookmarkDao
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    suspend fun getBookmarkByUrl(url: String): Bookmark? {
        return bookmarkDao.getBookmarkByUrl(url)
    }

    suspend fun insert(bookmark: Bookmark): Long {
        return bookmarkDao.insert(bookmark)
    }

    suspend fun delete(bookmark: Bookmark) {
        bookmarkDao.delete(bookmark)
    }

    suspend fun deleteByUrl(url: String) {
        bookmarkDao.deleteByUrl(url)
    }
}
