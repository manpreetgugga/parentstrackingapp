package com.parentstracker.app.util

import com.parentstracker.app.data.TrailPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Keeps a compact 24-hour path and turns it into a list of stops for the timeline. */
object Trail {
    const val KEEP_MS = 24 * 60 * 60_000L
    const val MIN_GAP_MS = 10 * 60_000L
    const val MIN_MOVE_METERS = 100.0
    const val MAX_POINTS = 150
    private const val STOP_RADIUS_METERS = 150.0

    /** Adds [point] if enough time passed or the person moved; drops points older than 24 h. */
    fun append(trail: List<TrailPoint>, point: TrailPoint): List<TrailPoint> {
        val recent = trail.filter { point.time - it.time <= KEEP_MS }
        val last = recent.lastOrNull()
        val add = last == null ||
            point.time - last.time >= MIN_GAP_MS ||
            Geo.distanceMeters(last.lat, last.lng, point.lat, point.lng) >= MIN_MOVE_METERS
        return (if (add) recent + point else recent).takeLast(MAX_POINTS)
    }

    data class Stop(
        val from: Long,
        val to: Long,
        val lat: Double,
        val lng: Double,
        val address: String?,
    )

    /** Groups consecutive points within ~150 m of each other into one stop, oldest first. */
    fun stops(trail: List<TrailPoint>): List<Stop> {
        val stops = mutableListOf<Stop>()
        for (point in trail.sortedBy { it.time }) {
            val current = stops.lastOrNull()
            if (current != null && Geo.distanceMeters(current.lat, current.lng, point.lat, point.lng) <= STOP_RADIUS_METERS) {
                stops[stops.lastIndex] = current.copy(to = point.time, address = current.address ?: point.address)
            } else {
                stops += Stop(point.time, point.time, point.lat, point.lng, point.address)
            }
        }
        return stops
    }

    fun clock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Google Maps link that replays the day as a route (no API key needed), or null for fewer than 2 stops. */
    fun routeLink(stops: List<Stop>): String? {
        if (stops.size < 2) return null
        val points = stops.map { "${Geo.coord(it.lat)},${Geo.coord(it.lng)}" }
        val waypoints = points.drop(1).dropLast(1).takeLast(8)
        return buildString {
            append("https://www.google.com/maps/dir/?api=1&origin=").append(points.first())
            append("&destination=").append(points.last())
            if (waypoints.isNotEmpty()) append("&waypoints=").append(waypoints.joinToString("%7C"))
        }
    }
}
