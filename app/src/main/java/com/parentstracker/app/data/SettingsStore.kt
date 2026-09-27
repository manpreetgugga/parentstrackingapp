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
) {
    companion object {
        const val DEFAULT_EMERGENCY_NUMBER = "112"
        val INTERVAL_CHOICES = listOf(1, 5, 15)
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
}
