package com.fietsrouten.data.api

import com.fietsrouten.data.model.OverpassResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassApi {
    @GET("api/interpreter")
    suspend fun query(@Query("data") query: String): OverpassResponse
}
