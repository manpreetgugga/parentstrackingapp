package com.parentstracker.app.util

import kotlin.math.roundToInt

/** Rough activity guess from GPS speed, shown as "Walking" or "Driving · 42 km/h". */
enum class Motion { STILL, WALKING, MOVING, DRIVING }

object MotionDetector {
    /** Only trust speed from a recent fix; an old fix says nothing about what someone is doing now. */
    const val FRESH_MS = 15 * 60_000L

    fun from(speedMetersPerSecond: Float): Motion = when {
        speedMetersPerSecond < 0.8f -> Motion.STILL
        speedMetersPerSecond < 2.5f -> Motion.WALKING
        speedMetersPerSecond < 6.5f -> Motion.MOVING
        else -> Motion.DRIVING
    }

    /** Label for a moving person, or null when they're still. */
    fun describe(speedMetersPerSecond: Float): String? {
        val kmh = (speedMetersPerSecond * 3.6f).roundToInt()
        return when (from(speedMetersPerSecond)) {
            Motion.STILL -> null
            Motion.WALKING -> "Walking"
            Motion.MOVING -> "On the move · $kmh km/h"
            Motion.DRIVING -> "Driving · $kmh km/h"
        }
    }
}
