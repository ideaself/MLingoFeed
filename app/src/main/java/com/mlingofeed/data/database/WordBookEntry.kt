package com.mlingofeed.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "word_book",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["mastered", "nextReviewDate"]),
        Index(value = ["dateAdded"])
    ]
)
data class WordBookEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val definition: String = "",
    val phonetic: String = "",
    val exampleSentence: String = "",
    val sourceUrl: String = "",
    val sourceTitle: String = "",
    @ColumnInfo(defaultValue = "") val mnemonic: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val nextReviewDate: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val mastered: Boolean = false
)
