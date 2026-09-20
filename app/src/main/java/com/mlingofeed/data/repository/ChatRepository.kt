package com.mlingofeed.data.repository

import com.mlingofeed.data.api.ChatMessage
import com.mlingofeed.data.api.ChatRequest
import com.mlingofeed.data.api.HttpClient
import com.mlingofeed.data.api.TranslationApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.HttpException
import java.io.IOException

class ChatRepository {

    private val okHttpClient = HttpClient.shared.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(TranslationApi::class.java)

    suspend fun translate(text: String, targetLang: String, apiUrl: String, apiKey: String, model: String): String {
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = "You are a professional translator. Translate the following text to $targetLang. Only provide the translation, no explanation."
            ),
            ChatMessage(
                role = "user",
                content = text
            )
        )

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = false
        )

        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: "No translation available"
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = false
        )

        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: "No response"
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Streams a chat completion (OpenAI-style SSE) and invokes [onDelta] for each text fragment.
     * Returns the full reply, or a localised error string when the call fails.
     */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        apiUrl: String,
        apiKey: String,
        model: String,
        onDelta: (String) -> Unit
    ): String {
        val request = ChatRequest(model = model, messages = messages, stream = true)
        return try {
            val body = api.chatStream(apiUrl, request, "Bearer $apiKey")
            val source = body.source()
            val full = StringBuilder()
            val reasoning = StringBuilder()
            // readUtf8Line() blocks and ignores coroutine cancellation, so closing the body from
            // the cancelling thread is what actually stops an abandoned stream.
            val cancellationHandler = kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion {
                runCatching { body.close() }
            }
            try {
                while (true) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val deltaObject = try {
                        org.json.JSONObject(payload)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                    } catch (_: Exception) {
                        null
                    }
                    // JSON nulls (e.g. content while a reasoning model is still thinking) must not
                    // become the literal string "null".
                    val delta = (deltaObject?.opt("content") as? String).orEmpty()
                    if (delta.isNotEmpty()) {
                        full.append(delta)
                        onDelta(delta)
                        continue
                    }
                    val thinking = (deltaObject?.opt("reasoning_content") as? String).orEmpty()
                    if (thinking.isNotEmpty()) reasoning.append(thinking)
                }
                full.toString().ifBlank { reasoning.toString().ifBlank { "No response" } }
            } finally {
                cancellationHandler?.dispose()
                runCatching { body.close() }
            }
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun fetchModels(baseApiUrl: String, apiKey: String): List<String> {
        val modelsUrl = if (baseApiUrl.contains("/chat/completions")) {
            baseApiUrl.replace("/chat/completions", "/models")
        } else {
            "${baseApiUrl.trimEnd('/')}/models"
        }
        return try {
            val response = api.getModels(modelsUrl, "Bearer $apiKey")
            response.data?.map { it.id }?.sorted() ?: emptyList()
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            throw Exception("API Error ${e.code()}: $errorBody")
        } catch (e: IOException) {
            throw Exception("Network error: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Error: ${e.message}")
        }
    }

    suspend fun analyzeDifficulty(
        text: String,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val truncatedText = if (text.length > 3000) text.substring(0, 3000) else text
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = """You are an English language learning assistant. Analyze the following English text and return a JSON object with these fields:
- "cefrLevel": CEFR level (A1, A2, B1, B2, C1, or C2)
- "difficulty": difficulty description in Chinese (简单/中等/困难/非常困难)
- "wordCount": approximate word count
- "avgSentenceLength": average words per sentence
- "suggestions": array of 2-3 learning suggestions in Chinese
- "keyVocabulary": array of 3-5 advanced words with brief definitions in Chinese

Return ONLY the JSON object, no other text."""
            ),
            ChatMessage(
                role = "user",
                content = truncatedText
            )
        )

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = 0.3
        )

        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: "{}"
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun detectCollocations(
        text: String,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val truncatedText = if (text.length > 3000) text.substring(0, 3000) else text
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = """You are an English language teaching expert. Analyze the following English text and find collocations, idioms, phrasal verbs, and authentic expressions.

Return a JSON array of objects, each with:
- "phrase": the collocation/idiom/phrase
- "type": one of "collocation", "idiom", "phrasal_verb", "expression", "fixed搭配"
- "meaning": brief Chinese explanation
- "example": the original sentence from the text containing this phrase
- "similar": a similar alternative expression (optional)

Find 5-10 items. Return ONLY the JSON array, no other text."""
            ),
            ChatMessage(
                role = "user",
                content = truncatedText
            )
        )

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = 0.3
        )

        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: "[]"
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun generateMnemonic(
        word: String,
        definition: String,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = """You are an English vocabulary tutor. Reply in Chinese with two short parts:
1) 助记: a memory hook (词根词缀/联想/谐音) for the word.
2) 例句: one simple English sentence using the word plus its Chinese translation.
Keep the whole reply under 80 words. No markdown headings."""
            ),
            ChatMessage(
                role = "user",
                content = "Word: $word\nDefinition: $definition"
            )
        )

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = 0.6
        )

        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: ""
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun summarize(
        text: String,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val truncated = if (text.length > 4000) text.substring(0, 4000) else text
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = "You are a news editor. Summarize the article in 2-3 sentences of Chinese. " +
                    "Plain text only, no markdown, no headings, no bullet lists."
            ),
            ChatMessage(role = "user", content = truncated)
        )
        val request = ChatRequest(model = model, messages = messages, stream = false, temperature = 0.3)
        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: ""
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun explainWord(
        word: String,
        definition: String,
        apiUrl: String,
        apiKey: String,
        model: String
    ): String {
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = "You are an English vocabulary tutor. Reply in Chinese covering: 常见搭配 (2-3), " +
                    "同义词/反义词, and 使用注意 or CEFR 词频. Keep under 100 words. Plain text, no markdown."
            ),
            ChatMessage(role = "user", content = "Word: $word\nDefinition: $definition")
        )
        val request = ChatRequest(model = model, messages = messages, stream = false, temperature = 0.5)
        return try {
            val response = api.chat(apiUrl, request, "Bearer $apiKey")
            response.choices?.firstOrNull()?.message?.content ?: ""
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            "API Error ${e.code()}: $errorBody"
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}