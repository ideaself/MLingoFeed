package com.webreader.data.repository

import com.webreader.data.database.RssArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

object RssParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class ParsedArticle(val title: String, val link: String, val description: String, val pubDate: Long)

    suspend fun parse(subscriptionId: Long, rssUrl: String): List<RssArticle> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(body, "", Parser.xmlParser())

            val articles = mutableListOf<RssArticle>()

            // RSS 2.0 <item>
            var items = doc.select("item")
            if (items.isEmpty()) {
                // Atom <entry>
                items = doc.select("entry")
            }

            for (item in items) {
                val title = item.selectFirst("title")?.text()?.trim() ?: continue
                val link = extractLink(item)
                if (link.isBlank()) continue
                val description = item.selectFirst("description")?.text()?.trim()
                    ?: item.selectFirst("summary")?.text()?.trim()
                    ?: item.selectFirst("content")?.text()?.trim()
                    ?: ""
                val pubDate = parseDate(item.selectFirst("pubDate")?.text()
                    ?: item.selectFirst("published")?.text()
                    ?: item.selectFirst("updated")?.text()
                    ?: item.selectFirst("dc|date")?.text()
                    ?: "")

                val cleanDesc = Jsoup.parse(description).text().take(300)

                articles.add(
                    RssArticle(
                        subscriptionId = subscriptionId,
                        title = Jsoup.parse(title).text(),
                        link = link,
                        description = cleanDesc,
                        pubDate = pubDate
                    )
                )
            }

            articles
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractLink(item: org.jsoup.nodes.Element): String {
        val linkEl = item.selectFirst("link")
        val href = linkEl?.attr("href")
        if (!href.isNullOrBlank()) return href
        return linkEl?.text()?.trim() ?: ""
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "EEE, dd MMM yyyy HH:mm:ss z"
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.ENGLISH)
                sdf.parse(dateStr)?.let { return it.time }
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
