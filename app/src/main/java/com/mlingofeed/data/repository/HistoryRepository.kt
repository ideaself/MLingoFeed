package com.mlingofeed.data.repository

import androidx.room.withTransaction
import com.mlingofeed.data.database.AppDatabase
import com.mlingofeed.data.database.History
import com.mlingofeed.data.database.HistoryDao
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao, private val database: AppDatabase) {
    val allHistory: Flow<List<History>> = historyDao.getAllHistory()

    suspend fun recordVisit(title: String, url: String) {
        database.withTransaction {
            historyDao.insert(History(title = title, url = url))
            historyDao.trimOld()
        }
    }

    suspend fun delete(id: Long) {
        historyDao.delete(id)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
