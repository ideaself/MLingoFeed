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
        val existingUrls = rssDao.getAllUrls().toSet()
        DEFAULT_SUBSCRIPTIONS.forEach { (title, url, category) ->
            if (url !in existingUrls) {
                rssDao.insertSubscription(
                    RssSubscription(title = title, url = url, category = category)
                )
            }
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

    companion object {
        val DEFAULT_SUBSCRIPTIONS = listOf(
            Triple("BBC Learning English", "https://feeds.bbci.co.uk/learningenglish/english/features/6-minute-english/rss.xml", "English"),
            Triple("VOA Learning English", "https://learningenglish.voanews.com/api/zkqiq-ev-ei", "English"),
            Triple("CNN Top Stories", "https://rss.cnn.com/rss/edition.rss", "English"),
            Triple("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "English"),
            Triple("Reuters World", "https://www.reutersagency.com/feed/?taxonomy=best-sectors&post_type=best", "English"),
            Triple("The Guardian World", "https://www.theguardian.com/world/rss", "English"),
            Triple("NPR News", "https://feeds.npr.org/1001/rss.xml", "English"),
            Triple("TIME", "https://time.com/feed/", "English")
        )
    }
}
