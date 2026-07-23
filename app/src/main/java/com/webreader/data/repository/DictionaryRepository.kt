package com.webreader.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DictionaryRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun lookupWord(urlTemplate: String, cssSelector: String, word: String): String {
        return try {
            val encodedWord = URLEncoder.encode(word, "UTF-8")
            val url = urlTemplate.replace("{word}", encodedWord)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Error: HTTP ${response.code}"

                val body = response.body?.string() ?: return "No result"

                if (cssSelector.isNotEmpty()) {
                    parseHtmlResult(body, cssSelector, urlTemplate)
                } else {
                    parseDefaultResult(body, urlTemplate)
                }
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun parseHtmlResult(html: String, cssSelector: String, urlTemplate: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val elements = doc.select(cssSelector)

            if (elements.isEmpty()) {
                return "No content found with selector: $cssSelector"
            }

            val sb = StringBuilder()
            elements.forEachIndexed { index, element ->
                val text = element.text().trim()
                if (text.isNotEmpty()) {
                    if (index > 0) sb.appendLine()
                    sb.appendLine(text)
                }
            }

            if (sb.isEmpty()) {
                "No readable content found"
            } else {
                sb.toString().trim()
            }
        } catch (e: Exception) {
            "Parse error: ${e.message}"
        }
    }

    private fun parseDefaultResult(json: String, urlTemplate: String): String {
        if (urlTemplate.contains("youdao.com/jsonapi") || urlTemplate.contains("jsonversion")) {
            return parseYoudaoResponse(json)
        }

        if (json.startsWith("{") || json.startsWith("[")) {
            return try {
                val obj = JSONObject(json)
                prettyPrintJson(obj)
            } catch (e: Exception) {
                json
            }
        }

        return json.take(2000)
    }

    private fun parseYoudaoResponse(json: String): String {
        return try {
            val obj = JSONObject(json)
            val sb = StringBuilder()

            val simple = obj.optJSONObject("simple")
            if (simple != null) {
                val wordArray = simple.optJSONArray("word")
                if (wordArray != null && wordArray.length() > 0) {
                    val wordObj = wordArray.getJSONObject(0)
                    val usPhone = wordObj.optString("usphone", "")
                    val ukPhone = wordObj.optString("ukphone", "")
                    if (usPhone.isNotEmpty()) sb.appendLine("US: /$usPhone/")
                    if (ukPhone.isNotEmpty()) sb.appendLine("UK: /$ukPhone/")
                }
            }

            val ec = obj.optJSONObject("ec")
            if (ec != null) {
                val wordArray = ec.optJSONArray("word")
                if (wordArray != null) {
                    for (i in 0 until wordArray.length()) {
                        val wordObj = wordArray.getJSONObject(i)
                        val usPhone = wordObj.optString("usphone", "")
                        val ukPhone = wordObj.optString("ukphone", "")
                        if (usPhone.isNotEmpty()) sb.appendLine("US: /$usPhone/")
                        if (ukPhone.isNotEmpty()) sb.appendLine("UK: /$ukPhone/")

                        val trs = wordObj.optJSONArray("trs")
                        if (trs != null) {
                            for (j in 0 until trs.length()) {
                                val tr = trs.getJSONObject(j)
                                val trArray = tr.optJSONArray("tr")
                                if (trArray != null) {
                                    for (k in 0 until trArray.length()) {
                                        val trObj = trArray.getJSONObject(k)
                                        val l = trObj.optJSONObject("l")
                                        if (l != null) {
                                            val iArray = l.optJSONArray("i")
                                            if (iArray != null) {
                                                for (m in 0 until iArray.length()) {
                                                    sb.appendLine("• ${iArray.getString(m)}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val blngSents = obj.optJSONObject("blng_sents_part")
            if (blngSents != null) {
                val sentencePair = blngSents.optJSONArray("sentence-pair")
                if (sentencePair != null && sentencePair.length() > 0) {
                    sb.appendLine("\nExamples:")
                    for (i in 0 until minOf(sentencePair.length(), 3)) {
                        val pair = sentencePair.getJSONObject(i)
                        val en = pair.optString("sentence", "")
                        val zh = pair.optString("sentence-translation", "")
                        if (en.isNotEmpty()) sb.appendLine("• $en")
                        if (zh.isNotEmpty()) sb.appendLine("  $zh")
                    }
                }
            }

            if (sb.isEmpty()) "No definition found"
            else sb.toString().trim()
        } catch (e: Exception) {
            "Parse error: ${e.message}"
        }
    }

    private fun prettyPrintJson(obj: JSONObject, indent: String = ""): String {
        val sb = StringBuilder()
        val keys = obj.keys()
        var first = true
        while (keys.hasNext()) {
            if (!first) sb.appendLine()
            first = false
            val key = keys.next()
            val value = obj.get(key)
            sb.append("$indent\"$key\": ")
            when (value) {
                is JSONObject -> sb.append(prettyPrintJson(value, "$indent  "))
                is org.json.JSONArray -> sb.append(prettyPrintArray(value, "$indent  "))
                is String -> sb.append("\"$value\"")
                else -> sb.append(value.toString())
            }
        }
        return sb.toString()
    }

    private fun prettyPrintArray(array: org.json.JSONArray, indent: String): String {
        val sb = StringBuilder()
        sb.appendLine("[")
        for (i in 0 until array.length()) {
            val value = array.get(i)
            sb.append("$indent  ")
            when (value) {
                is JSONObject -> sb.append(prettyPrintJson(value, "$indent  "))
                is org.json.JSONArray -> sb.append(prettyPrintArray(value, "$indent  "))
                is String -> sb.append("\"$value\"")
                else -> sb.append(value.toString())
            }
            if (i < array.length() - 1) sb.append(",")
            sb.appendLine()
        }
        sb.append("$indent]")
        return sb.toString()
    }
}