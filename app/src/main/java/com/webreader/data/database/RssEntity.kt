package com.webreader.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rss_folders")
data class RssFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rss_subscriptions")
data class RssSubscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long? = null,
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
    val content: String = "",
    val pubDate: Long = 0,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rss_tags")
data class RssTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = ""
)

@Entity(
    tableName = "rss_article_tags",
    primaryKeys = ["articleId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class RssArticleTag(
    val articleId: Long,
    val tagId: Long
)

@Entity(tableName = "rss_rules")
data class RssRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keyword: String,
    val action: String,
    val tagId: Long? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
