package com.mlingofeed.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true), Index(value = ["category"])]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int = 0,
    val category: String = "",
    val scrollPosition: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)