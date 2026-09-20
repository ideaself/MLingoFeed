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
    @Query("SELECT * FROM word_book ORDER BY dateAdded DESC LIMIT 500")
    fun getAllWords(): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE mastered = 0 AND nextReviewDate <= :now ORDER BY nextReviewDate ASC LIMIT 200")
    fun getDueWords(now: Long): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE mastered = 1 ORDER BY dateAdded DESC LIMIT 200")
    fun getMasteredWords(): Flow<List<WordBookEntry>>

    @Query("SELECT * FROM word_book WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): WordBookEntry?

    @Query("SELECT * FROM word_book WHERE word LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY dateAdded DESC")
    fun searchWords(query: String): Flow<List<WordBookEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WordBookEntry): Long

    @Update
    suspend fun update(entry: WordBookEntry)

    @Query("UPDATE word_book SET mastered = NOT mastered WHERE word = :word")
    suspend fun toggleMastered(word: String)

    @Query("UPDATE word_book SET mnemonic = :mnemonic WHERE word = :word")
    suspend fun updateMnemonic(word: String, mnemonic: String)

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

    @Query("SELECT word FROM word_book ORDER BY dateAdded DESC LIMIT 500")
    suspend fun getWordTexts(): List<String>
}
