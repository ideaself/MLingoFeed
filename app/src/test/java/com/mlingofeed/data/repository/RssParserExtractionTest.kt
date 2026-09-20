package com.mlingofeed.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the article body extraction. The fixtures mirror the markup of real
 * pages (Al Jazeera news/video, BBC) with placeholder text, so no copyrighted prose is stored
 * in the repository.
 */
class RssParserExtractionTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture fixtures/$name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `al jazeera news page keeps the body and drops page furniture`() {
        val text = RssParser.extractFromHtml(fixture("aljazeera_news.html"))

        assertTrue(text.contains("First real paragraph"))
        assertTrue(text.contains("Second paragraph"))
        assertTrue(text.contains("A list item"))

        assertFalse("headline must not be repeated", text.contains("Headline that the app already shows"))
        assertFalse("dates must be dropped", text.contains("Published On"))
        assertFalse("save widget must be dropped", text.contains("Save"))
        assertFalse("share widget must be dropped", text.contains("copylink"))
        assertFalse("related videos must be dropped", text.contains("Related video teaser"))
    }

    @Test
    fun `video page without a body falls back to the page description`() {
        val text = RssParser.extractFromHtml(fixture("aljazeera_video.html"))

        assertEquals("The US recalled a billion products off the market in just six months.", text)
    }

    @Test
    fun `bbc page drops metadata and link blocks`() {
        val text = RssParser.extractFromHtml(fixture("bbc_article.html"))

        assertTrue(text.contains("Body paragraph one"))
        assertTrue(text.contains("Body paragraph two"))

        assertFalse("dateline must be dropped", text.contains("15 minutes ago"))
        assertFalse("published label must be dropped", text.contains("Published"))
        assertFalse("related link block must be dropped", text.contains("Promoted story headline"))
    }

    @Test
    fun `short chrome lines are filtered but real prose stays`() {
        val text = RssParser.extractFromHtml(fixture("generic_chrome.html"))

        assertTrue(text.contains("Real prose sentence one"))
        assertTrue(text.contains("Another genuine paragraph"))

        assertFalse(text.contains("Published 2 hours ago"))
        assertFalse(text.contains("Updated 1 hour ago"))
        assertFalse(text.contains("copylink"))
        assertFalse(text.lines().any { it.trim() == "Save" })
    }
}
