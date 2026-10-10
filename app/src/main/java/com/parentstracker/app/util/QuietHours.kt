package com.parentstracker.app.util

import java.time.Instant
import java.time.ZoneId

/** Quiet hours: non-urgent alerts arrive silently. SOS, pings and safety checks always ring. */
object QuietHours {
    /** True when [minuteOfDay] falls in [start, end), which may wrap past midnight (e.g. 22:00–07:00). */
    fun isQuiet(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    fun isQuietAt(now: Long, enabled: Boolean, start: Int, end: Int, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!enabled) return false
        val time = Instant.ofEpochMilli(now).atZone(zone)
        return isQuiet(time.hour * 60 + time.minute, start, end)
    }

    fun format(minuteOfDay: Int): String = ReminderTimes.format(minuteOfDay / 60, minuteOfDay % 60)
}
