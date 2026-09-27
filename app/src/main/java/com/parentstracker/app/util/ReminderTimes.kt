package com.parentstracker.app.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderTimes {
    /** The next moment (after [now]) that the clock shows [hour]:[minute]. */
    fun nextTrigger(hour: Int, minute: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var next = current.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(current)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    fun format(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
