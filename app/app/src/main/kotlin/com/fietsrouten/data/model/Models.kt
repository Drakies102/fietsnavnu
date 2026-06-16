package com.fietsrouten.data.model

import com.google.gson.annotations.SerializedName

data class NominatimResult(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("display_name") val displayName: String,
    val lat: String,
    val lon: String
)

// GraphHopper uses [longitude, latitude] order in its points arrays.
data class GraphHopperRequest(
    val points: List<List<Double>>,
    val profile: String = "bike",
    @SerializedName("points_encoded") val pointsEncoded: Boolean = false,
    val locale: String = "nl",
    val instructions: Boolean = true,
    val elevation: Boolean = true
)

data class GraphHopperResponse(
    val paths: List<RoutePath>
)

data class RoutePath(
    val points: GeoJsonLineString,
    val distance: Double,
    val time: Long,
    val instructions: List<RouteInstruction>
)

data class GeoJsonLineString(
    val type: String,
    val coordinates: List<List<Double>>
)

data class RouteInstruction(
    val text: String,
    val distance: Double,
    val time: Long,
    val sign: Int,
    val interval: List<Int>
)

data class RouteResult(
    val coordinates: List<List<Double>>,
    val distanceMeters: Double,
    val durationMs: Long,
    val instructions: List<RouteInstruction>,
    val elevationProfile: List<Double> = emptyList()
)

data class TripSummary(
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double
)

data class Poi(
    val id: Long,
    val name: String,
    val amenity: String,
    val lat: Double,
    val lon: Double
)

data class PoiResponse(
    val features: List<PoiFeature>
)

data class PoiFeature(
    val geometry: PoiGeometry,
    val properties: PoiProperties
)

data class PoiGeometry(val coordinates: List<Double>)

data class PoiProperties(val id: Long, val name: String, val amenity: String)

data class Knooppunt(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val ref: String
)

data class CyclingRoute(
    val id: Long,
    val name: String,
    val network: String,
    val segments: List<List<List<Double>>>
)

data class OverpassResponse(
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val tags: Map<String, String>?,
    val members: List<OverpassMember>?
)

data class OverpassMember(
    val type: String,
    val ref: Long,
    val role: String,
    val geometry: List<OverpassGeomPoint>?
)

data class OverpassGeomPoint(
    val lat: Double,
    val lon: Double
)
