package com.parentstracker.app

import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {

    @Test
    fun distanceBetweenDelhiAndMumbaiIsAbout1150km() {
        val meters = Geo.distanceMeters(28.6139, 77.2090, 19.0760, 72.8777)
        assertEquals(1_150_000.0, meters, 10_000.0)
    }

    @Test
    fun distanceToSamePointIsZero() {
        assertEquals(0.0, Geo.distanceMeters(30.9, 75.85, 30.9, 75.85), 0.001)
    }

    @Test
    fun formatsDistances() {
        assertEquals("350 m", Geo.formatDistance(349.6))
        assertEquals("3.2 km", Geo.formatDistance(3_210.0))
        assertEquals("42 km", Geo.formatDistance(41_700.0))
    }

    @Test
    fun formatsTimeAgo() {
        val now = 10_000_000_000L
        assertEquals("just now", Geo.timeAgo(now - 30_000, now))
        assertEquals("5 min ago", Geo.timeAgo(now - 5 * 60_000, now))
        assertEquals("3 h ago", Geo.timeAgo(now - 3 * 3_600_000, now))
        assertEquals("2 d ago", Geo.timeAgo(now - 2 * 86_400_000L, now))
        assertEquals("never", Geo.timeAgo(0, now))
        assertEquals("just now", Geo.timeAgo(now + 5_000, now)) // phone clock slightly ahead
    }

    @Test
    fun findsClosestContainingPlace() {
        val home = SavedPlace("h", "Home", 30.9000, 75.8500, 150)
        val temple = SavedPlace("t", "Gurudwara", 30.9010, 75.8500, 150)
        // ~22 m from home, ~89 m from the temple: both contain it, home is closer.
        assertEquals("Home", Geo.placeFor(30.9002, 75.8500, listOf(temple, home))?.name)
        // ~1.1 km away from both.
        assertNull(Geo.placeFor(30.9100, 75.8500, listOf(home, temple)))
    }

    @Test
    fun linksUseDotDecimalsRegardlessOfLocale() {
        assertEquals("https://maps.google.com/?q=30.900000,75.850000", Geo.mapsLink(30.9, 75.85))
    }
}
