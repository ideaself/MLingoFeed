package com.mlingofeed.data.repository

import com.mlingofeed.data.api.HttpClient
import com.mlingofeed.data.api.await
import com.mlingofeed.data.database.RssArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object RssParser {
    private val client = HttpClient.shared.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

    private val WHITESPACE_REGEX = Regex("\\n{3,}")

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH)
    ).map { it.withLocale(Locale.ENGLISH) }

    suspend fun parse(subscriptionId: Long, rssUrl: String): List<RssArticle> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", BROWSER_UA)
                .build()
            val body = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: return@withContext emptyList()
            }
            val doc = Jsoup.parse(body, "", Parser.xmlParser())

            val articles = mutableListOf<RssArticle>()

            var items = doc.select("item")
            if (items.isEmpty()) {
                items = doc.select("entry")
            }

            for (item in items) {
                val rawTitle = item.selectFirst("title")?.text()?.trim() ?: continue
                val link = extractLink(item)
                if (link.isBlank()) continue
                val rawDescription = item.selectFirst("description")?.text()?.trim()
                    ?: item.selectFirst("summary")?.text()?.trim()
                    ?: item.selectFirst("content")?.text()?.trim()
                    ?: ""
                val pubDate = parseDate(
                    item.selectFirst("pubDate")?.text()
                        ?: item.selectFirst("published")?.text()
                        ?: item.selectFirst("updated")?.text()
                        ?: item.selectFirst("dc|date")?.text()
                        ?: ""
                )

                articles.add(
                    RssArticle(
                        subscriptionId = subscriptionId,
                        title = stripHtml(rawTitle),
                        link = link,
                        description = stripHtml(rawDescription).take(300),
                        pubDate = pubDate
                    )
                )
            }

            articles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchFullContent(articleUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val body = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext ""
                response.body?.string() ?: return@withContext ""
            }
            val doc = Jsoup.parse(body)

            doc.select("script, style, nav, header, footer, aside, .ad, .advertisement, .social-share, .comments, noscript").remove()

            val content = extractMainContent(doc)
            if (content.length > 200) {
                return@withContext content
            }

            doc.body()?.text() ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    private fun stripHtml(text: String): String {
        val plain = if (text.contains('<')) Jsoup.parseBodyFragment(text).text() else text
        // Feeds read through the XML parser leave entities (e.g. &#039;) as literal text, and
        // some double-encode them, so decode twice.
        return Parser.unescapeEntities(Parser.unescapeEntities(plain, false), false)
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
        val parentTextLengths = HashMap<Element, Int>()
        val paragraphs = doc.select("p")
        for (p in paragraphs) {
            val parent = p.parent() ?: continue
            val textLength = parentTextLengths.getOrPut(parent) { parent.text().length }
            if (textLength > maxTextLength && textLength > 200) {
                maxTextLength = textLength
                bestElement = parent
            }
        }

        if (bestElement != null) {
            return cleanExtractedText(bestElement)
        }

        return paragraphs.joinToString("\n\n") { it.text() }
    }

    private fun cleanExtractedText(element: Element): String {
        element.select("script, style, iframe, .ad, .advertisement, .social-share, .related-articles, .newsletter-signup, noscript").remove()
        return element.select("p, h1, h2, h3, h4, li")
            .joinToString("\n\n") { it.text() }
            .replace(WHITESPACE_REGEX, "\n\n")
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
        val trimmed = dateStr.trim()
        for (formatter in DATE_FORMATTERS) {
            try {
                val parsed = formatter.parseBest(
                    trimmed,
                    Instant::from,
                    ZonedDateTime::from,
                    OffsetDateTime::from,
                    LocalDateTime::from,
                    LocalDate::from
                )
                return when (parsed) {
                    is Instant -> parsed.toEpochMilli()
                    is ZonedDateTime -> parsed.toInstant().toEpochMilli()
                    is OffsetDateTime -> parsed.toInstant().toEpochMilli()
                    is LocalDateTime -> parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    is LocalDate -> parsed.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else -> continue
                }
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
