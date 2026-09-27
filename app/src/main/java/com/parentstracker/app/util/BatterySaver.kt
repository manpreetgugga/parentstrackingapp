package com.parentstracker.app.util

/** Slows location updates when the battery is low so a parent's phone lasts longer. */
object BatterySaver {
    const val LOW_PERCENT = 20
    const val CRITICAL_PERCENT = 10
    const val SAVER_INTERVAL_MINUTES = 15

    fun effectiveInterval(baseMinutes: Int, battery: Int?, charging: Boolean, enabled: Boolean): Int =
        if (enabled && isLow(battery, charging)) maxOf(baseMinutes, SAVER_INTERVAL_MINUTES) else baseMinutes

    fun isLow(battery: Int?, charging: Boolean) = battery != null && !charging && battery <= LOW_PERCENT

    fun isCritical(battery: Int?, charging: Boolean) = battery != null && !charging && battery <= CRITICAL_PERCENT
}
