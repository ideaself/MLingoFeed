package com.webreader.data.repository

import com.webreader.data.database.RssArticle
import com.webreader.data.database.RssDao
import com.webreader.data.database.RssSubscription
import kotlinx.coroutines.flow.Flow

class RssRepository(private val rssDao: RssDao) {
    val allSubscriptions: Flow<List<RssSubscription>> = rssDao.getAllSubscriptions()

    fun getArticles(subscriptionId: Long): Flow<List<RssArticle>> =
        rssDao.getArticlesBySubscription(subscriptionId)

    fun getUnreadCount(subscriptionId: Long): Flow<Int> =
        rssDao.getUnreadCount(subscriptionId)

    suspend fun addSubscription(title: String, url: String, category: String = "English"): Long {
        return rssDao.insertSubscription(
            RssSubscription(title = title, url = url, category = category)
        )
    }

    suspend fun deleteSubscription(id: Long) {
        rssDao.deleteArticlesBySubscription(id)
        rssDao.deleteSubscription(id)
    }

    suspend fun updateSubscription(id: Long, title: String, url: String, category: String) {
        val existing = rssDao.getSubscriptionById(id)
        if (existing != null) {
            rssDao.insertSubscription(existing.copy(title = title, url = url, category = category))
        }
    }

    suspend fun insertArticles(articles: List<RssArticle>) {
        rssDao.insertArticles(articles)
    }

    suspend fun markAsRead(id: Long) {
        rssDao.markAsRead(id)
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
        val existing = rssDao.getAllSubscriptions()
        // Only init if empty - we check by querying once
    }

    companion object {
        val DEFAULT_SUBSCRIPTIONS = listOf(
            Triple("BBC Learning English", "https://feeds.bbci.co.uk/learningenglish/english/features/6-minute-english/rss.xml", "English"),
            Triple("VOA Learning English", "https://learningenglish.voanews.com/api/zkqxyipq-$", "English"),
            Triple("CNN Top Stories", "http://rss.cnn.com/rss/edition.rss", "English"),
            Triple("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "English"),
            Triple("Reuters", "https://www.rss.reuters.com/news/world", "English"),
            Triple("The Guardian", "https://www.theguardian.com/world/rss", "English"),
            Triple("NPR News", "https://feeds.npr.org/1001/rss.xml", "English"),
            Triple("TIME", "https://time.com/feed/", "English")
        )
    }
}
