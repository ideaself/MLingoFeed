package com.mlingofeed.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface DictionaryApi {
    @GET
    suspend fun lookupWord(
        @Url url: String,
        @Query("word") word: String
    ): String
}
