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
    private val LIST_MARKER_REGEX = Regex("(?i)^list\\s+\\d+\\s+of\\s+\\d+")
    private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")

    /** Below this the extraction is treated as failed and the page description is used instead. */
    private const val MIN_CONTENT_LENGTH = 200
    private const val MIN_DESCRIPTION_LENGTH = 60
    private const val MIN_SELECTOR_SCORE = 300

    /** Page furniture that must never end up in the article body. */
    private const val CHROME_SELECTOR =
        "script, style, noscript, iframe, form, button, input, select, textarea, svg, dialog, video, audio, " +
            "nav, header, footer, aside, [hidden], .hidden, .u-hidden, [aria-hidden=true], " +
            "[data-component*=related], [data-component*=promo], [data-testid=metadata], [data-block=links]"

    /**
     * Class/id names that always belong to page furniture (ads, share widgets, comment sections, ...).
     * Lookarounds keep real words such as "commentary" or "padding" from matching.
     */
    private val CHROME_NAME_REGEX = Regex(
        "(?<![A-Za-z])(?i:advert|advertis|sponsor|social|share|shared|comment|related|recommend|promo|" +
            "newsletter|subscribe|signup|sign-up|popup|modal|cookie|breadcrumb|sidebar|widget|toolbar|" +
            "trending|most-read|read-more|reading-list|skip-link|follow|personaliz|banner)(?![a-z])"
    )

    /** Short lines that are metadata or social widgets rather than prose. */
    private val CHROME_LINE_REGEX = Regex(
        "(?i)^(published|updated|advertisement|sponsored|read more|watch more|follow us|sign up|subscribe|" +
            "most read|trending)\\b.*|^\\d+\\s*(min|mins|minute|minutes|hour|hours|day|days)\\s+(read|ago)$|" +
            "^why follow\\?$|^follow this (section|tag|topic)\\b.*|^go to your personalized feed$|" +
            "^custom feed:.*|^smart alerts:.*|^update your preferences.*"
    )

    private const val CONTENT_ELEMENTS = "p, h2, h3, h4, li, blockquote"

    private val DESCRIPTION_SELECTORS = listOf(
        "meta[name=description]",
        "meta[property=og:description]",
        "meta[name=twitter:description]"
    )

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
            stripPageChrome(doc)

            val content = extractMainContent(doc)
            if (content.length >= MIN_CONTENT_LENGTH) {
                return@withContext content
            }

            // Video pages and galleries often have no body at all; their social/meta description
            // is real prose and beats returning leftover page furniture.
            val description = extractDescription(doc)
            if (description.length >= MIN_DESCRIPTION_LENGTH) description else content
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

    /** Removes navigation, ads, share and comment widgets so the extraction only sees the article. */
    private fun stripPageChrome(doc: org.jsoup.nodes.Document) {
        for (element in doc.allElements) {
            if (element === doc || element.tagName() == "html" || element.tagName() == "body") continue
            val className = element.className()
            if (className.isNotEmpty() && CHROME_NAME_REGEX.containsMatchIn(className)) {
                element.remove()
                continue
            }
            val id = element.id()
            if (id.isNotEmpty() && CHROME_NAME_REGEX.containsMatchIn(id)) {
                element.remove()
            }
        }
        doc.select(CHROME_SELECTOR).remove()
    }

    private fun extractMainContent(doc: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            "[itemprop=articleBody]",
            "[data-article-body]",
            "[data-component=text-block]",
            ".wysiwyg--all-content",
            ".wysiwyg",
            ".article-body",
            ".article__body",
            ".article-body-content",
            ".story-body",
            ".story-content",
            ".article-content",
            ".entry-content",
            ".post-content",
            ".post-body",
            ".content-body",
            "#content-body",
            ".article-text",
            ".body-content",
            ".RichTextStoryBody",
            ".text-block-container",
            "article",
            "[role='main']",
            "main"
        )

        var bestElement: Element? = null
        var bestScore = 0
        for (selector in selectors) {
            for (element in doc.select(selector).take(5)) {
                if (element.text().length < 200) continue
                val score = readableScore(element)
                if (score > bestScore) {
                    bestScore = score
                    bestElement = element
                }
            }
        }

        var content = if (bestElement != null && bestScore >= MIN_SELECTOR_SCORE) {
            cleanExtractedText(bestElement!!)
        } else ""

        // Last resort: the densest paragraph container, e.g. pages without semantic wrappers.
        if (content.length < MIN_CONTENT_LENGTH) {
            val dense = densestParagraphContainer(doc)
            if (dense != null && readableScore(dense) > bestScore) {
                val denseText = cleanExtractedText(dense)
                if (denseText.length > content.length) content = denseText
            }
        }

        return content
    }

    private fun densestParagraphContainer(doc: org.jsoup.nodes.Document): Element? {
        var best: Element? = null
        var bestScore = 0
        val scores = HashMap<Element, Int>()
        for (paragraph in doc.select("p")) {
            val parent = paragraph.parent() ?: continue
            val score = scores.getOrPut(parent) { readableScore(parent) }
            if (score > bestScore) {
                bestScore = score
                best = parent
            }
        }
        return best
    }

    /** Total length of the readable paragraphs inside [element]; used to pick the real article. */
    private fun readableScore(element: Element): Int {
        var total = 0
        for (paragraph in element.select(CONTENT_ELEMENTS)) {
            val text = paragraph.text().trim()
            if (isReadableParagraph(text)) total += text.length.coerceAtMost(200)
        }
        return total
    }

    private fun cleanExtractedText(element: Element): String {
        return element.select(CONTENT_ELEMENTS)
            .map { it.text().trim() }
            .filter { isReadableParagraph(it) }
            .joinToString("\n\n")
            .replace(WHITESPACE_REGEX, "\n\n")
            .trim()
    }

    /** Keeps real prose and drops short page furniture such as "Save", "Share" or "Follow us". */
    private fun isReadableParagraph(text: String): Boolean {
        if (LIST_MARKER_REGEX.containsMatchIn(text)) return false
        if (text.length < 80 && CHROME_LINE_REGEX.containsMatchIn(text)) return false
        if (text.length >= 25) return true
        return text.split(WHITESPACE_SPLIT_REGEX).count { it.isNotBlank() } >= 4
    }

    private fun extractDescription(doc: org.jsoup.nodes.Document): String {
        for (selector in DESCRIPTION_SELECTORS) {
            val meta = doc.selectFirst(selector) ?: continue
            val content = meta.attr("content").trim()
            if (content.isNotEmpty()) return content
        }
        return ""
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
