package com.mlingofeed.data.repository

import com.mlingofeed.data.database.History
import com.mlingofeed.data.database.HistoryDao
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<History>> = historyDao.getAllHistory()

    suspend fun recordVisit(title: String, url: String) {
        val existing = historyDao.getHistoryByUrl(url)
        if (existing != null) {
            historyDao.updateVisit(url, System.currentTimeMillis(), title)
        } else {
            historyDao.insert(History(title = title, url = url))
            val count = historyDao.getCount()
            if (count > 100) {
                historyDao.trimOld()
            }
        }
    }

    suspend fun delete(id: Long) {
        historyDao.delete(id)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
