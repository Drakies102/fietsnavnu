package com.fietsrouten.navigation

import android.location.Location
import com.fietsrouten.data.model.RouteResult
import org.maplibre.android.geometry.LatLng
import kotlin.math.*

class NavigationEngine {

    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
    }

    private var lastBearing = 0f

    fun update(location: Location, route: RouteResult): NavigationSession {
        val routeLatLngs = route.coordinates.map { LatLng(it[1], it[0]) }
        val userLatLng = LatLng(location.latitude, location.longitude)

        val (nearestIndex, snappedPoint, snapDistance) = findNearestSegment(userLatLng, routeLatLngs)

        val instrIndex = (route.instructions.indexOfFirst { instr ->
            nearestIndex in instr.interval[0]..instr.interval[1]
        }).takeIf { it >= 0 } ?: route.instructions.lastIndex

        val currentInstruction = route.instructions[instrIndex]
        val nextInstruction = route.instructions.getOrNull(instrIndex + 1)

        val instrEndIndex = currentInstruction.interval[1].coerceAtMost(routeLatLngs.lastIndex)
        val distanceToTurn = distanceAlongRoute(nearestIndex, instrEndIndex, routeLatLngs, snappedPoint)
        val remainingDistance = distanceAlongRoute(nearestIndex, routeLatLngs.lastIndex, routeLatLngs, snappedPoint)

        val routeFraction = if (route.distanceMeters > 0) remainingDistance / route.distanceMeters else 0.0
        // When moving, derive ETA from actual GPS speed (m/s); fall back to proportional estimate when stopped
        val remainingDuration = if (location.speed > 0.5f && remainingDistance > 0) {
            (remainingDistance / location.speed * 1000).toLong()
        } else {
            (route.durationMs * routeFraction).toLong()
        }

        // Only use GPS bearing when moving; otherwise hold last known bearing
        if (location.hasBearing() && location.speed > 0.5f) {
            lastBearing = location.bearing
        }

        return NavigationSession(
            currentInstructionIndex = instrIndex,
            currentInstruction = currentInstruction,
            nextInstruction = nextInstruction,
            distanceToTurnMeters = distanceToTurn,
            remainingDistanceMeters = remainingDistance,
            remainingDurationMs = remainingDuration,
            isOffRoute = snapDistance > OFF_ROUTE_THRESHOLD_METERS,
            snappedLatLng = snappedPoint,
            bearing = lastBearing
        )
    }

    private data class SegmentResult(val index: Int, val snappedPoint: LatLng, val distanceMeters: Double)

    private fun findNearestSegment(point: LatLng, route: List<LatLng>): SegmentResult {
        if (route.size < 2) return SegmentResult(0, route[0], haversineMeters(point, route[0]))
        var bestIndex = 0
        var bestSnapped = route[0]
        var bestDist = Double.MAX_VALUE
        for (i in 0 until route.lastIndex) {
            val snapped = snapToSegment(point, route[i], route[i + 1])
            val dist = haversineMeters(point, snapped)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
                bestSnapped = snapped
            }
        }
        return SegmentResult(bestIndex, bestSnapped, bestDist)
    }

    private fun snapToSegment(point: LatLng, a: LatLng, b: LatLng): LatLng {
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return a
        val t = ((point.longitude - a.longitude) * dx + (point.latitude - a.latitude) * dy) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        return LatLng(a.latitude + tc * dy, a.longitude + tc * dx)
    }

    private fun distanceAlongRoute(
        fromIndex: Int, toIndex: Int, route: List<LatLng>, snappedFrom: LatLng
    ): Double {
        if (fromIndex >= toIndex) return 0.0
        var dist = haversineMeters(snappedFrom, route[fromIndex + 1])
        for (i in (fromIndex + 1) until toIndex) {
            dist += haversineMeters(route[i], route[i + 1])
        }
        return dist
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val r = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
