package com.parentstracker.app.util

import com.parentstracker.app.data.EventKind
import com.parentstracker.app.data.LogEvent
import com.parentstracker.app.data.MoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The last seven days at a glance, built from the on-phone activity history. */
data class WeeklySummary(
    val checkIns: Int,
    val medicinesTaken: Int,
    val medicinesMissed: Int,
    val sos: Int,
    val familyAlerts: Int,
    val journeys: Int,
    /** Seven (day label, mood emoji or null) pairs, oldest first. */
    val moods: List<Pair<String, String?>>,
) {
    /** Share of medicine doses taken, or null when no doses were recorded. */
    val adherencePercent: Int?
        get() {
            val total = medicinesTaken + medicinesMissed
            return if (total == 0) null else medicinesTaken * 100 / total
        }

    companion object {
        const val DAYS = 7

        fun compute(events: List<LogEvent>, moods: List<MoodEntry>, now: Long, zone: ZoneId = ZoneId.systemDefault()): WeeklySummary {
            val today = Days.epochDay(now, zone)
            val from = today - (DAYS - 1)
            val week = events.filter { Days.epochDay(it.time, zone) >= from }
            fun count(kind: EventKind) = week.count { it.kind == kind }
            val moodByDay = moods.associate { it.day to it.mood }
            return WeeklySummary(
                checkIns = count(EventKind.CHECK_IN) + count(EventKind.MOOD),
                medicinesTaken = count(EventKind.MEDICINE_TAKEN),
                medicinesMissed = count(EventKind.MEDICINE_MISSED),
                sos = week.count { it.kind == EventKind.SOS && it.title.startsWith("SOS sent") },
                familyAlerts = count(EventKind.FAMILY_ALERT),
                journeys = week.count { it.kind == EventKind.JOURNEY && it.title.startsWith("Started") },
                moods = (from..today).map { day ->
                    LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to moodByDay[day]
                },
            )
        }
    }
}

object Days {
    fun epochDay(time: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay()

    fun minuteOfDay(time: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val t = Instant.ofEpochMilli(time).atZone(zone)
        return t.hour * 60 + t.minute
    }

    /** "Today", "Yesterday" or e.g. "Mon, 6 Oct". */
    fun label(day: Long, today: Long): String = when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(day).let {
            "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, ${it.dayOfMonth} " +
                it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
}
