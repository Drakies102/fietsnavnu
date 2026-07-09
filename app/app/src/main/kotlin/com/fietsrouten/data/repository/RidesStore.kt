package com.fietsrouten.data.repository

import android.content.Context
import com.fietsrouten.data.model.RideRecord
import org.json.JSONArray
import org.json.JSONObject

/** Local ride history — JSON array in SharedPreferences, newest first. No backend involved. */
object RidesStore {
    private const val PREFS_NAME = "fietsrouten_rides"
    private const val KEY_RIDES = "rides_json"
    private const val MAX_RIDES = 100

    fun addRide(context: Context, ride: RideRecord) {
        val rides = getRides(context).toMutableList()
        rides.add(0, ride)
        save(context, rides.take(MAX_RIDES))
    }

    fun getRides(context: Context): List<RideRecord> {
        val json = prefs(context).getString(KEY_RIDES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RideRecord(
                    timestampMs = o.getLong("timestampMs"),
                    distanceMeters = o.getDouble("distanceMeters"),
                    durationMs = o.getLong("durationMs"),
                    elevationGainMeters = o.optDouble("elevationGainMeters", 0.0),
                    avgSpeedKmh = o.getDouble("avgSpeedKmh"),
                    profile = o.optString("profile", "bike")
                )
            }
            // Filters out degenerate entries from a prior build that saved a ride on every
            // stopped navigation, even ones with ~0 distance (e.g. stop-right-after-start).
            .filter { it.distanceMeters >= 20.0 }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, rides: List<RideRecord>) {
        val array = JSONArray()
        rides.forEach { ride ->
            array.put(JSONObject().apply {
                put("timestampMs", ride.timestampMs)
                put("distanceMeters", ride.distanceMeters)
                put("durationMs", ride.durationMs)
                put("elevationGainMeters", ride.elevationGainMeters)
                put("avgSpeedKmh", ride.avgSpeedKmh)
                put("profile", ride.profile)
            })
        }
        prefs(context).edit().putString(KEY_RIDES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
