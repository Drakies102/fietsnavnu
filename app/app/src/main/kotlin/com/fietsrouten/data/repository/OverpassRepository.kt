package com.fietsrouten.data.repository

import com.fietsrouten.Config
import com.fietsrouten.data.api.OverpassApi
import com.fietsrouten.data.model.CyclingRoute
import com.fietsrouten.data.model.Knooppunt
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.abs

class OverpassRepository {

    private val api: OverpassApi = Retrofit.Builder()
        .baseUrl(Config.OVERPASS_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Accept", "*/*")
                            .header("User-Agent", "FietsNavNu/1.0 Android")
                            .build()
                    )
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OverpassApi::class.java)

    suspend fun getKnoopunten(
        south: Double, west: Double, north: Double, east: Double
    ): List<Knooppunt> {
        val bbox = "$south,$west,$north,$east"
        val query = "[out:json];node[rcn_ref]($bbox);out;"
        return api.query(query).elements.mapNotNull { el ->
            if (el.type == "node" && el.lat != null && el.lon != null) {
                val ref = el.tags?.get("rcn_ref") ?: return@mapNotNull null
                Knooppunt(el.id, el.lat, el.lon, ref)
            } else null
        }
    }

    suspend fun getCyclingRoutes(
        south: Double, west: Double, north: Double, east: Double
    ): List<CyclingRoute> {
        val bbox = "$south,$west,$north,$east"
        val query = """[out:json];relation[route=bicycle][network~"ncn|rcn|lcn"]($bbox);out geom;"""
        return api.query(query).elements.mapNotNull { el ->
            if (el.type != "relation") return@mapNotNull null
            val name = el.tags?.get("name") ?: el.tags?.get("ref") ?: return@mapNotNull null
            val network = el.tags?.get("network") ?: "lcn"
            val segments = el.members?.mapNotNull { member ->
                member.geometry
                    ?.takeIf { it.size >= 2 }
                    ?.map { pt -> listOf(pt.lon, pt.lat) }
            } ?: return@mapNotNull null
            if (segments.isEmpty()) return@mapNotNull null
            CyclingRoute(el.id, name, network, segments)
        }
    }

    fun filterKnoopuntenNearRoute(
        nodes: List<Knooppunt>,
        routeCoords: List<List<Double>>
    ): List<Knooppunt> {
        val sample = routeCoords.filterIndexed { i, _ -> i % 5 == 0 }
        return nodes.filter { node ->
            sample.any { pt ->
                abs(node.lat - pt[1]) < 0.001 && abs(node.lon - pt[0]) < 0.0015
            }
        }
    }
}
