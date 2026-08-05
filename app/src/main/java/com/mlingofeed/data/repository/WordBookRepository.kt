package com.mlingofeed.data.repository

import com.mlingofeed.data.database.WordBookDao
import com.mlingofeed.data.database.WordBookEntry
import kotlinx.coroutines.flow.Flow

class WordBookRepository(private val dao: WordBookDao) {

    val allWords: Flow<List<WordBookEntry>> = dao.getAllWords()
    val dueWords: Flow<List<WordBookEntry>> = dao.getDueWords()
    val masteredWords: Flow<List<WordBookEntry>> = dao.getMasteredWords()

    fun searchWords(query: String): Flow<List<WordBookEntry>> = dao.searchWords(query)

    suspend fun addWord(
        word: String,
        definition: String = "",
        phonetic: String = "",
        exampleSentence: String = "",
        sourceUrl: String = "",
        sourceTitle: String = ""
    ): Long {
        val existing = dao.getWord(word)
        if (existing != null) return existing.id

        return dao.insert(
            WordBookEntry(
                word = word,
                definition = definition,
                phonetic = phonetic,
                exampleSentence = exampleSentence,
                sourceUrl = sourceUrl,
                sourceTitle = sourceTitle
            )
        )
    }

    suspend fun markAsMastered(word: String) {
        val entry = dao.getWord(word) ?: return
        dao.update(entry.copy(mastered = true))
    }

    suspend fun markAsNotMastered(word: String) {
        val entry = dao.getWord(word) ?: return
        dao.update(entry.copy(mastered = false))
    }

    suspend fun reviewWord(word: String, isKnown: Boolean) {
        val entry = dao.getWord(word) ?: return
        val newCount = entry.reviewCount + 1
        val interval = getReviewInterval(newCount)
        val nextReview = System.currentTimeMillis() + interval

        dao.update(
            entry.copy(
                reviewCount = newCount,
                nextReviewDate = nextReview,
                mastered = isKnown && newCount >= 5
            )
        )
    }

    suspend fun deleteWord(word: String) = dao.deleteByWord(word)

    suspend fun isWordSaved(word: String): Boolean = dao.getWord(word) != null

    suspend fun getTotalCount(): Int = dao.getTotalCount()
    suspend fun getMasteredCount(): Int = dao.getMasteredCount()
    suspend fun getDueCount(): Int = dao.getDueCount()
    suspend fun getWordsAddedBetween(start: Long, end: Long): Int = dao.getWordsAddedBetween(start, end)

    private fun getReviewInterval(reviewCount: Int): Long {
        val day = 24 * 60 * 60 * 1000L
        return when (reviewCount) {
            1 -> day
            2 -> 2 * day
            3 -> 4 * day
            4 -> 7 * day
            5 -> 15 * day
            else -> 30 * day
        }
    }
}
