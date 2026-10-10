package com.parentstracker.app.notify

import android.content.Context
import com.parentstracker.app.data.AppEvent
import com.parentstracker.app.data.FamilyRepository
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.data.EventKind
import com.parentstracker.app.util.Curfews
import com.parentstracker.app.util.Days
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.QuietHours
import com.parentstracker.app.util.ReminderTimes
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Watches family members' updates and raises notifications for SOS, arriving at / leaving saved
 * places, low battery, phones that stopped updating, and "Are you OK?" pings.
 * Runs while the app process is alive (open, or sharing in the background).
 */
class AlertMonitor(private val context: Context, private val repository: FamilyRepository) {

    private val lastSosAt = mutableMapOf<String, Long>()
    private val lastPlaceId = mutableMapOf<String, String>()
    private val lowBatteryNotified = mutableSetOf<String>()
    private val offlineNotified = mutableSetOf<String>()
    private val notifiedPings = mutableSetOf<String>()
    private val lastSpeedAlert = mutableMapOf<String, Long>()
    private val curfewNotified = mutableSetOf<String>()
    private var chargeReminded = false

    /** Re-checks every few minutes, because "no updates for 3 hours" happens without any new data. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5 * 60_000L)
        }
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.members, repository.settingsState.filterNotNull(), ticker) { members, settings, now ->
                Triple(members, settings, now)
            }.collect { (members, settings, now) -> check(members, settings, now) }
        }
        scope.launch {
            repository.incomingPings.collect { pings ->
                pings.forEach { ping ->
                    if (notifiedPings.add(ping.id) && System.currentTimeMillis() - ping.createdAt < PING_FRESH_MS) {
                        Notifications.ping(context, ping.id, ping.fromName)
                    }
                }
            }
        }
        scope.launch {
            repository.events.collect { event ->
                when (event) {
                    is AppEvent.InviteAccepted -> Notifications.alert(
                        context,
                        ("invite" + event.name).hashCode(),
                        "${event.name} accepted your invite",
                        "You're now connected on Parents Tracker.",
                    )
                    is AppEvent.PingAnswered -> {
                        repository.log(EventKind.FAMILY_ALERT, "${event.name}: ${event.response.message}", "Reply to your “Are you OK?”")
                        Notifications.alert(
                        context,
                        ("answer" + event.name).hashCode(),
                        "${event.name}: ${event.response.message}",
                        "Reply to your “Are you OK?”",
                    )
                    }
                }
            }
        }
    }

    private suspend fun check(members: List<Member>, settings: UserSettings, now: Long) {
        val quiet = QuietHours.isQuietAt(now, settings.quietHoursEnabled, settings.quietStartMinute, settings.quietEndMinute)

        /** Posts a family alert (silently during quiet hours) and records it in the activity history. */
        suspend fun notify(key: String, title: String, text: String, critical: Boolean = false) {
            Notifications.alert(context, key.hashCode(), title, text, quiet = quiet && !critical)
            repository.log(EventKind.FAMILY_ALERT, title, text)
        }

        checkMyBattery(settings)
        checkCurfews(members, settings, now) { key, title, text -> notify(key, title, text) }

        for (member in members) {
            if (member.isDemo) continue
            val name = member.displayName

            if (member.sos && member.sosAt > (lastSosAt[member.uid] ?: 0L)) {
                lastSosAt[member.uid] = member.sosAt
                if (now - member.sosAt < SOS_FRESH_MS) {
                    val where = member.location?.address?.let { " near $it" }.orEmpty()
                    notify("sos" + member.uid, "🚨 SOS from $name", "$name needs help$where. Open the app to call or get directions.", critical = true)
                }
            }

            val location = member.location ?: continue

            if (settings.arrivalAlerts && settings.places.isNotEmpty()) {
                val previous = lastPlaceId[member.uid]
                val previousPlace = settings.places.firstOrNull { it.id == previous }
                val stillThere = previousPlace != null &&
                    Geo.distanceMeters(location.lat, location.lng, previousPlace.lat, previousPlace.lng) <= previousPlace.radiusMeters + HYSTERESIS_M
                val current = if (stillThere) previousPlace else Geo.placeFor(location.lat, location.lng, settings.places)
                val currentId = current?.id ?: OUTSIDE
                if (previous != null && previous != currentId) {
                    val key = "place" + member.uid
                    when {
                        current != null -> notify(key, "$name arrived at ${current.name}", "Updated ${Geo.timeAgo(location.timeMillis, now)}")
                        previousPlace != null -> notify(key, "$name left ${previousPlace.name}", location.address ?: "Tap to see where")
                    }
                }
                lastPlaceId[member.uid] = currentId
            }

            val battery = location.battery
            if (settings.lowBatteryAlerts && battery != null) {
                if (battery <= LOW_BATTERY && !location.charging) {
                    if (lowBatteryNotified.add(member.uid)) {
                        notify("battery" + member.uid, "$name's phone battery is low ($battery%)", "They may go offline soon. Maybe give them a call.")
                    }
                } else if (battery > LOW_BATTERY + 10 || location.charging) {
                    lowBatteryNotified.remove(member.uid)
                }
            }

            val silentFor = now - location.timeMillis
            if (settings.offlineAlertHours > 0 && member.sharing && silentFor > settings.offlineAlertHours * HOUR_MS) {
                if (offlineNotified.add(member.uid)) {
                    notify(
                        "offline" + member.uid,
                        "No updates from $name for ${silentFor / HOUR_MS} h",
                        "Their phone may be switched off, out of battery or without internet. Last seen ${location.address ?: "on the map"}.",
                    )
                }
            } else if (silentFor <= settings.offlineAlertHours * HOUR_MS) {
                offlineNotified.remove(member.uid)
            }

            // Fast driving, from GPS speed on a fresh fix. At most one alert per person every 30 minutes.
            val kmh = location.speed * 3.6f
            if (settings.speedAlertKmh > 0 && member.sharing && now - location.timeMillis < SPEED_FRESH_MS && kmh > settings.speedAlertKmh) {
                if (now - (lastSpeedAlert[member.uid] ?: 0L) > SPEED_REPEAT_MS) {
                    lastSpeedAlert[member.uid] = now
                    notify(
                        "speed" + member.uid,
                        "$name is going ${kmh.roundToInt()} km/h",
                        "Over your ${settings.speedAlertKmh} km/h alert. Near ${location.address ?: "their last location"}.",
                    )
                }
            }
        }
    }

    /** "Expected by" rules: one alert per person, rule and day. */
    private suspend fun checkCurfews(
        members: List<Member>,
        settings: UserSettings,
        now: Long,
        notify: suspend (String, String, String) -> Unit,
    ) {
        if (settings.curfews.isEmpty()) return
        val minute = Days.minuteOfDay(now)
        val today = Days.epochDay(now)
        for (rule in settings.curfews) {
            val place = settings.places.firstOrNull { it.id == rule.placeId } ?: continue
            for (member in Curfews.late(rule, place, members, minute, now)) {
                if (curfewNotified.add("${rule.id}|${member.uid}|$today")) {
                    notify(
                        "curfew" + rule.id + member.uid,
                        "${member.displayName} isn't at ${place.name} yet",
                        "Expected by ${ReminderTimes.format(rule.hour, rule.minute)}. Now near ${member.location?.address ?: "somewhere else"}.",
                    )
                }
            }
        }
    }

    /** On this phone: a gentle "please charge" when the battery is low, so family keeps getting updates. */
    private fun checkMyBattery(settings: UserSettings) {
        val mine = settings.lastLocation ?: return
        val level = mine.battery ?: return
        if (!settings.chargeReminder || mine.charging || level > CHARGE_REMIND_AT) {
            if (mine.charging || level > CHARGE_REMIND_AT + 10) chargeReminded = false
            return
        }
        if (!chargeReminded) {
            chargeReminded = true
            Notifications.chargeReminder(context, level)
        }
    }

    private companion object {
        const val OUTSIDE = "outside"
        const val HYSTERESIS_M = 50.0
        const val LOW_BATTERY = 15
        const val SOS_FRESH_MS = 60 * 60_000L
        const val PING_FRESH_MS = 60 * 60_000L
        const val HOUR_MS = 60 * 60_000L
        const val SPEED_FRESH_MS = 10 * 60_000L
        const val SPEED_REPEAT_MS = 30 * 60_000L
        const val CHARGE_REMIND_AT = 20
    }
}
