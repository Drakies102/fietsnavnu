package com.fietsrouten.navigation

import com.fietsrouten.data.model.RouteInstruction
import org.maplibre.android.geometry.LatLng

data class NavigationSession(
    val currentInstructionIndex: Int,
    val currentInstruction: RouteInstruction,
    val nextInstruction: RouteInstruction?,
    val distanceToTurnMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationMs: Long,
    val isOffRoute: Boolean,
    val snappedLatLng: LatLng,
    val bearing: Float
)
