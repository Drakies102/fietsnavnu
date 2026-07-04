package com.fietsrouten.data.api

import com.fietsrouten.data.model.NominatimResult
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "nl"
    ): List<NominatimResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): NominatimResult
}
