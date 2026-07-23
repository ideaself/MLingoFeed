package com.webreader.data.repository

import com.webreader.data.api.DictionaryApi
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class DictionaryRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(okHttpClient)
        .build()

    private val api = retrofit.create(DictionaryApi::class.java)

    suspend fun lookupWord(dictionaryUrl: String, word: String): String {
        return try {
            val request = Request.Builder()
                .url("$dictionaryUrl$word")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Lookup failed: ${response.code}"
                val body = response.body?.string() ?: return "No result"
                parseYoudaoResponse(body)
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
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
}
