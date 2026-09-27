package com.parentstracker.app.util

import com.parentstracker.app.data.SavedPlace
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Location older than this is shown as "may be offline". */
    const val STALE_AFTER_MS = 2 * 60 * 60_000L

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1_000 -> "${meters.roundToInt()} m"
        meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1_000)
        else -> "${(meters / 1_000).roundToInt()} km"
    }

    fun timeAgo(then: Long, now: Long): String {
        if (then <= 0) return "never"
        val diff = now - then
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> "${diff / 86_400_000} d ago"
        }
    }

    fun coord(value: Double): String = String.format(Locale.US, "%.6f", value)

    fun mapsLink(lat: Double, lng: Double) = "https://maps.google.com/?q=${coord(lat)},${coord(lng)}"

    fun directionsLink(lat: Double, lng: Double) =
        "https://www.google.com/maps/dir/?api=1&destination=${coord(lat)},${coord(lng)}"

    /** The closest saved place whose radius (plus [extraMeters] of slack) contains the point. */
    fun placeFor(lat: Double, lng: Double, places: List<SavedPlace>, extraMeters: Double = 0.0): SavedPlace? =
        places
            .map { it to distanceMeters(lat, lng, it.lat, it.lng) }
            .filter { (place, distance) -> distance <= place.radiusMeters + extraMeters }
            .minByOrNull { it.second }
            ?.first
}
