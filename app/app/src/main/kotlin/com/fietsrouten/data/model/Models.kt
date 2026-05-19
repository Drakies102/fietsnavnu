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
    val instructions: Boolean = true
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
    val instructions: List<RouteInstruction>
)
