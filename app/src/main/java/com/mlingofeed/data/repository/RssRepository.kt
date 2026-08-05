package com.mlingofeed.data.repository

import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.data.database.RssArticleTag
import com.mlingofeed.data.database.RssDao
import com.mlingofeed.data.database.RssFolder
import com.mlingofeed.data.database.RssRule
import com.mlingofeed.data.database.RssSubscription
import com.mlingofeed.data.database.RssTag
import kotlinx.coroutines.flow.Flow

class RssRepository(private val rssDao: RssDao) {
    val allSubscriptions: Flow<List<RssSubscription>> = rssDao.getAllSubscriptions()
    val allArticles: Flow<List<RssArticle>> = rssDao.getAllArticles()
    val allFolders: Flow<List<RssFolder>> = rssDao.getAllFolders()
    val allTags: Flow<List<RssTag>> = rssDao.getAllTags()
    val allRules: Flow<List<RssRule>> = rssDao.getAllRules()
    val favoriteArticles: Flow<List<RssArticle>> = rssDao.getFavoriteArticles()
    val unreadArticles: Flow<List<RssArticle>> = rssDao.getUnreadArticles()
    val totalUnreadCount: Flow<Int> = rssDao.getTotalUnreadCount()

    fun getArticles(subscriptionId: Long): Flow<List<RssArticle>> =
        rssDao.getArticlesBySubscription(subscriptionId)

    fun getSubscriptionsByFolder(folderId: Long): Flow<List<RssSubscription>> =
        rssDao.getSubscriptionsByFolder(folderId)

    fun getSubscriptionsWithoutFolder(): Flow<List<RssSubscription>> =
        rssDao.getSubscriptionsWithoutFolder()

    fun getUnreadCount(subscriptionId: Long): Flow<Int> =
        rssDao.getUnreadCount(subscriptionId)

    fun getArticlesByTag(tagId: Long): Flow<List<RssArticle>> =
        rssDao.getArticlesByTag(tagId)

    fun searchArticles(query: String): Flow<List<RssArticle>> =
        rssDao.searchArticles(query)

    suspend fun getArticleById(id: Long): RssArticle? =
        rssDao.getArticleById(id)

    suspend fun getUnreadCountSync(subscriptionId: Long): Int =
        rssDao.getUnreadCountSync(subscriptionId)

    suspend fun addSubscription(title: String, url: String, folderId: Long? = null): Long {
        return rssDao.insertSubscription(
            RssSubscription(title = title, url = url, folderId = folderId)
        )
    }

    suspend fun deleteSubscription(id: Long) {
        rssDao.deleteArticlesBySubscription(id)
        rssDao.deleteSubscription(id)
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

    suspend fun insertArticles(articles: List<RssArticle>) {
        if (articles.isEmpty()) return
        val rules = rssDao.getEnabledRulesSync()
        if (rules.isNotEmpty()) {
            val processedArticles = articles.map { article ->
                applyRules(article, rules)
            }
            rssDao.insertArticles(processedArticles)
        } else {
            rssDao.insertArticles(articles)
        }
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
        val article = rssDao.getArticleById(id) ?: return
        rssDao.setReadStatus(id, !article.isRead)
    }

    suspend fun toggleFavorite(id: Long) {
        val article = rssDao.getArticleById(id) ?: return
        rssDao.setFavoriteStatus(id, !article.isFavorite)
    }

    suspend fun updateArticleContent(id: Long, content: String) {
        rssDao.updateArticleContent(id, content)
    }

    suspend fun markAllAsRead(subscriptionId: Long) {
        rssDao.setAllReadStatus(subscriptionId, true)
    }

    suspend fun markAllAsRead() {
        rssDao.getAllSubscriptionsSync().forEach { sub ->
            rssDao.setAllReadStatus(sub.id, true)
        }
    }

    suspend fun fetchAndRefresh(subscriptionId: Long): Int {
        val sub = rssDao.getSubscriptionById(subscriptionId) ?: return 0
        val articles = RssParser.parse(sub.id, sub.url)
        val existingUrls = rssDao.getAllUrls().toSet()
        val newArticles = articles.filter { it.link !in existingUrls || true }
        rssDao.insertArticles(newArticles)
        return newArticles.size
    }

    suspend fun refreshAll(): Int {
        val subscriptions = rssDao.getAllSubscriptionsSync()
        var totalNew = 0
        for (sub in subscriptions) {
            if (sub.isEnabled) {
                try {
                    val articles = RssParser.parse(sub.id, sub.url)
                    rssDao.insertArticles(articles)
                    totalNew += articles.size
                } catch (_: Exception) {}
            }
        }
        return totalNew
    }

    suspend fun deleteAllSubscriptions() {
        rssDao.deleteAllArticles()
        rssDao.deleteAllSubscriptions()
    }

    suspend fun cleanupOldArticles() {
        val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        rssDao.deleteOldArticles(oneWeekAgo)
    }

    suspend fun initDefaultSubscriptions() {
        val count = rssDao.getSubscriptionCount()
        if (count > 0) return

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

    suspend fun cleanupDuplicates() {
        val allSubs = rssDao.getAllSubscriptionsSync()
        val seen = mutableSetOf<String>()
        for (sub in allSubs) {
            if (sub.url in seen) {
                rssDao.deleteArticlesBySubscription(sub.id)
                rssDao.deleteSubscription(sub.id)
            } else {
                seen.add(sub.url)
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
        rssDao.getAllSubscriptionsSync().filter { it.folderId == id }.forEach {
            rssDao.moveSubscriptionToFolder(it.id, null)
        }
        rssDao.deleteFolder(id)
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
        rssDao.clearArticleTags(articleId)
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
        return ReadStats(
            totalArticles = rssDao.getTotalArticleCount(),
            readArticles = rssDao.getTotalReadCount(),
            unreadArticles = rssDao.getTotalReadCount(),
            favoriteArticles = rssDao.getTotalFavoriteCount(),
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
