package com.mlingofeed.data.api

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
    val stream: Boolean = false,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.3
)

data class ChatResponse(
    val choices: List<ChatChoice>?
)

data class ChatChoice(
    val message: ChatMessage?
)

data class ModelListResponse(
    @SerializedName("data") val data: List<ModelInfo>?
)

data class ModelInfo(
    val id: String,
    @SerializedName("object") val objectType: String?
)

interface TranslationApi {
    @Headers("Content-Type: application/json")
    @POST
    suspend fun chat(
        @Url url: String,
        @Body request: ChatRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): ChatResponse

    @retrofit2.http.GET
    suspend fun getModels(
        @Url url: String,
        @retrofit2.http.Header("Authorization") authorization: String
    ): ModelListResponse
}
