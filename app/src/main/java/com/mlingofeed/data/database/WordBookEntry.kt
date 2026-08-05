package com.mlingofeed.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_book",
    indices = [Index(value = ["word"], unique = true)]
)
data class WordBookEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val definition: String = "",
    val phonetic: String = "",
    val exampleSentence: String = "",
    val sourceUrl: String = "",
    val sourceTitle: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val nextReviewDate: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val mastered: Boolean = false
)
