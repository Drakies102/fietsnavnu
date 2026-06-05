package com.fietsrouten.data.api

import com.fietsrouten.data.model.PoiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PoiApi {
    @GET("api/pois")
    suspend fun getPois(
        @Query("south") south: Double,
        @Query("west") west: Double,
        @Query("north") north: Double,
        @Query("east") east: Double,
        @Query("types") types: String = "cafe,restaurant,fast_food,bar,bakery,pub"
    ): PoiResponse
}
