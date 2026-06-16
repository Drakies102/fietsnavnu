package com.fietsrouten.data.repository

import com.fietsrouten.Config
import com.fietsrouten.data.api.GraphHopperApi
import com.fietsrouten.data.api.NominatimApi
import com.fietsrouten.data.model.GraphHopperRequest
import com.fietsrouten.data.model.NominatimResult
import com.fietsrouten.data.model.RouteResult
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RouteRepository {

    private val nominatimApi: NominatimApi = Retrofit.Builder()
        .baseUrl(Config.NOMINATIM_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimApi::class.java)

    private val graphHopperApi: GraphHopperApi = Retrofit.Builder()
        .baseUrl(Config.GRAPHHOPPER_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GraphHopperApi::class.java)

    suspend fun searchAddress(query: String): List<NominatimResult> =
        nominatimApi.search(query)

    suspend fun getRoute(
        waypoints: List<Pair<Double, Double>>,
        profile: String = "bike"
    ): RouteResult {
        // GraphHopper expects [longitude, latitude] order
        val request = GraphHopperRequest(
            points = waypoints.map { (lat, lon) -> listOf(lon, lat) },
            profile = profile
        )
        val response = graphHopperApi.getRoute(request)
        val path = response.paths.first()
        val elevations = path.points.coordinates.map { if (it.size >= 3) it[2] else 0.0 }
        return RouteResult(
            coordinates = path.points.coordinates,
            distanceMeters = path.distance,
            durationMs = path.time,
            instructions = path.instructions,
            elevationProfile = elevations
        )
    }

    suspend fun getRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
        profile: String = "bike"
    ): RouteResult = getRoute(listOf(fromLat to fromLon, toLat to toLon), profile)
}
