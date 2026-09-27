package com.parentstracker.app.data

/** A pretend family placed around the user, so the app can be previewed before family sync is set up. */
object DemoData {
    const val DEFAULT_LAT = 28.6139
    const val DEFAULT_LNG = 77.2090

    fun members(center: LocationSnapshot?, places: List<SavedPlace>, now: Long = System.currentTimeMillis()): List<Member> {
        val baseLat = center?.lat ?: DEFAULT_LAT
        val baseLng = center?.lng ?: DEFAULT_LNG
        val home = places.firstOrNull()
        val momLat = home?.lat ?: (baseLat + 0.0042)
        val momLng = home?.lng ?: (baseLng + 0.0031)
        val momAddress = home?.let { "${it.name} (demo)" } ?: "Near your area (demo)"
        val dadLat = baseLat + 0.0213
        val dadLng = baseLng - 0.0178
        val minute = 60_000L
        return listOf(
            Member(
                uid = "demo-mom",
                name = "Mom",
                relation = "Mom",
                location = LocationSnapshot(
                    lat = momLat,
                    lng = momLng,
                    accuracy = 12f,
                    timeMillis = now - 2 * minute,
                    address = momAddress,
                    battery = 76,
                ),
                checkInMessage = CHECK_IN_MESSAGES.first(),
                checkInAt = now - 45 * minute,
                trail = listOf(
                    TrailPoint(momLat, momLng, now - 9 * 60 * minute, momAddress),
                    TrailPoint(momLat + 0.006, momLng + 0.004, now - 6 * 60 * minute, "Temple (demo)"),
                    TrailPoint(momLat + 0.0061, momLng + 0.0041, now - 5 * 60 * minute, "Temple (demo)"),
                    TrailPoint(momLat, momLng, now - 4 * 60 * minute, momAddress),
                    TrailPoint(momLat, momLng, now - 2 * minute, momAddress),
                ),
                medical = MedicalInfo(
                    bloodGroup = "B+",
                    conditions = "Diabetes (type 2)",
                    medicines = "Metformin 500 mg twice a day",
                    doctorName = "Dr. Sharma",
                    doctorPhone = "+91 98765 43210",
                ),
                isDemo = true,
            ),
            Member(
                uid = "demo-dad",
                name = "Dad",
                relation = "Dad",
                location = LocationSnapshot(
                    lat = dadLat,
                    lng = dadLng,
                    accuracy = 25f,
                    timeMillis = now - 4 * minute,
                    speed = 11.5f,
                    address = "Main road (demo)",
                    battery = 14,
                ),
                trail = listOf(
                    TrailPoint(baseLat + 0.0040, baseLng + 0.0030, now - 3 * 60 * minute, "Home (demo)"),
                    TrailPoint(baseLat + 0.0150, baseLng - 0.0100, now - 2 * 60 * minute, "Local market (demo)"),
                    TrailPoint(baseLat + 0.0152, baseLng - 0.0101, now - 70 * minute, "Local market (demo)"),
                    TrailPoint(dadLat, dadLng, now - 4 * minute, "Main road (demo)"),
                ),
                isDemo = true,
            ),
        )
    }
}
