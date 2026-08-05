package com.mlingofeed.data.repository

import com.mlingofeed.data.database.RssArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

object RssParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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

            var items = doc.select("item")
            if (items.isEmpty()) {
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

    suspend fun fetchFullContent(articleUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ""
            val doc = Jsoup.parse(body)

            doc.select("script, style, nav, header, footer, aside, .ad, .advertisement, .social-share, .comments, noscript").remove()

            val content = extractMainContent(doc)
            if (content.length > 200) {
                return@withContext content
            }

            doc.body()?.text() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractMainContent(doc: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            "article",
            "[role='main']",
            "main",
            ".post-content",
            ".article-content",
            ".entry-content",
            ".content-body",
            ".story-body",
            "#content-body",
            ".post-body",
            ".article-body",
            ".story-content"
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null && element.text().length > 200) {
                return cleanExtractedText(element)
            }
        }

        var bestElement: Element? = null
        var maxTextLength = 0
        val paragraphs = doc.select("p")
        for (p in paragraphs) {
            val parent = p.parent() ?: continue
            val textLength = parent.text().length
            if (textLength > maxTextLength && textLength > 200) {
                maxTextLength = textLength
                bestElement = parent
            }
        }

        if (bestElement != null) {
            return cleanExtractedText(bestElement)
        }

        return doc.select("p").joinToString("\n\n") { it.text() }
    }

    private fun cleanExtractedText(element: Element): String {
        element.select("script, style, iframe, .ad, .advertisement, .social-share, .related-articles, .newsletter-signup, noscript").remove()
        return element.select("p, h1, h2, h3, h4, li")
            .joinToString("\n\n") { it.text() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun extractLink(item: Element): String {
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
