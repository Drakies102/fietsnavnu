package com.fietsrouten.data.api

import com.fietsrouten.data.model.GraphHopperRequest
import com.fietsrouten.data.model.GraphHopperResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface GraphHopperApi {
    @POST("route")
    suspend fun getRoute(@Body request: GraphHopperRequest): GraphHopperResponse
}
