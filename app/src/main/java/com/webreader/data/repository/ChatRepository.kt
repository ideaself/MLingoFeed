package com.webreader.data.repository

import com.webreader.data.api.ChatMessage
import com.webreader.data.api.ChatRequest
import com.webreader.data.api.TranslationApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.HttpException
import java.io.IOException

class ChatRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
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
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}