package com.fietsrouten.data.repository

import com.fietsrouten.Config
import com.fietsrouten.data.api.PoiApi
import com.fietsrouten.data.model.Poi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.abs

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

    // Keep only POIs within ~300m of any route coordinate
    fun filterNearRoute(pois: List<Poi>, routeCoords: List<List<Double>>): List<Poi> {
        val sample = routeCoords.filterIndexed { i, _ -> i % 3 == 0 }
        return pois.filter { poi ->
            sample.any { pt ->
                abs(poi.lat - pt[1]) < 0.003 && abs(poi.lon - pt[0]) < 0.004
            }
        }
    }
}
