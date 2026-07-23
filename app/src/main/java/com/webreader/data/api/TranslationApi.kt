package com.webreader.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatResponse(
    val choices: List<ChatChoice>?
)

data class ChatChoice(
    val message: ChatMessage?
)

interface TranslationApi {
    @Headers("Content-Type: application/json")
    @POST
    suspend fun chat(
        @Url url: String,
        @Body request: ChatRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): ChatResponse
}
