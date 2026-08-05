package com.mlingofeed.data.repository

import com.mlingofeed.data.database.RssFolder
import com.mlingofeed.data.database.RssSubscription
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object OpmlParser {
    data class OpmlFolder(val name: String, val feeds: List<OpmlFeed>)
    data class OpmlFeed(val title: String, val url: String)

    fun parseOpml(opmlContent: String): List<OpmlFolder> {
        try {
            val doc = Jsoup.parse(opmlContent, "", Parser.xmlParser())
            val outlines = doc.select("body > outline")
            val folders = mutableListOf<OpmlFolder>()

            for (outline in outlines) {
                val folderName = outline.attr("text").ifBlank { outline.attr("title") }
                val feedOutlines = outline.select("outline[xmlUrl]")

                if (feedOutlines.isNotEmpty()) {
                    val feeds = feedOutlines.mapNotNull { feedOutline ->
                        val xmlUrl = feedOutline.attr("xmlUrl")
                        val title = feedOutline.attr("text").ifBlank { feedOutline.attr("title") }.ifBlank { xmlUrl }
                        if (xmlUrl.isNotBlank()) OpmlFeed(title, xmlUrl) else null
                    }
                    if (folderName.isNotBlank()) {
                        folders.add(OpmlFolder(folderName, feeds))
                    } else {
                        folders.add(OpmlFolder("Imported", feeds))
                    }
                } else if (outline.hasAttr("xmlUrl")) {
                    val xmlUrl = outline.attr("xmlUrl")
                    val title = outline.attr("text").ifBlank { outline.attr("title") }.ifBlank { xmlUrl }
                    if (xmlUrl.isNotBlank()) {
                        folders.add(OpmlFolder("Imported", listOf(OpmlFeed(title, xmlUrl))))
                    }
                }
            }

            return folders
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun exportToOpml(folders: List<RssFolder>, subscriptions: List<RssSubscription>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<opml version=\"2.0\">")
        sb.appendLine("  <head><title>Web Reader Subscriptions</title></head>")
        sb.appendLine("  <body>")

        for (folder in folders) {
            val folderSubs = subscriptions.filter { it.folderId == folder.id }
            if (folderSubs.isNotEmpty()) {
                sb.appendLine("    <outline text=\"${escapeXml(folder.name)}\" title=\"${escapeXml(folder.name)}\">")
                for (sub in folderSubs) {
                    sb.appendLine("      <outline type=\"rss\" text=\"${escapeXml(sub.title)}\" title=\"${escapeXml(sub.title)}\" xmlUrl=\"${escapeXml(sub.url)}\"/>")
                }
                sb.appendLine("    </outline>")
            }
        }

        val ungroupedSubs = subscriptions.filter { it.folderId == null }
        for (sub in ungroupedSubs) {
            sb.appendLine("    <outline type=\"rss\" text=\"${escapeXml(sub.title)}\" title=\"${escapeXml(sub.title)}\" xmlUrl=\"${escapeXml(sub.url)}\"/>")
        }

        sb.appendLine("  </body>")
        sb.appendLine("</opml>")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
