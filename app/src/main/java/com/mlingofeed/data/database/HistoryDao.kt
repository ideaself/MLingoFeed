package com.mlingofeed.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 200")
    fun getAllHistory(): Flow<List<History>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): History?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History): Long

    @Query("UPDATE history SET visitedAt = :visitedAt, title = :title WHERE url = :url")
    suspend fun updateVisit(url: String, visitedAt: Long, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visitedAt DESC LIMIT 100)")
    suspend fun trimOld()
}
