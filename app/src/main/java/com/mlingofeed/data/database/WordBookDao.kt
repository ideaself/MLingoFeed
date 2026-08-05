package com.mlingofeed.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookDao {
    @Query("SELECT * FROM word_book ORDER BY dateAdded DESC")
    fun getAllWords(): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE mastered = 0 ORDER BY nextReviewDate ASC")
    fun getDueWords(): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE mastered = 1 ORDER BY dateAdded DESC")
    fun getMasteredWords(): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): WordBookEntry?

    @Query("SELECT * FROM word_book WHERE word LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchWords(query: String): Flow<List<WordBookEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WordBookEntry): Long

    @Update
    suspend fun update(entry: WordBookEntry)

    @Delete
    suspend fun delete(entry: WordBookEntry)

    @Query("DELETE FROM word_book WHERE word = :word")
    suspend fun deleteByWord(word: String)

    @Query("SELECT COUNT(*) FROM word_book")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM word_book WHERE mastered = 1")
    suspend fun getMasteredCount(): Int

    @Query("SELECT COUNT(*) FROM word_book WHERE mastered = 0 AND nextReviewDate <= :now")
    suspend fun getDueCount(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM word_book WHERE dateAdded >= :startTime AND dateAdded <= :endTime")
    suspend fun getWordsAddedBetween(startTime: Long, endTime: Long): Int
}
