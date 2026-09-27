package com.parentstracker.app

import com.parentstracker.app.data.DemoData
import com.parentstracker.app.data.LocationSnapshot
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.ui.components.memberStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberStatusTest {
    private val now = 10_000_000_000L
    private val home = SavedPlace("h", "Home", 30.9, 75.85, 150)

    private fun member(minutesAgo: Long, sharing: Boolean = true, lat: Double = 30.9) = Member(
        uid = "u",
        name = "Mom",
        location = LocationSnapshot(lat, 75.85, timeMillis = now - minutesAgo * 60_000),
        sharing = sharing,
    )

    @Test
    fun showsSavedPlace() {
        val (text, warn) = memberStatus(member(2), listOf(home), now)
        assertEquals("At Home · 2 min ago", text)
        assertFalse(warn)
    }

    @Test
    fun warnsWhenLocationIsOld() {
        val (text, warn) = memberStatus(member(3 * 60, lat = 31.5), listOf(home), now)
        assertEquals("Updated 3 h ago · phone may be off or offline", text)
        assertTrue(warn)
    }

    @Test
    fun showsPaused() {
        val (text, warn) = memberStatus(member(10, sharing = false), emptyList(), now)
        assertEquals("Paused sharing · last seen 10 min ago", text)
        assertTrue(warn)
    }

    @Test
    fun waitsForFirstLocation() {
        val (text, _) = memberStatus(Member(uid = "u", name = "Dad", location = null), emptyList(), now)
        assertEquals("Waiting for their first location update…", text)
    }

    @Test
    fun demoFamilyUsesHomeAndIsMarked() {
        val demo = DemoData.members(center = null, places = listOf(home), now = now)
        assertEquals(2, demo.size)
        assertTrue(demo.all { it.isDemo })
        assertEquals("At Home · 2 min ago", memberStatus(demo[0], listOf(home), now).first)
    }
}
