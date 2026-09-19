package com.mlingofeed.data.database

import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS index over [RssArticle]'s searchable text. Room keeps it in sync through
 * generated triggers; the article rows themselves remain in `rss_articles`.
 */
@Fts4(contentEntity = RssArticle::class)
@Entity(tableName = "rss_articles_fts")
data class RssArticleFts(
    val title: String,
    val description: String,
    val content: String
)
