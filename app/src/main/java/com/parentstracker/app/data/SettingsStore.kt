package com.parentstracker.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "parents_tracker_settings")

data class UserSettings(
    val userName: String = "",
    val onboardingDone: Boolean = false,
    val sharingEnabled: Boolean = true,
    val updateIntervalMinutes: Int = 5,
    val places: List<SavedPlace> = emptyList(),
    val emergencyNumbers: List<String> = emptyList(),
    val emergencyDialNumber: String = DEFAULT_EMERGENCY_NUMBER,
    val demoMode: Boolean = false,
    val lastLocation: LocationSnapshot? = null,
    val labels: Map<String, MemberLabel> = emptyMap(),
    val arrivalAlerts: Boolean = true,
    val lowBatteryAlerts: Boolean = true,
    val sosActive: Boolean = false,
    val sosAt: Long = 0,
    val checkInMessage: String? = null,
    val checkInAt: Long = 0,
    val lastViewerCount: Int = 0,
    val trail: List<TrailPoint> = emptyList(),
    val shareTrail: Boolean = true,
    val medical: MedicalInfo = MedicalInfo(),
    val reminders: List<Reminder> = emptyList(),
    val seniorMode: Boolean = false,
    val batterySaver: Boolean = true,
    val offlineAlertHours: Int = 3,
    val events: List<LogEvent> = emptyList(),
    val journey: Journey? = null,
    val moods: List<MoodEntry> = emptyList(),
    val curfews: List<CurfewRule> = emptyList(),
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val shakeToSos: Boolean = false,
    val fallDetection: Boolean = false,
    val autoArrivalCheckIn: Boolean = true,
    val speedAlertKmh: Int = 0,
    val chargeReminder: Boolean = true,
    val simpleMode: Boolean = false,
    val simpleLanguage: SimpleLanguage = SimpleLanguage.ENGLISH,
    val simpleCallName: String = "",
    val simpleCallNumber: String = "",
    val speakAloud: Boolean = true,
) {
    companion object {
        const val DEFAULT_EMERGENCY_NUMBER = "112"
        val INTERVAL_CHOICES = listOf(1, 5, 15)
        val OFFLINE_ALERT_CHOICES = listOf(0, 2, 3, 6)
        val SPEED_ALERT_CHOICES = listOf(0, 80, 100, 120)
        const val MAX_EVENTS = 300
        const val MOOD_DAYS = 30
    }
}

/** Everything the app remembers on this phone, backed by Preferences DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("user_name")
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val SHARING = booleanPreferencesKey("sharing_enabled")
        val INTERVAL = intPreferencesKey("update_interval_min")
        val PLACES = stringPreferencesKey("places")
        val SOS_NUMBERS = stringPreferencesKey("emergency_numbers")
        val SOS_DIAL = stringPreferencesKey("emergency_dial")
        val DEMO = booleanPreferencesKey("demo_mode")
        val LAST_LOCATION = stringPreferencesKey("last_location")
        val LABELS = stringPreferencesKey("labels")
        val ARRIVAL_ALERTS = booleanPreferencesKey("arrival_alerts")
        val BATTERY_ALERTS = booleanPreferencesKey("battery_alerts")
        val SOS_ACTIVE = booleanPreferencesKey("sos_active")
        val SOS_AT = longPreferencesKey("sos_at")
        val CHECK_IN_MSG = stringPreferencesKey("check_in_msg")
        val CHECK_IN_AT = longPreferencesKey("check_in_at")
        val VIEWER_COUNT = intPreferencesKey("viewer_count")
        val TRAIL = stringPreferencesKey("trail")
        val SHARE_TRAIL = booleanPreferencesKey("share_trail")
        val MEDICAL = stringPreferencesKey("medical")
        val REMINDERS = stringPreferencesKey("reminders")
        val SENIOR_MODE = booleanPreferencesKey("senior_mode")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val OFFLINE_ALERT_HOURS = intPreferencesKey("offline_alert_hours")
        val EVENTS = stringPreferencesKey("events")
        val JOURNEY = stringPreferencesKey("journey")
        val MOODS = stringPreferencesKey("moods")
        val CURFEWS = stringPreferencesKey("curfews")
        val QUIET_ON = booleanPreferencesKey("quiet_on")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val SHAKE_SOS = booleanPreferencesKey("shake_sos")
        val FALL_DETECTION = booleanPreferencesKey("fall_detection")
        val AUTO_ARRIVAL = booleanPreferencesKey("auto_arrival_check_in")
        val SPEED_ALERT = intPreferencesKey("speed_alert_kmh")
        val CHARGE_REMINDER = booleanPreferencesKey("charge_reminder")
        val SIMPLE_MODE = booleanPreferencesKey("simple_mode")
        val SIMPLE_LANGUAGE = stringPreferencesKey("simple_language")
        val SIMPLE_CALL_NAME = stringPreferencesKey("simple_call_name")
        val SIMPLE_CALL_NUMBER = stringPreferencesKey("simple_call_number")
        val SPEAK_ALOUD = booleanPreferencesKey("speak_aloud")
    }

    val flow: Flow<UserSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): UserSettings = flow.first()

    private fun Preferences.toSettings() = UserSettings(
        userName = this[Keys.NAME].orEmpty(),
        onboardingDone = this[Keys.ONBOARDED] ?: false,
        sharingEnabled = this[Keys.SHARING] ?: true,
        updateIntervalMinutes = this[Keys.INTERVAL] ?: 5,
        places = JsonCodec.decodePlaces(this[Keys.PLACES]),
        emergencyNumbers = JsonCodec.decodeStrings(this[Keys.SOS_NUMBERS]),
        emergencyDialNumber = this[Keys.SOS_DIAL] ?: UserSettings.DEFAULT_EMERGENCY_NUMBER,
        demoMode = this[Keys.DEMO] ?: false,
        lastLocation = JsonCodec.decodeLocation(this[Keys.LAST_LOCATION]),
        labels = JsonCodec.decodeLabels(this[Keys.LABELS]),
        arrivalAlerts = this[Keys.ARRIVAL_ALERTS] ?: true,
        lowBatteryAlerts = this[Keys.BATTERY_ALERTS] ?: true,
        sosActive = this[Keys.SOS_ACTIVE] ?: false,
        sosAt = this[Keys.SOS_AT] ?: 0,
        checkInMessage = this[Keys.CHECK_IN_MSG],
        checkInAt = this[Keys.CHECK_IN_AT] ?: 0,
        lastViewerCount = this[Keys.VIEWER_COUNT] ?: 0,
        trail = JsonCodec.decodeTrail(this[Keys.TRAIL]),
        shareTrail = this[Keys.SHARE_TRAIL] ?: true,
        medical = JsonCodec.decodeMedical(this[Keys.MEDICAL]),
        reminders = JsonCodec.decodeReminders(this[Keys.REMINDERS]),
        seniorMode = this[Keys.SENIOR_MODE] ?: false,
        batterySaver = this[Keys.BATTERY_SAVER] ?: true,
        offlineAlertHours = this[Keys.OFFLINE_ALERT_HOURS] ?: 3,
        events = JsonCodec.decodeEvents(this[Keys.EVENTS]),
        journey = JsonCodec.decodeJourney(this[Keys.JOURNEY]),
        moods = JsonCodec.decodeMoods(this[Keys.MOODS]),
        curfews = JsonCodec.decodeCurfews(this[Keys.CURFEWS]),
        quietHoursEnabled = this[Keys.QUIET_ON] ?: false,
        quietStartMinute = this[Keys.QUIET_START] ?: (22 * 60),
        quietEndMinute = this[Keys.QUIET_END] ?: (7 * 60),
        shakeToSos = this[Keys.SHAKE_SOS] ?: false,
        fallDetection = this[Keys.FALL_DETECTION] ?: false,
        autoArrivalCheckIn = this[Keys.AUTO_ARRIVAL] ?: true,
        speedAlertKmh = this[Keys.SPEED_ALERT] ?: 0,
        chargeReminder = this[Keys.CHARGE_REMINDER] ?: true,
        simpleMode = this[Keys.SIMPLE_MODE] ?: false,
        simpleLanguage = this[Keys.SIMPLE_LANGUAGE]
            ?.let { runCatching { SimpleLanguage.valueOf(it) }.getOrNull() } ?: SimpleLanguage.ENGLISH,
        simpleCallName = this[Keys.SIMPLE_CALL_NAME].orEmpty(),
        simpleCallNumber = this[Keys.SIMPLE_CALL_NUMBER].orEmpty(),
        speakAloud = this[Keys.SPEAK_ALOUD] ?: true,
    )

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun completeOnboarding(name: String) = edit {
        it[Keys.NAME] = name
        it[Keys.ONBOARDED] = true
    }

    suspend fun setName(name: String) = edit { it[Keys.NAME] = name }

    suspend fun setSharingEnabled(enabled: Boolean) = edit { it[Keys.SHARING] = enabled }

    suspend fun setInterval(minutes: Int) = edit { it[Keys.INTERVAL] = minutes }

    suspend fun setLastLocation(location: LocationSnapshot) = edit {
        it[Keys.LAST_LOCATION] = JsonCodec.encodeLocation(location)
    }

    suspend fun addPlace(name: String, lat: Double, lng: Double, radiusMeters: Int) = edit {
        val places = JsonCodec.decodePlaces(it[Keys.PLACES])
        val place = SavedPlace(UUID.randomUUID().toString(), name, lat, lng, radiusMeters)
        it[Keys.PLACES] = JsonCodec.encodePlaces(places + place)
    }

    suspend fun removePlace(id: String) = edit {
        val places = JsonCodec.decodePlaces(it[Keys.PLACES])
        it[Keys.PLACES] = JsonCodec.encodePlaces(places.filterNot { p -> p.id == id })
    }

    suspend fun addEmergencyNumber(number: String) = edit {
        val numbers = JsonCodec.decodeStrings(it[Keys.SOS_NUMBERS])
        if (number !in numbers) it[Keys.SOS_NUMBERS] = JsonCodec.encodeStrings(numbers + number)
    }

    suspend fun removeEmergencyNumber(number: String) = edit {
        val numbers = JsonCodec.decodeStrings(it[Keys.SOS_NUMBERS])
        it[Keys.SOS_NUMBERS] = JsonCodec.encodeStrings(numbers - number)
    }

    suspend fun setEmergencyDialNumber(number: String) = edit { it[Keys.SOS_DIAL] = number }

    suspend fun setDemoMode(enabled: Boolean) = edit { it[Keys.DEMO] = enabled }

    suspend fun putLabel(uid: String, label: MemberLabel) = edit {
        val labels = JsonCodec.decodeLabels(it[Keys.LABELS]).toMutableMap()
        val old = labels[uid]
        labels[uid] = MemberLabel(
            nickname = label.nickname ?: old?.nickname,
            relation = label.relation ?: old?.relation,
            phone = label.phone ?: old?.phone,
        )
        it[Keys.LABELS] = JsonCodec.encodeLabels(labels)
    }

    /** Replaces a label outright (used by the edit dialog, where clearing a field is allowed). */
    suspend fun replaceLabel(uid: String, label: MemberLabel) = edit {
        val labels = JsonCodec.decodeLabels(it[Keys.LABELS]).toMutableMap()
        labels[uid] = label
        it[Keys.LABELS] = JsonCodec.encodeLabels(labels)
    }

    suspend fun setArrivalAlerts(enabled: Boolean) = edit { it[Keys.ARRIVAL_ALERTS] = enabled }

    suspend fun setLowBatteryAlerts(enabled: Boolean) = edit { it[Keys.BATTERY_ALERTS] = enabled }

    suspend fun setSos(active: Boolean, at: Long) = edit {
        it[Keys.SOS_ACTIVE] = active
        it[Keys.SOS_AT] = at
    }

    suspend fun setCheckIn(message: String, at: Long) = edit {
        it[Keys.CHECK_IN_MSG] = message
        it[Keys.CHECK_IN_AT] = at
    }

    suspend fun setViewerCount(count: Int) = edit { it[Keys.VIEWER_COUNT] = count }

    suspend fun setTrail(trail: List<TrailPoint>) = edit { it[Keys.TRAIL] = JsonCodec.encodeTrail(trail) }

    suspend fun setShareTrail(enabled: Boolean) = edit { it[Keys.SHARE_TRAIL] = enabled }

    suspend fun setMedical(info: MedicalInfo) = edit { it[Keys.MEDICAL] = JsonCodec.encodeMedical(info) }

    suspend fun addReminder(label: String, hour: Int, minute: Int, type: ReminderType, stock: Int? = null) = edit {
        val reminders = JsonCodec.decodeReminders(it[Keys.REMINDERS])
        val reminder = Reminder(UUID.randomUUID().toString(), label, hour, minute, type, stock = stock)
        it[Keys.REMINDERS] = JsonCodec.encodeReminders((reminders + reminder).sortedBy { r -> r.hour * 60 + r.minute })
    }

    suspend fun removeReminder(id: String) = edit {
        val reminders = JsonCodec.decodeReminders(it[Keys.REMINDERS])
        it[Keys.REMINDERS] = JsonCodec.encodeReminders(reminders.filterNot { r -> r.id == id })
    }

    suspend fun setReminderEnabled(id: String, enabled: Boolean) = edit {
        val reminders = JsonCodec.decodeReminders(it[Keys.REMINDERS])
        it[Keys.REMINDERS] = JsonCodec.encodeReminders(reminders.map { r -> if (r.id == id) r.copy(enabled = enabled) else r })
    }

    suspend fun setSeniorMode(enabled: Boolean) = edit { it[Keys.SENIOR_MODE] = enabled }

    suspend fun setBatterySaver(enabled: Boolean) = edit { it[Keys.BATTERY_SAVER] = enabled }

    suspend fun setOfflineAlertHours(hours: Int) = edit { it[Keys.OFFLINE_ALERT_HOURS] = hours }

    suspend fun setReminderStock(id: String, stock: Int?) = edit {
        val reminders = JsonCodec.decodeReminders(it[Keys.REMINDERS])
        it[Keys.REMINDERS] = JsonCodec.encodeReminders(reminders.map { r -> if (r.id == id) r.copy(stock = stock) else r })
    }

    /** Counts one tablet off a tracked medicine; returns the new stock (null when it isn't tracked). */
    suspend fun takeOne(id: String): Int? {
        var left: Int? = null
        edit {
            val reminders = JsonCodec.decodeReminders(it[Keys.REMINDERS])
            it[Keys.REMINDERS] = JsonCodec.encodeReminders(
                reminders.map { r ->
                    val stock = r.stock
                    if (r.id == id && stock != null) r.copy(stock = (stock - 1).coerceAtLeast(0)).also { u -> left = u.stock } else r
                },
            )
        }
        return left
    }

    suspend fun appendEvent(event: LogEvent) = edit {
        val events = JsonCodec.decodeEvents(it[Keys.EVENTS])
        it[Keys.EVENTS] = JsonCodec.encodeEvents((events + event).takeLast(UserSettings.MAX_EVENTS))
    }

    suspend fun clearEvents() = edit { it.remove(Keys.EVENTS) }

    suspend fun setJourney(journey: Journey?) = edit {
        if (journey == null) it.remove(Keys.JOURNEY) else it[Keys.JOURNEY] = JsonCodec.encodeJourney(journey)
    }

    /** One mood per day; a second pick the same day replaces the first. */
    suspend fun setMood(day: Long, mood: String) = edit {
        val moods = JsonCodec.decodeMoods(it[Keys.MOODS]).filterNot { m -> m.day == day || m.day < day - UserSettings.MOOD_DAYS }
        it[Keys.MOODS] = JsonCodec.encodeMoods((moods + MoodEntry(day, mood)).sortedBy { m -> m.day })
    }

    suspend fun addCurfew(placeId: String, uid: String?, hour: Int, minute: Int) = edit {
        val rules = JsonCodec.decodeCurfews(it[Keys.CURFEWS])
        it[Keys.CURFEWS] = JsonCodec.encodeCurfews(rules + CurfewRule(UUID.randomUUID().toString(), placeId, uid, hour, minute))
    }

    suspend fun removeCurfew(id: String) = edit {
        val rules = JsonCodec.decodeCurfews(it[Keys.CURFEWS])
        it[Keys.CURFEWS] = JsonCodec.encodeCurfews(rules.filterNot { r -> r.id == id })
    }

    suspend fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) = edit {
        it[Keys.QUIET_ON] = enabled
        it[Keys.QUIET_START] = startMinute
        it[Keys.QUIET_END] = endMinute
    }

    suspend fun setShakeToSos(enabled: Boolean) = edit { it[Keys.SHAKE_SOS] = enabled }
    suspend fun setFallDetection(enabled: Boolean) = edit { it[Keys.FALL_DETECTION] = enabled }
    suspend fun setAutoArrivalCheckIn(enabled: Boolean) = edit { it[Keys.AUTO_ARRIVAL] = enabled }
    suspend fun setSpeedAlertKmh(kmh: Int) = edit { it[Keys.SPEED_ALERT] = kmh }
    suspend fun setChargeReminder(enabled: Boolean) = edit { it[Keys.CHARGE_REMINDER] = enabled }
    suspend fun setSimpleMode(enabled: Boolean) = edit { it[Keys.SIMPLE_MODE] = enabled }
    suspend fun setSimpleLanguage(language: SimpleLanguage) = edit { it[Keys.SIMPLE_LANGUAGE] = language.name }
    suspend fun setSpeakAloud(enabled: Boolean) = edit { it[Keys.SPEAK_ALOUD] = enabled }

    suspend fun setSimpleCallContact(name: String, number: String) = edit {
        it[Keys.SIMPLE_CALL_NAME] = name
        it[Keys.SIMPLE_CALL_NUMBER] = number
    }
}
