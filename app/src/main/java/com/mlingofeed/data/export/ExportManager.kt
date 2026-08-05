package com.mlingofeed.data.export

import android.content.Context
import android.net.Uri
import com.mlingofeed.data.database.Bookmark
import com.mlingofeed.data.database.RssSubscription
import org.json.JSONArray
import org.json.JSONObject

data class ExportData(
    val bookmarks: List<Bookmark>,
    val settings: Map<String, String>,
    val subscriptions: List<RssSubscription> = emptyList()
)

object ExportManager {

    private const val CURRENT_VERSION = 2

    fun exportToJson(
        context: Context,
        uri: Uri,
        bookmarks: List<Bookmark>,
        settings: Map<String, String>,
        subscriptions: List<RssSubscription> = emptyList()
    ): Boolean {
        return try {
            val root = JSONObject().apply {
                put("version", CURRENT_VERSION)
                put("exportedAt", System.currentTimeMillis())

                val bmArray = JSONArray()
                bookmarks.forEach { bm ->
                    bmArray.put(JSONObject().apply {
                        put("title", bm.title)
                        put("url", bm.url)
                        put("createdAt", bm.createdAt)
                    })
                }
                put("bookmarks", bmArray)

                val subArray = JSONArray()
                subscriptions.forEach { sub ->
                    subArray.put(JSONObject().apply {
                        put("title", sub.title)
                        put("url", sub.url)
                    })
                }
                put("rssSubscriptions", subArray)

                put("settings", JSONObject(settings))
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun importFromJson(context: Context, uri: Uri): ExportData? {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null

            val root = JSONObject(jsonString)

            val bmArray = root.optJSONArray("bookmarks")
            val bookmarks = mutableListOf<Bookmark>()
            if (bmArray != null) {
                for (i in 0 until bmArray.length()) {
                    val obj = bmArray.getJSONObject(i)
                    bookmarks.add(
                        Bookmark(
                            title = obj.optString("title", ""),
                            url = obj.optString("url", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val settingsMap = mutableMapOf<String, String>()
            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                for (key in settingsObj.keys()) {
                    settingsMap[key] = settingsObj.optString(key, "")
                }
            }

            val subscriptions = mutableListOf<RssSubscription>()
            val subArray = root.optJSONArray("rssSubscriptions")
            if (subArray != null) {
                for (i in 0 until subArray.length()) {
                    val obj = subArray.getJSONObject(i)
                    subscriptions.add(
                        RssSubscription(
                            title = obj.optString("title", ""),
                            url = obj.optString("url", "")
                        )
                    )
                }
            }

            ExportData(bookmarks = bookmarks, settings = settingsMap, subscriptions = subscriptions)
        } catch (e: Exception) {
            null
        }
    }
}
