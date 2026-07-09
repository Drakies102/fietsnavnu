package com.fietsrouten.data.model

data class RideRecord(
    val timestampMs: Long,
    val distanceMeters: Double,
    val durationMs: Long,
    val elevationGainMeters: Double,
    val avgSpeedKmh: Double,
    val profile: String
)
