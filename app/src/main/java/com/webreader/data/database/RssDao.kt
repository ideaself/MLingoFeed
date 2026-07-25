package com.webreader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {
    @Query("SELECT * FROM rss_subscriptions ORDER BY createdAt ASC")
    fun getAllSubscriptions(): Flow<List<RssSubscription>>

    @Query("SELECT * FROM rss_subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: Long): RssSubscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: RssSubscription): Long

    @Query("DELETE FROM rss_subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: Long)

    @Query("SELECT * FROM rss_articles WHERE subscriptionId = :subscriptionId ORDER BY pubDate DESC LIMIT 200")
    fun getArticlesBySubscription(subscriptionId: Long): Flow<List<RssArticle>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<RssArticle>)

    @Query("UPDATE rss_articles SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM rss_articles WHERE fetchedAt < :timestamp")
    suspend fun deleteOldArticles(timestamp: Long)

    @Query("SELECT COUNT(*) FROM rss_articles WHERE subscriptionId = :subscriptionId AND isRead = 0")
    fun getUnreadCount(subscriptionId: Long): Flow<Int>

    @Query("DELETE FROM rss_articles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteArticlesBySubscription(subscriptionId: Long)

    @Query("DELETE FROM rss_articles")
    suspend fun deleteAllArticles()

    @Query("DELETE FROM rss_subscriptions")
    suspend fun deleteAllSubscriptions()
}
