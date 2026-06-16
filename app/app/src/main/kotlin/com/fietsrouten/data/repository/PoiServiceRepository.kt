package com.fietsrouten.data.repository

import com.fietsrouten.Config
import com.fietsrouten.data.api.PoiApi
import com.fietsrouten.data.model.Poi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class PoiServiceRepository {

    private val api: PoiApi = Retrofit.Builder()
        .baseUrl(Config.POI_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PoiApi::class.java)

    suspend fun getPoisInBbox(
        south: Double, west: Double, north: Double, east: Double
    ): List<Poi> = api.getPois(south, west, north, east).features.map { f ->
        Poi(
            id = f.properties.id,
            name = f.properties.name,
            amenity = f.properties.amenity,
            lat = f.geometry.coordinates[1],
            lon = f.geometry.coordinates[0]
        )
    }

    fun filterNearRoute(pois: List<Poi>, routeCoords: List<List<Double>>): List<Poi> {
        val sample = routeCoords.filterIndexed { i, _ -> i % 5 == 0 }
        return pois.filter { poi ->
            sample.any { pt -> haversineMeters(poi.lat, poi.lon, pt[1], pt[0]) <= 150.0 }
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val phi1 = Math.toRadians(lat1); val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(a))
    }
}
