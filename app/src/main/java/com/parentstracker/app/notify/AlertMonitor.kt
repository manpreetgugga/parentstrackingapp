package com.parentstracker.app.notify

import android.content.Context
import com.parentstracker.app.data.AppEvent
import com.parentstracker.app.data.FamilyRepository
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.util.Geo
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
                    is AppEvent.PingAnswered -> Notifications.alert(
                        context,
                        ("answer" + event.name).hashCode(),
                        "${event.name}: ${event.response.message}",
                        "Reply to your “Are you OK?”",
                    )
                }
            }
        }
    }

    private fun check(members: List<Member>, settings: UserSettings, now: Long) {
        for (member in members) {
            if (member.isDemo) continue
            val name = member.displayName

            if (member.sos && member.sosAt > (lastSosAt[member.uid] ?: 0L)) {
                lastSosAt[member.uid] = member.sosAt
                if (now - member.sosAt < SOS_FRESH_MS) {
                    val where = member.location?.address?.let { " near $it" }.orEmpty()
                    Notifications.alert(context, ("sos" + member.uid).hashCode(), "🚨 SOS from $name", "$name needs help$where. Open the app to call or get directions.")
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
                    val id = ("place" + member.uid).hashCode()
                    when {
                        current != null -> Notifications.alert(context, id, "$name arrived at ${current.name}", "Updated ${Geo.timeAgo(location.timeMillis, now)}")
                        previousPlace != null -> Notifications.alert(context, id, "$name left ${previousPlace.name}", location.address ?: "Tap to see where")
                    }
                }
                lastPlaceId[member.uid] = currentId
            }

            val battery = location.battery
            if (settings.lowBatteryAlerts && battery != null) {
                if (battery <= LOW_BATTERY && !location.charging) {
                    if (lowBatteryNotified.add(member.uid)) {
                        Notifications.alert(context, ("battery" + member.uid).hashCode(), "$name's phone battery is low ($battery%)", "They may go offline soon. Maybe give them a call.")
                    }
                } else if (battery > LOW_BATTERY + 10 || location.charging) {
                    lowBatteryNotified.remove(member.uid)
                }
            }

            val silentFor = now - location.timeMillis
            if (settings.offlineAlertHours > 0 && member.sharing && silentFor > settings.offlineAlertHours * HOUR_MS) {
                if (offlineNotified.add(member.uid)) {
                    Notifications.alert(
                        context,
                        ("offline" + member.uid).hashCode(),
                        "No updates from $name for ${silentFor / HOUR_MS} h",
                        "Their phone may be switched off, out of battery or without internet. Last seen ${location.address ?: "on the map"}.",
                    )
                }
            } else if (silentFor <= settings.offlineAlertHours * HOUR_MS) {
                offlineNotified.remove(member.uid)
            }
        }
    }

    private companion object {
        const val OUTSIDE = "outside"
        const val HYSTERESIS_M = 50.0
        const val LOW_BATTERY = 15
        const val SOS_FRESH_MS = 60 * 60_000L
        const val PING_FRESH_MS = 60 * 60_000L
        const val HOUR_MS = 60 * 60_000L
    }
}
