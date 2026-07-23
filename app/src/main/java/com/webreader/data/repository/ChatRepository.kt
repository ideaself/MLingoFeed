package com.webreader.data.repository

import com.webreader.data.api.ChatMessage
import com.webreader.data.api.ChatRequest
import com.webreader.data.api.TranslationApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ChatRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com/")
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
        } catch (e: Exception) {
            "Translation error: ${e.message}"
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
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
