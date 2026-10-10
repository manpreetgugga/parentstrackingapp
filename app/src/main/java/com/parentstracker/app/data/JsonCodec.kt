package com.parentstracker.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Small JSON helpers for the structured values kept in DataStore preferences. */
internal object JsonCodec {

    fun encodeLocation(location: LocationSnapshot): String = JSONObject().apply {
        put("lat", location.lat)
        put("lng", location.lng)
        put("acc", location.accuracy.toDouble())
        put("t", location.timeMillis)
        put("spd", location.speed.toDouble())
        location.address?.let { put("addr", it) }
        location.battery?.let { put("bat", it) }
        put("chg", location.charging)
    }.toString()

    fun decodeLocation(json: String?): LocationSnapshot? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            LocationSnapshot(
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                accuracy = o.optDouble("acc", 0.0).toFloat(),
                timeMillis = o.getLong("t"),
                speed = o.optDouble("spd", 0.0).toFloat(),
                address = if (o.has("addr")) o.getString("addr") else null,
                battery = if (o.has("bat")) o.getInt("bat") else null,
                charging = o.optBoolean("chg", false),
            )
        }.getOrNull()
    }

    fun encodePlaces(places: List<SavedPlace>): String = JSONArray().apply {
        places.forEach { p ->
            put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("lat", p.lat)
                put("lng", p.lng)
                put("r", p.radiusMeters)
            })
        }
    }.toString()

    fun decodePlaces(json: String?): List<SavedPlace> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedPlace(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.getInt("r"))
            }
        }.getOrDefault(emptyList())
    }

    fun encodeStrings(values: List<String>): String = JSONArray(values).toString()

    fun decodeStrings(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun encodeTrail(trail: List<TrailPoint>): String = JSONArray().apply {
        trail.forEach { p ->
            put(JSONObject().apply {
                put("lat", p.lat)
                put("lng", p.lng)
                put("t", p.time)
                p.address?.let { put("a", it) }
            })
        }
    }.toString()

    fun decodeTrail(json: String?): List<TrailPoint> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                TrailPoint(o.getDouble("lat"), o.getDouble("lng"), o.getLong("t"), if (o.has("a")) o.getString("a") else null)
            }
        }.getOrDefault(emptyList())
    }

    fun encodeMedical(info: MedicalInfo): String = JSONObject().apply {
        put("blood", info.bloodGroup)
        put("conditions", info.conditions)
        put("allergies", info.allergies)
        put("medicines", info.medicines)
        put("doctor", info.doctorName)
        put("doctorPhone", info.doctorPhone)
    }.toString()

    fun decodeMedical(json: String?): MedicalInfo {
        if (json.isNullOrBlank()) return MedicalInfo()
        return runCatching {
            val o = JSONObject(json)
            MedicalInfo(
                bloodGroup = o.optString("blood"),
                conditions = o.optString("conditions"),
                allergies = o.optString("allergies"),
                medicines = o.optString("medicines"),
                doctorName = o.optString("doctor"),
                doctorPhone = o.optString("doctorPhone"),
            )
        }.getOrDefault(MedicalInfo())
    }

    fun encodeReminders(reminders: List<Reminder>): String = JSONArray().apply {
        reminders.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("label", r.label)
                put("h", r.hour)
                put("m", r.minute)
                put("type", r.type.name)
                put("on", r.enabled)
                r.stock?.let { put("stock", it) }
            })
        }
    }.toString()

    fun decodeReminders(json: String?): List<Reminder> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Reminder(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    hour = o.getInt("h"),
                    minute = o.getInt("m"),
                    type = runCatching { ReminderType.valueOf(o.getString("type")) }.getOrDefault(ReminderType.MEDICINE),
                    enabled = o.optBoolean("on", true),
                    stock = if (o.has("stock")) o.getInt("stock") else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeLabels(labels: Map<String, MemberLabel>): String = JSONObject().apply {
        labels.forEach { (uid, label) ->
            put(uid, JSONObject().apply {
                label.nickname?.let { put("n", it) }
                label.relation?.let { put("r", it) }
                label.phone?.let { put("p", it) }
            })
        }
    }.toString()

    fun decodeLabels(json: String?): Map<String, MemberLabel> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { uid ->
                val l = o.getJSONObject(uid)
                MemberLabel(
                    nickname = if (l.has("n")) l.getString("n") else null,
                    relation = if (l.has("r")) l.getString("r") else null,
                    phone = if (l.has("p")) l.getString("p") else null,
                )
            }
        }.getOrDefault(emptyMap())
    }

    fun encodeEvents(events: List<LogEvent>): String = JSONArray().apply {
        events.forEach { e ->
            put(JSONObject().apply {
                put("t", e.time)
                put("k", e.kind.name)
                put("title", e.title)
                e.detail?.let { put("d", it) }
            })
        }
    }.toString()

    fun decodeEvents(json: String?): List<LogEvent> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val kind = runCatching { EventKind.valueOf(o.getString("k")) }.getOrNull() ?: return@mapNotNull null
                LogEvent(o.getLong("t"), kind, o.getString("title"), if (o.has("d")) o.getString("d") else null)
            }
        }.getOrDefault(emptyList())
    }

    fun encodeJourney(journey: Journey): String = JSONObject().apply {
        put("dest", journey.destination)
        put("start", journey.startedAt)
        put("due", journey.dueAt)
    }.toString()

    fun decodeJourney(json: String?): Journey? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            Journey(o.getString("dest"), o.getLong("start"), o.getLong("due"))
        }.getOrNull()
    }

    fun encodeMoods(moods: List<MoodEntry>): String = JSONArray().apply {
        moods.forEach { m ->
            put(JSONObject().apply {
                put("day", m.day)
                put("mood", m.mood)
            })
        }
    }.toString()

    fun decodeMoods(json: String?): List<MoodEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                MoodEntry(o.getLong("day"), o.getString("mood"))
            }
        }.getOrDefault(emptyList())
    }

    fun encodeCurfews(rules: List<CurfewRule>): String = JSONArray().apply {
        rules.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("place", r.placeId)
                r.uid?.let { put("uid", it) }
                put("h", r.hour)
                put("m", r.minute)
            })
        }
    }.toString()

    fun decodeCurfews(json: String?): List<CurfewRule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                CurfewRule(o.getString("id"), o.getString("place"), if (o.has("uid")) o.getString("uid") else null, o.getInt("h"), o.getInt("m"))
            }
        }.getOrDefault(emptyList())
    }
}
