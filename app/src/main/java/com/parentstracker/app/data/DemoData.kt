package com.parentstracker.app.data

/** A pretend family placed around the user, so the app can be previewed before family sync is set up. */
object DemoData {
    const val DEFAULT_LAT = 28.6139
    const val DEFAULT_LNG = 77.2090

    fun members(center: LocationSnapshot?, places: List<SavedPlace>, now: Long = System.currentTimeMillis()): List<Member> {
        val baseLat = center?.lat ?: DEFAULT_LAT
        val baseLng = center?.lng ?: DEFAULT_LNG
        val home = places.firstOrNull()
        return listOf(
            Member(
                uid = "demo-mom",
                name = "Mom",
                relation = "Mom",
                location = LocationSnapshot(
                    lat = home?.lat ?: (baseLat + 0.0042),
                    lng = home?.lng ?: (baseLng + 0.0031),
                    accuracy = 12f,
                    timeMillis = now - 2 * 60_000,
                    address = home?.let { "${it.name} (demo)" } ?: "Near your area (demo)",
                    battery = 76,
                ),
                checkInMessage = CHECK_IN_MESSAGES.first(),
                checkInAt = now - 45 * 60_000,
                isDemo = true,
            ),
            Member(
                uid = "demo-dad",
                name = "Dad",
                relation = "Dad",
                location = LocationSnapshot(
                    lat = baseLat + 0.0213,
                    lng = baseLng - 0.0178,
                    accuracy = 25f,
                    timeMillis = now - 17 * 60_000,
                    speed = 1.2f,
                    address = "Local market (demo)",
                    battery = 14,
                ),
                isDemo = true,
            ),
        )
    }
}
