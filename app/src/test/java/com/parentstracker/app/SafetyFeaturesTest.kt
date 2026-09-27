package com.parentstracker.app

import com.parentstracker.app.data.LocationSnapshot
import com.parentstracker.app.data.MedicalInfo
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.TrailPoint
import com.parentstracker.app.ui.components.memberStatus
import com.parentstracker.app.util.BatterySaver
import com.parentstracker.app.util.Motion
import com.parentstracker.app.util.MotionDetector
import com.parentstracker.app.util.ReminderTimes
import com.parentstracker.app.util.Trail
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFeaturesTest {
    private val minute = 60_000L
    private val zone = ZoneId.of("Asia/Kolkata")

    // ---- Activity status ----

    @Test
    fun classifiesMotionFromSpeed() {
        assertEquals(Motion.STILL, MotionDetector.from(0.2f))
        assertEquals(Motion.WALKING, MotionDetector.from(1.3f))
        assertEquals(Motion.MOVING, MotionDetector.from(4f))
        assertEquals(Motion.DRIVING, MotionDetector.from(11.5f))
        assertNull(MotionDetector.describe(0f))
        assertEquals("Walking", MotionDetector.describe(1.3f))
        assertEquals("Driving · 41 km/h", MotionDetector.describe(11.5f))
    }

    @Test
    fun statusShowsDrivingOnlyForFreshFixes() {
        val now = 10_000_000_000L
        fun member(minutesAgo: Long) = Member(
            uid = "u",
            name = "Dad",
            location = LocationSnapshot(30.0, 75.0, timeMillis = now - minutesAgo * minute, speed = 11.5f),
        )
        assertEquals("Driving · 41 km/h · 4 min ago", memberStatus(member(4), emptyList(), now).first)
        assertEquals("Updated 40 min ago", memberStatus(member(40), emptyList(), now).first)
    }

    // ---- Trail ----

    @Test
    fun trailSkipsNearbyPointsUntilTenMinutesPass() {
        val start = TrailPoint(30.9, 75.85, 0)
        val one = Trail.append(emptyList(), start)
        val sameSpotSoon = Trail.append(one, TrailPoint(30.9001, 75.85, 3 * minute))
        assertEquals(1, sameSpotSoon.size)
        val sameSpotLater = Trail.append(one, TrailPoint(30.9001, 75.85, 11 * minute))
        assertEquals(2, sameSpotLater.size)
        val movedFar = Trail.append(one, TrailPoint(30.91, 75.85, 2 * minute))
        assertEquals(2, movedFar.size)
    }

    @Test
    fun trailDropsPointsOlderThanADay() {
        val old = TrailPoint(30.9, 75.85, 0)
        val result = Trail.append(listOf(old), TrailPoint(30.95, 75.85, Trail.KEEP_MS + minute))
        assertEquals(1, result.size)
        assertEquals(Trail.KEEP_MS + minute, result.single().time)
    }

    @Test
    fun trailGroupsPointsIntoStops() {
        val trail = listOf(
            TrailPoint(30.9000, 75.85, 0, "Home"),
            TrailPoint(30.9002, 75.85, 20 * minute, "Home"),
            TrailPoint(30.9200, 75.85, 60 * minute, "Temple"),
            TrailPoint(30.9201, 75.85, 90 * minute, null),
            TrailPoint(30.9000, 75.85, 150 * minute, "Home"),
        )
        val stops = Trail.stops(trail)
        assertEquals(listOf("Home", "Temple", "Home"), stops.map { it.address })
        assertEquals(60 * minute, stops[1].from)
        assertEquals(90 * minute, stops[1].to)
        val link = Trail.routeLink(stops)!!
        assertTrue(link.startsWith("https://www.google.com/maps/dir/?api=1&origin=30.900000,75.850000"))
        assertTrue(link.contains("&waypoints=30.920000,75.850000"))
        assertNull(Trail.routeLink(stops.take(1)))
    }

    // ---- Reminders ----

    @Test
    fun nextReminderIsLaterTodayOrTomorrow() {
        val eightAm = LocalDateTime.of(2026, 9, 28, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val nineAm = LocalDateTime.of(2026, 9, 28, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val sevenAmTomorrow = LocalDateTime.of(2026, 9, 29, 7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(nineAm, ReminderTimes.nextTrigger(9, 0, eightAm, zone))
        assertEquals(sevenAmTomorrow, ReminderTimes.nextTrigger(7, 0, eightAm, zone))
        // Exactly at the reminder time -> next day, so an alarm never re-fires itself.
        assertEquals(nineAm + 24 * 60 * minute, ReminderTimes.nextTrigger(9, 0, nineAm, zone))
    }

    // ---- Battery saver ----

    @Test
    fun batterySaverSlowsUpdatesOnlyWhenLowAndNotCharging() {
        assertEquals(5, BatterySaver.effectiveInterval(5, battery = 80, charging = false, enabled = true))
        assertEquals(15, BatterySaver.effectiveInterval(5, battery = 18, charging = false, enabled = true))
        assertEquals(5, BatterySaver.effectiveInterval(5, battery = 18, charging = true, enabled = true))
        assertEquals(5, BatterySaver.effectiveInterval(5, battery = 18, charging = false, enabled = false))
        assertEquals(15, BatterySaver.effectiveInterval(15, battery = 5, charging = false, enabled = true))
        assertTrue(BatterySaver.isCritical(9, charging = false))
        assertFalse(BatterySaver.isCritical(9, charging = true))
        assertFalse(BatterySaver.isCritical(null, charging = false))
    }

    // ---- Medical ID ----

    @Test
    fun medicalSummaryListsOnlyFilledFields() {
        assertTrue(MedicalInfo().isEmpty)
        val info = MedicalInfo(bloodGroup = "O+", allergies = "Penicillin", doctorPhone = "+91 98765 43210")
        assertFalse(info.isEmpty)
        assertEquals("Blood group O+ · Allergies: Penicillin · Doctor: +91 98765 43210", info.summary())
    }
}
