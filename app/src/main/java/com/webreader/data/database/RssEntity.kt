package com.webreader.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rss_subscriptions")
data class RssSubscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "English",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rss_articles",
    indices = [Index(value = ["link"], unique = true)]
)
data class RssArticle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val title: String,
    val link: String,
    val description: String = "",
    val pubDate: Long = 0,
    val isRead: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)
