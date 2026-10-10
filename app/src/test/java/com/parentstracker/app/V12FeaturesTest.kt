package com.parentstracker.app

import com.parentstracker.app.data.CurfewRule
import com.parentstracker.app.data.EventKind
import com.parentstracker.app.data.LocationSnapshot
import com.parentstracker.app.data.LogEvent
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.MoodEntry
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.data.SimpleLanguage
import com.parentstracker.app.util.Curfews
import com.parentstracker.app.util.Days
import com.parentstracker.app.util.FallDetector
import com.parentstracker.app.util.QuietHours
import com.parentstracker.app.util.ShakeDetector
import com.parentstracker.app.util.SimpleText
import com.parentstracker.app.util.WeeklySummary
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V12FeaturesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val minute = 60_000L

    private fun at(hour: Int, min: Int, day: Int = 10): Long =
        LocalDateTime.of(2026, 10, day, hour, min).atZone(zone).toInstant().toEpochMilli()

    // ---- Quiet hours ----

    @Test
    fun quietHoursWrapPastMidnight() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue(QuietHours.isQuiet(23 * 60, start, end))
        assertTrue(QuietHours.isQuiet(3 * 60, start, end))
        assertFalse(QuietHours.isQuiet(7 * 60, start, end))
        assertFalse(QuietHours.isQuiet(12 * 60, start, end))
    }

    @Test
    fun quietHoursWithinOneDayAndDisabled() {
        assertTrue(QuietHours.isQuiet(14 * 60, 13 * 60, 15 * 60))
        assertFalse(QuietHours.isQuiet(16 * 60, 13 * 60, 15 * 60))
        assertFalse(QuietHours.isQuiet(10, 0, 0))
        assertFalse(QuietHours.isQuietAt(at(23, 0), enabled = false, start = 22 * 60, end = 7 * 60, zone = zone))
        assertTrue(QuietHours.isQuietAt(at(23, 0), enabled = true, start = 22 * 60, end = 7 * 60, zone = zone))
    }

    // ---- Shake and fall ----

    @Test
    fun threeHardShakesTriggerSos() {
        val shake = ShakeDetector()
        assertFalse(shake.onSample(3f, 0))
        assertFalse(shake.onSample(1f, 100))
        assertFalse(shake.onSample(3f, 400))
        assertTrue(shake.onSample(3f, 800))
    }

    @Test
    fun slowOrGentleShakesDoNot() {
        val shake = ShakeDetector()
        assertFalse(shake.onSample(1.8f, 0))
        assertFalse(shake.onSample(1.8f, 300))
        assertFalse(shake.onSample(1.8f, 600))
        // Hard but spread over several seconds, like walking with the phone.
        assertFalse(shake.onSample(3f, 0))
        assertFalse(shake.onSample(3f, 2_000))
        assertFalse(shake.onSample(3f, 4_000))
    }

    @Test
    fun fallThenLyingStillIsDetected() {
        val fall = FallDetector()
        var t = 0L
        assertFalse(fall.onSample(1f, t))
        assertFalse(fall.onSample(0.2f, t + 100)) // free fall
        assertFalse(fall.onSample(3.5f, t + 400)) // impact
        t += 400
        var detected = false
        while (t < 6_000) {
            t += 50
            detected = detected || fall.onSample(1.02f, t)
        }
        assertTrue(detected)
    }

    @Test
    fun fallFollowedByMovementIsIgnored() {
        val fall = FallDetector()
        fall.onSample(0.2f, 0)
        fall.onSample(3.5f, 300)
        var detected = false
        var t = 300L
        while (t < 20_000) {
            t += 50
            // Picked the phone up and kept walking: the reading keeps changing.
            detected = detected || fall.onSample(if ((t / 500) % 2 == 0L) 1.6f else 0.6f, t)
        }
        assertFalse(detected)
    }

    @Test
    fun impactWithoutFreeFallIsIgnored() {
        val fall = FallDetector()
        fall.onSample(3.5f, 0) // e.g. phone put down hard on a table
        var detected = false
        var t = 0L
        while (t < 6_000) {
            t += 50
            detected = detected || fall.onSample(1f, t)
        }
        assertFalse(detected)
    }

    // ---- Weekly summary ----

    @Test
    fun weeklySummaryCountsOnlyTheLastSevenDays() {
        val now = at(12, 0, day = 10)
        val events = listOf(
            LogEvent(at(9, 0, 10), EventKind.MEDICINE_TAKEN, "Took BP tablet 💊"),
            LogEvent(at(21, 0, 9), EventKind.MEDICINE_TAKEN, "Took BP tablet 💊"),
            LogEvent(at(21, 0, 8), EventKind.MEDICINE_MISSED, "Missed BP tablet"),
            LogEvent(at(10, 0, 7), EventKind.CHECK_IN, "I'm OK 👍"),
            LogEvent(at(10, 0, 7), EventKind.SOS, "SOS sent"),
            LogEvent(at(10, 5, 7), EventKind.SOS, "SOS turned off"),
            LogEvent(at(11, 0, 6), EventKind.JOURNEY, "Started trip to Market"),
            LogEvent(at(11, 30, 6), EventKind.JOURNEY, "Reached Market"),
            LogEvent(at(10, 0, 1), EventKind.CHECK_IN, "Too old"),
        )
        val moods = listOf(MoodEntry(Days.epochDay(now, zone), "😊"), MoodEntry(Days.epochDay(at(9, 0, 8), zone), "🤒"))
        val summary = WeeklySummary.compute(events, moods, now, zone)
        assertEquals(1, summary.checkIns)
        assertEquals(2, summary.medicinesTaken)
        assertEquals(1, summary.medicinesMissed)
        assertEquals(66, summary.adherencePercent)
        assertEquals(1, summary.sos)
        assertEquals(1, summary.journeys)
        assertEquals(7, summary.moods.size)
        assertEquals("😊", summary.moods.last().second)
        assertEquals("🤒", summary.moods[4].second)
        assertNull(summary.moods.first().second)
    }

    @Test
    fun noMedicinesMeansNoAdherence() {
        assertNull(WeeklySummary.compute(emptyList(), emptyList(), at(12, 0), zone).adherencePercent)
    }

    // ---- "Expected by" alerts ----

    private val home = SavedPlace("home", "Home", 28.6, 77.2, 150)

    private fun member(uid: String, lat: Double, time: Long) = Member(
        uid = uid,
        name = uid,
        location = LocationSnapshot(lat = lat, lng = 77.2, timeMillis = time),
    )

    @Test
    fun curfewFlagsOnlyPeopleAwayAfterTheDeadline() {
        val now = at(20, 30)
        val rule = CurfewRule("r", "home", null, 20, 0)
        val atHome = member("mom", 28.6, now - 5 * minute)
        val away = member("dad", 28.65, now - 5 * minute)
        val late = Curfews.late(rule, home, listOf(atHome, away), Days.minuteOfDay(now, zone), now)
        assertEquals(listOf("dad"), late.map { it.uid })
    }

    @Test
    fun curfewIgnoresBeforeDeadlineLongAfterAndOldFixes() {
        val rule = CurfewRule("r", "home", null, 20, 0)
        val before = at(19, 30)
        val away = member("dad", 28.65, before - minute)
        assertTrue(Curfews.late(rule, home, listOf(away), Days.minuteOfDay(before, zone), before).isEmpty())

        val muchLater = at(23, 30)
        assertTrue(Curfews.late(rule, home, listOf(away.copy(location = away.location!!.copy(timeMillis = muchLater))), Days.minuteOfDay(muchLater, zone), muchLater).isEmpty())

        val now = at(20, 30)
        val stale = member("dad", 28.65, now - 5 * 60 * minute)
        assertTrue(Curfews.late(rule, home, listOf(stale), Days.minuteOfDay(now, zone), now).isEmpty())
    }

    @Test
    fun curfewForOnePersonSkipsOthers() {
        val now = at(20, 30)
        val rule = CurfewRule("r", "home", "mom", 20, 0)
        val away = member("dad", 28.65, now - minute)
        assertTrue(Curfews.late(rule, home, listOf(away), Days.minuteOfDay(now, zone), now).isEmpty())
    }

    @Test
    fun curfewAfterMidnightDeadlineWraps() {
        val rule = CurfewRule("r", "home", null, 23, 30)
        val now = at(0, 15, day = 11)
        val away = member("dad", 28.65, now - minute)
        assertEquals(1, Curfews.late(rule, home, listOf(away), Days.minuteOfDay(now, zone), now).size)
    }

    // ---- Simple mode text ----

    @Test
    fun simpleModeSpeaksEachLanguage() {
        assertEquals("I'm OK", SimpleText(SimpleLanguage.ENGLISH).ok)
        assertEquals("मैं ठीक हूँ", SimpleText(SimpleLanguage.HINDI).ok)
        assertEquals("ਮੈਂ ਠੀਕ ਹਾਂ", SimpleText(SimpleLanguage.PUNJABI).ok)
        assertEquals("Call Manpreet", SimpleText(SimpleLanguage.ENGLISH).call("Manpreet"))
        assertEquals("Good morning", SimpleText(SimpleLanguage.ENGLISH).greeting(8))
    }

    @Test
    fun dayLabels() {
        val today = Days.epochDay(at(12, 0), zone)
        assertEquals("Today", Days.label(today, today))
        assertEquals("Yesterday", Days.label(today - 1, today))
    }
}
