package com.mlingofeed.data.repository

import androidx.room.withTransaction
import com.mlingofeed.data.database.AppDatabase
import com.mlingofeed.data.database.WordBookDao
import com.mlingofeed.data.database.WordBookEntry
import com.mlingofeed.data.escapeLikePattern
import kotlinx.coroutines.flow.Flow

class WordBookRepository(private val dao: WordBookDao, private val database: AppDatabase) {

    val allWords: Flow<List<WordBookEntry>> = dao.getAllWords()
    val dueWords: Flow<List<WordBookEntry>>
        get() = dao.getDueWords(System.currentTimeMillis())
    val masteredWords: Flow<List<WordBookEntry>> = dao.getMasteredWords()

    fun searchWords(query: String): Flow<List<WordBookEntry>> =
        dao.searchWords(escapeLikePattern(query))

    suspend fun toggleMastered(word: String) {
        dao.toggleMastered(word)
    }

    suspend fun updateMnemonic(word: String, mnemonic: String) {
        dao.updateMnemonic(word, mnemonic)
    }

    suspend fun addWord(
        word: String,
        definition: String = "",
        phonetic: String = "",
        exampleSentence: String = "",
        sourceUrl: String = "",
        sourceTitle: String = ""
    ): Long = database.withTransaction {
        val existing = dao.getWord(word)
        if (existing != null) {
            // Backfill context on previously saved words that lack it.
            if (existing.exampleSentence.isBlank() && exampleSentence.isNotBlank()) {
                dao.update(
                    existing.copy(
                        exampleSentence = exampleSentence,
                        sourceUrl = sourceUrl.ifBlank { existing.sourceUrl },
                        sourceTitle = sourceTitle.ifBlank { existing.sourceTitle }
                    )
                )
            }
            return@withTransaction existing.id
        }

        dao.insert(
            WordBookEntry(
                word = word,
                definition = definition,
                phonetic = phonetic,
                exampleSentence = exampleSentence,
                sourceUrl = sourceUrl,
                sourceTitle = sourceTitle
            )
        ).takeIf { it != -1L } ?: dao.getWord(word)?.id ?: -1L
    }

    suspend fun markAsMastered(word: String) {
        database.withTransaction {
            val entry = dao.getWord(word) ?: return@withTransaction
            dao.update(entry.copy(mastered = true))
        }
    }

    suspend fun markAsNotMastered(word: String) {
        database.withTransaction {
            val entry = dao.getWord(word) ?: return@withTransaction
            dao.update(entry.copy(mastered = false))
        }
    }

    suspend fun reviewWord(word: String, isKnown: Boolean) {
        database.withTransaction {
            val entry = dao.getWord(word) ?: return@withTransaction
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
    }

    suspend fun deleteWord(word: String) = dao.deleteByWord(word)

    /**
     * Imports words from CSV/Anki text. Existing words are kept; their definition is only
     * backfilled when it was empty. Returns the number of newly added words.
     */
    suspend fun importWords(content: String): Int {
        val entries = WordBookImporter.parse(content)
        if (entries.isEmpty()) return 0
        return database.withTransaction {
            var imported = 0
            entries.forEach { (word, definition) ->
                val existing = dao.getWord(word)
                if (existing == null) {
                    dao.insert(WordBookEntry(word = word, definition = definition))
                    imported++
                } else if (existing.definition.isBlank() && definition.isNotBlank()) {
                    dao.update(existing.copy(definition = definition))
                }
            }
            imported
        }
    }

    suspend fun isWordSaved(word: String): Boolean = dao.getWord(word) != null

    suspend fun getTotalCount(): Int = dao.getTotalCount()
    suspend fun getMasteredCount(): Int = dao.getMasteredCount()
    suspend fun getDueCount(): Int = dao.getDueCount()
    suspend fun getWordsAddedBetween(start: Long, end: Long): Int = dao.getWordsAddedBetween(start, end)

    suspend fun getWordTexts(): List<String> = dao.getWordTexts()

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
