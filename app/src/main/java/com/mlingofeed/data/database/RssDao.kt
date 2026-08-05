package com.mlingofeed.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {
    // Folders
    @Query("SELECT * FROM rss_folders ORDER BY `order` ASC")
    fun getAllFolders(): Flow<List<RssFolder>>

    @Query("SELECT * FROM rss_folders ORDER BY `order` ASC")
    suspend fun getAllFoldersSync(): List<RssFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: RssFolder): Long

    @Query("UPDATE rss_folders SET name = :name WHERE id = :id")
    suspend fun updateFolder(id: Long, name: String)

    @Query("DELETE FROM rss_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    // Subscriptions
    @Query("SELECT * FROM rss_subscriptions ORDER BY createdAt ASC")
    fun getAllSubscriptions(): Flow<List<RssSubscription>>

    @Query("SELECT * FROM rss_subscriptions ORDER BY createdAt ASC")
    suspend fun getAllSubscriptionsSync(): List<RssSubscription>

    @Query("SELECT * FROM rss_subscriptions WHERE folderId = :folderId ORDER BY createdAt ASC")
    fun getSubscriptionsByFolder(folderId: Long): Flow<List<RssSubscription>>

    @Query("SELECT * FROM rss_subscriptions WHERE folderId IS NULL ORDER BY createdAt ASC")
    fun getSubscriptionsWithoutFolder(): Flow<List<RssSubscription>>

    @Query("SELECT * FROM rss_subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: Long): RssSubscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: RssSubscription): Long

    @Query("DELETE FROM rss_subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: Long)

    @Query("UPDATE rss_subscriptions SET folderId = :folderId WHERE id = :subscriptionId")
    suspend fun moveSubscriptionToFolder(subscriptionId: Long, folderId: Long?)

    // Articles
    @Query("SELECT * FROM rss_articles WHERE subscriptionId = :subscriptionId ORDER BY pubDate DESC LIMIT 200")
    fun getArticlesBySubscription(subscriptionId: Long): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles ORDER BY pubDate DESC LIMIT 300")
    fun getAllArticles(): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE isFavorite = 1 ORDER BY pubDate DESC LIMIT 200")
    fun getFavoriteArticles(): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE isRead = 0 ORDER BY pubDate DESC LIMIT 300")
    fun getUnreadArticles(): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY pubDate DESC LIMIT 100")
    fun searchArticles(query: String): Flow<List<RssArticle>>

    @Query("SELECT * FROM rss_articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: Long): RssArticle?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<RssArticle>)

    @Query("UPDATE rss_articles SET isRead = :isRead WHERE id = :id")
    suspend fun setReadStatus(id: Long, isRead: Boolean)

    @Query("UPDATE rss_articles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE rss_articles SET content = :content WHERE id = :id")
    suspend fun updateArticleContent(id: Long, content: String)

    @Query("UPDATE rss_articles SET isRead = :isRead WHERE subscriptionId = :subscriptionId")
    suspend fun setAllReadStatus(subscriptionId: Long, isRead: Boolean)

    @Query("DELETE FROM rss_articles WHERE fetchedAt < :timestamp")
    suspend fun deleteOldArticles(timestamp: Long)

    @Query("SELECT COUNT(*) FROM rss_articles WHERE subscriptionId = :subscriptionId AND isRead = 0")
    fun getUnreadCount(subscriptionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM rss_articles WHERE subscriptionId = :subscriptionId AND isRead = 0")
    suspend fun getUnreadCountSync(subscriptionId: Long): Int

    @Query("DELETE FROM rss_articles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteArticlesBySubscription(subscriptionId: Long)

    @Query("DELETE FROM rss_articles")
    suspend fun deleteAllArticles()

    @Query("DELETE FROM rss_subscriptions")
    suspend fun deleteAllSubscriptions()

    @Query("SELECT COUNT(*) FROM rss_subscriptions")
    suspend fun getSubscriptionCount(): Int

    @Query("SELECT url FROM rss_subscriptions")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT COUNT(*) FROM rss_articles WHERE isRead = 0")
    fun getTotalUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM rss_articles WHERE isRead = 1")
    suspend fun getTotalReadCount(): Int

    @Query("SELECT COUNT(*) FROM rss_articles WHERE isFavorite = 1")
    suspend fun getTotalFavoriteCount(): Int

    @Query("SELECT COUNT(*) FROM rss_articles")
    suspend fun getTotalArticleCount(): Int

    // Tags
    @Query("SELECT * FROM rss_tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<RssTag>>

    @Query("SELECT * FROM rss_tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<RssTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: RssTag): Long

    @Query("DELETE FROM rss_tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleTag(crossRef: RssArticleTag)

    @Query("DELETE FROM rss_article_tags WHERE articleId = :articleId")
    suspend fun clearArticleTags(articleId: Long)

    @Query("SELECT t.* FROM rss_tags t INNER JOIN rss_article_tags at ON t.id = at.tagId WHERE at.articleId = :articleId")
    suspend fun getTagsForArticle(articleId: Long): List<RssTag>

    @Query("SELECT a.* FROM rss_articles a INNER JOIN rss_article_tags at ON a.id = at.articleId WHERE at.tagId = :tagId ORDER BY a.pubDate DESC LIMIT 200")
    fun getArticlesByTag(tagId: Long): Flow<List<RssArticle>>

    // Rules
    @Query("SELECT * FROM rss_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<RssRule>>

    @Query("SELECT * FROM rss_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledRulesSync(): List<RssRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RssRule): Long

    @Query("UPDATE rss_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateRuleEnabled(id: Long, isEnabled: Boolean)

    @Query("DELETE FROM rss_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}
