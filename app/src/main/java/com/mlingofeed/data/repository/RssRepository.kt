package com.mlingofeed.data.repository

import androidx.room.withTransaction
import com.mlingofeed.data.escapeLikePattern
import com.mlingofeed.data.database.AppDatabase
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.database.RssArticleTag
import com.mlingofeed.data.database.RssDao
import com.mlingofeed.data.database.RssFolder
import com.mlingofeed.data.database.RssRule
import com.mlingofeed.data.database.RssSubscription
import com.mlingofeed.data.database.RssTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class RssRepository(private val rssDao: RssDao, private val database: AppDatabase) {
    val allSubscriptions: Flow<List<RssSubscription>> = rssDao.getAllSubscriptions()
    val allArticles: Flow<List<RssArticle>> = rssDao.getAllArticles()
    val allFolders: Flow<List<RssFolder>> = rssDao.getAllFolders()
    val allTags: Flow<List<RssTag>> = rssDao.getAllTags()
    val allRules: Flow<List<RssRule>> = rssDao.getAllRules()
    val favoriteArticles: Flow<List<RssArticle>> = rssDao.getFavoriteArticles()
    val unreadArticles: Flow<List<RssArticle>> = rssDao.getUnreadArticles()
    val totalUnreadCount: Flow<Int> = rssDao.getTotalUnreadCount()
    private val refreshMutex = Mutex()
    val unreadCountsBySubscription: Flow<Map<Long, Int>> =
        rssDao.getUnreadCountsBySubscription().map { list ->
            list.associate { it.subscriptionId to it.unreadCount }
        }

    fun getArticles(subscriptionId: Long): Flow<List<RssArticle>> =
        rssDao.getArticlesBySubscription(subscriptionId)

    fun getSubscriptionsByFolder(folderId: Long): Flow<List<RssSubscription>> =
        rssDao.getSubscriptionsByFolder(folderId)

    fun getSubscriptionsWithoutFolder(): Flow<List<RssSubscription>> =
        rssDao.getSubscriptionsWithoutFolder()

    fun getArticlesByTag(tagId: Long): Flow<List<RssArticle>> =
        rssDao.getArticlesByTag(tagId)

    fun searchArticles(query: String): Flow<List<RssArticle>> =
        rssDao.searchArticles(escapeLikePattern(query))

    suspend fun getArticleById(id: Long): RssArticle? =
        rssDao.getArticleById(id)

    suspend fun getSubscriptionById(id: Long): RssSubscription? =
        rssDao.getSubscriptionById(id)

    suspend fun addSubscription(title: String, url: String, folderId: Long? = null): Long {
        return rssDao.insertSubscription(
            RssSubscription(title = title, url = url, folderId = folderId)
        )
    }

    suspend fun addSubscriptions(subscriptions: List<Pair<String, String>>): Int {
        if (subscriptions.isEmpty()) return 0
        return database.withTransaction {
            subscriptions.forEach { (title, url) ->
                rssDao.insertSubscription(RssSubscription(title = title, url = url))
            }
            subscriptions.size
        }
    }

    suspend fun deleteSubscription(id: Long) {
        database.withTransaction {
            rssDao.clearArticleTagsForSubscription(id)
            rssDao.deleteArticlesBySubscription(id)
            rssDao.deleteSubscription(id)
        }
    }

    suspend fun updateSubscription(id: Long, title: String, url: String, folderId: Long? = null) {
        val existing = rssDao.getSubscriptionById(id)
        if (existing != null) {
            rssDao.insertSubscription(existing.copy(title = title, url = url, folderId = folderId))
        }
    }

    suspend fun moveSubscriptionToFolder(subscriptionId: Long, folderId: Long?) {
        rssDao.moveSubscriptionToFolder(subscriptionId, folderId)
    }

    suspend fun insertArticles(articles: List<RssArticle>): Int {
        if (articles.isEmpty()) return 0
        return insertArticles(articles, rssDao.getEnabledRulesSync())
    }

    private suspend fun insertArticles(articles: List<RssArticle>, rules: List<RssRule>): Int {
        if (articles.isEmpty()) return 0
        val processedArticles = if (rules.isEmpty()) {
            articles
        } else {
            articles.map { article -> applyRules(article, rules) }
        }
        return rssDao.insertArticles(processedArticles).count { it != -1L }
    }

    private fun applyRules(article: RssArticle, rules: List<RssRule>): RssArticle {
        var processed = article
        for (rule in rules) {
            val keyword = rule.keyword.lowercase()
            val matches = article.title.lowercase().contains(keyword) ||
                    article.description.lowercase().contains(keyword)
            if (matches) {
                when (rule.action) {
                    "mark_read" -> {
                        processed = processed.copy(isRead = true)
                    }
                    "favorite" -> {
                        processed = processed.copy(isFavorite = true)
                    }
                }
            }
        }
        return processed
    }

    suspend fun markAsRead(id: Long) {
        rssDao.setReadStatus(id, true)
    }

    suspend fun markAsUnread(id: Long) {
        rssDao.setReadStatus(id, false)
    }

    suspend fun toggleReadStatus(id: Long) {
        rssDao.toggleReadStatus(id)
    }

    suspend fun toggleFavorite(id: Long) {
        rssDao.toggleFavoriteStatus(id)
    }

    suspend fun updateArticleContent(id: Long, content: String) {
        rssDao.updateArticleContent(id, content)
    }

    suspend fun markAllAsRead(subscriptionId: Long) {
        rssDao.setAllReadStatus(subscriptionId, true)
    }

    suspend fun markAllAsRead() {
        rssDao.markAllArticlesRead()
    }

    suspend fun refreshAll(): Int = refreshMutex.withLock {
        coroutineScope {
            val subscriptions = rssDao.getAllSubscriptionsSync().filter { it.isEnabled }
            val rules = rssDao.getEnabledRulesSync()
            val semaphore = Semaphore(4)
            subscriptions.map { sub ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val articles = RssParser.parse(sub.id, sub.url)
                            insertArticles(articles, rules)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            0
                        }
                    }
                }
            }.awaitAll().sum()
        }
    }

    suspend fun deleteAllSubscriptions() {
        database.withTransaction {
            rssDao.deleteAllArticles()
            rssDao.clearOrphanArticleTags()
            rssDao.deleteAllSubscriptions()
        }
    }

    suspend fun getFoldersSync(): List<RssFolder> = rssDao.getAllFoldersSync()

    suspend fun getSubscriptionsSync(): List<RssSubscription> = rssDao.getAllSubscriptionsSync()

    /**
     * Imports parsed OPML folders/subs atomically, reusing an existing folder with the same name
     * and skipping feeds that are already subscribed. Returns the number of new subscriptions.
     */
    suspend fun importOpml(folders: List<OpmlParser.OpmlFolder>): Int {
        if (folders.isEmpty()) return 0
        return database.withTransaction {
            val folderIds = rssDao.getAllFoldersSync().associate { it.name to it.id }.toMutableMap()
            val knownUrls = rssDao.getAllSubscriptionsSync().map { it.url }.toMutableSet()
            var added = 0
            folders.forEach { folder ->
                if (folder.feeds.isEmpty()) return@forEach
                val folderId = folderIds.getOrPut(folder.name) {
                    rssDao.insertFolder(RssFolder(name = folder.name, order = folderIds.size))
                }
                folder.feeds.forEach { feed ->
                    if (knownUrls.add(feed.url)) {
                        rssDao.insertSubscription(
                            RssSubscription(title = feed.title, url = feed.url, folderId = folderId)
                        )
                        added++
                    }
                }
            }
            added
        }
    }

    suspend fun cleanupOldArticles() {
        val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        database.withTransaction {
            rssDao.deleteOldArticles(oneWeekAgo)
            rssDao.clearOrphanArticleTags()
        }
    }

    suspend fun initDefaultSubscriptions() {
        if (rssDao.getSubscriptionCount() > 0) return

        database.withTransaction {
            val folderIds = mutableMapOf<String, Long>()
            DEFAULT_SUBSCRIPTIONS.forEach { (title, url, folder) ->
                val folderId = folderIds.getOrPut(folder) {
                    rssDao.insertFolder(RssFolder(name = folder, order = folderIds.size))
                }
                rssDao.insertSubscription(
                    RssSubscription(title = title, url = url, folderId = folderId)
                )
            }
        }
    }

    suspend fun cleanupDuplicates() {
        database.withTransaction {
            val allSubs = rssDao.getAllSubscriptionsSync()
            val seen = mutableSetOf<String>()
            for (sub in allSubs) {
                if (sub.url in seen) {
                    rssDao.clearArticleTagsForSubscription(sub.id)
                    rssDao.deleteArticlesBySubscription(sub.id)
                    rssDao.deleteSubscription(sub.id)
                } else {
                    seen.add(sub.url)
                }
            }
        }
    }

    suspend fun addFolder(name: String): Long {
        val existing = rssDao.getAllFoldersSync()
        return rssDao.insertFolder(RssFolder(name = name, order = existing.size))
    }

    suspend fun renameFolder(id: Long, name: String) {
        rssDao.updateFolder(id, name)
    }

    suspend fun deleteFolder(id: Long) {
        database.withTransaction {
            rssDao.clearFolderFromSubscriptions(id)
            rssDao.deleteFolder(id)
        }
    }

    suspend fun addTag(name: String, color: String = ""): Long {
        return rssDao.insertTag(RssTag(name = name, color = color))
    }

    suspend fun deleteTag(id: Long) {
        rssDao.deleteTag(id)
    }

    suspend fun getTagsForArticle(articleId: Long): List<RssTag> {
        return rssDao.getTagsForArticle(articleId)
    }

    suspend fun addTagToArticle(articleId: Long, tagId: Long) {
        rssDao.insertArticleTag(RssArticleTag(articleId = articleId, tagId = tagId))
    }

    suspend fun removeTagFromArticle(articleId: Long, tagId: Long) {
        rssDao.removeArticleTag(articleId, tagId)
    }

    suspend fun addRule(name: String, keyword: String, action: String, tagId: Long? = null): Long {
        return rssDao.insertRule(RssRule(name = name, keyword = keyword, action = action, tagId = tagId))
    }

    suspend fun toggleRule(id: Long, isEnabled: Boolean) {
        rssDao.updateRuleEnabled(id, isEnabled)
    }

    suspend fun deleteRule(id: Long) {
        rssDao.deleteRule(id)
    }

    suspend fun getReadStats(): ReadStats {
        val stats = rssDao.getArticleStats()
        return ReadStats(
            totalArticles = stats.total,
            readArticles = stats.readCount,
            unreadArticles = stats.total - stats.readCount,
            favoriteArticles = stats.favoriteCount,
            subscriptionCount = rssDao.getSubscriptionCount()
        )
    }

    data class ReadStats(
        val totalArticles: Int,
        val readArticles: Int,
        val unreadArticles: Int,
        val favoriteArticles: Int,
        val subscriptionCount: Int
    )

    companion object {
        val DEFAULT_SUBSCRIPTIONS = listOf(
            Triple("Al Jazeera", "https://www.aljazeera.com/Services/Rss/?PostingId=2007731105943979989", "News"),
            Triple("TIME", "https://time.com/feed/", "News"),
            Triple("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "News")
        )
    }
}
