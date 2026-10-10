package com.parentstracker.app.data

import android.location.Location
import android.util.Log
import com.parentstracker.app.location.LocationTracker
import com.parentstracker.app.util.Days
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.ReminderTimes
import com.parentstracker.app.util.Trail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Single source of truth for the UI, the sharing service and alerts. */
class FamilyRepository(
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    val cloud: CloudBackend,
    private val tracker: LocationTracker,
) {
    val settingsState: StateFlow<UserSettings?> = settings.flow.stateIn(scope, SharingStarted.Eagerly, null)

    val cloudStatus: StateFlow<CloudStatus> = cloud.status

    private val profile: StateFlow<MyCloudProfile?> = cloud.myProfile.stateIn(scope, SharingStarted.Eagerly, null)

    /** People who can see this user's location. */
    val viewers: StateFlow<List<Viewer>> = combine(profile, settings.flow) { p, s ->
        p?.viewers.orEmpty().map { v -> v.copy(relation = s.labels[v.uid]?.relation) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** People whose location this user can see (plus the demo family when enabled). */
    val members: StateFlow<List<Member>> = combine(cloud.members, settings.flow) { remote, s ->
        val real = remote.map { it.toMember(s.labels[it.uid]) }.sortedBy { it.displayName.lowercase() }
        if (s.demoMode) real + DemoData.members(s.lastLocation, s.places) else real
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val invites: StateFlow<List<Invite>> = cloud.myInvites.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Whether this phone should be publishing its location right now; null while still loading. */
    val shouldShare: StateFlow<Boolean?> = combine(settingsState, profile) { s, p ->
        when {
            !cloud.isConfigured -> false
            s == null || p == null -> null
            else -> s.onboardingDone && s.sharingEnabled && p.viewers.isNotEmpty()
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    private val handledInvites = mutableSetOf<String>()
    private val handledPings = mutableSetOf<String>()

    /** Unanswered "Are you OK?" pings sent to this user in the last day. */
    val incomingPings: StateFlow<List<Ping>> = cloud.incomingPings
        .map { list -> list.filter { it.response == null && System.currentTimeMillis() - it.createdAt < PING_TTL_MS } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Pings this user sent that are still waiting for a reply. */
    val outgoingPings: StateFlow<List<Ping>> = cloud.outgoingPings
        .map { list -> list.filter { it.response == null && System.currentTimeMillis() - it.createdAt < PING_TTL_MS } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun start() {
        scope.launch { connect() }
        scope.launch { invites.collect { handleInviteResponses(it) } }
        scope.launch { cloud.outgoingPings.collect { handlePingReplies(it) } }
        scope.launch {
            profile.filterNotNull().map { it.viewers.size }.distinctUntilChanged().collect { settings.setViewerCount(it) }
        }
    }

    suspend fun connect() {
        if (!cloud.isConfigured) return
        try {
            cloud.connect()
            val s = settings.current()
            if (s.onboardingDone) {
                cloud.publishState(
                    MyStateUpdate(
                        name = s.userName,
                        sharing = s.sharingEnabled,
                        medical = s.medical,
                        trail = if (s.shareTrail) s.trail else emptyList(),
                    ),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't connect to family sync", e)
        }
    }

    // ---- My profile & location ----

    suspend fun completeOnboarding(name: String) {
        settings.completeOnboarding(name)
        connect()
    }

    suspend fun setName(name: String) {
        settings.setName(name)
        publish(MyStateUpdate(name = name))
    }

    suspend fun refreshMyLocation(forcePublish: Boolean = false): Result<LocationSnapshot> = runCatching {
        check(tracker.hasLocationPermission()) { "Location permission is needed to find you" }
        val location = tracker.currentLocation()
            ?: throw IllegalStateException("Couldn't get a location fix. Is Location (GPS) turned on?")
        onLocation(location, forcePublish)
    }

    /** Stores a new fix (with address and battery) and publishes it if sharing is on. */
    suspend fun onLocation(location: Location, forcePublish: Boolean = false): LocationSnapshot {
        val s = settings.current()
        val previous = s.lastLocation
        val now = System.currentTimeMillis()
        val moved = previous == null ||
            Geo.distanceMeters(previous.lat, previous.lng, location.latitude, location.longitude) > ADDRESS_REFRESH_METERS
        val addressIsOld = previous == null || now - previous.timeMillis > ADDRESS_REFRESH_MS
        val address = if (!moved && !addressIsOld && previous?.address != null) {
            previous.address
        } else {
            tracker.reverseGeocode(location.latitude, location.longitude) ?: previous?.address?.takeIf { !moved }
        }
        val battery = tracker.batteryStatus()
        val snapshot = LocationSnapshot(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            timeMillis = minOf(location.time.takeIf { it > 0 } ?: now, now),
            speed = location.speed,
            address = address,
            battery = battery.level,
            charging = battery.charging,
        )
        settings.setLastLocation(snapshot)
        if (previous != null) onPlaceChange(s, previous, snapshot)
        val trail = Trail.append(s.trail, TrailPoint(snapshot.lat, snapshot.lng, snapshot.timeMillis, snapshot.address))
        val trailChanged = trail != s.trail
        if (trailChanged) settings.setTrail(trail)
        if (s.sharingEnabled || forcePublish) {
            publish(
                MyStateUpdate(
                    name = s.userName,
                    location = snapshot,
                    sharing = s.sharingEnabled,
                    trail = if (trailChanged && s.shareTrail) trail else null,
                ),
            )
        }
        return snapshot
    }

    /**
     * Arriving at one of my saved places: check in automatically ("Reached Home 🏠"), and finish a
     * "Going somewhere" trip whose destination is that place.
     */
    private suspend fun onPlaceChange(s: UserSettings, previous: LocationSnapshot, now: LocationSnapshot) {
        val before = Geo.placeFor(previous.lat, previous.lng, s.places, extraMeters = PLACE_SLACK_METERS)
        val after = Geo.placeFor(now.lat, now.lng, s.places) ?: return
        if (before?.id == after.id) return
        val journey = s.journey
        if (journey != null && journey.destination.trim().equals(after.name.trim(), ignoreCase = true)) {
            endJourney(arrived = true)
        } else if (s.autoArrivalCheckIn) {
            checkIn("Reached ${after.name} 🏠", EventKind.CHECK_IN, "Arrived at ${after.name} (automatic)")
        }
    }

    suspend fun setSharingEnabled(enabled: Boolean) {
        settings.setSharingEnabled(enabled)
        publish(MyStateUpdate(sharing = enabled))
    }

    suspend fun setInterval(minutes: Int) = settings.setInterval(minutes)

    // ---- Safety ----

    suspend fun triggerSos(reason: String? = null) {
        val now = System.currentTimeMillis()
        settings.setSos(true, now)
        publish(MyStateUpdate(sos = true, sosAt = now))
        log(EventKind.SOS, "SOS sent", reason)
        refreshMyLocation(forcePublish = true)
    }

    suspend fun cancelSos() {
        settings.setSos(false, 0)
        publish(MyStateUpdate(sos = false))
        log(EventKind.SOS, "SOS turned off", "Marked safe")
    }

    /** Shares a check-in with family and records it in the activity history (unless [logTitle] is null). */
    suspend fun checkIn(message: String, kind: EventKind = EventKind.CHECK_IN, logTitle: String? = message) {
        val now = System.currentTimeMillis()
        settings.setCheckIn(message, now)
        publish(MyStateUpdate(checkInMessage = message, checkInAt = now))
        if (logTitle != null) log(kind, logTitle, "Shared with family")
    }

    // ---- Activity history ----

    suspend fun log(kind: EventKind, title: String, detail: String? = null) {
        settings.appendEvent(LogEvent(System.currentTimeMillis(), kind, title, detail))
    }

    suspend fun clearHistory() = settings.clearEvents()

    // ---- Medicine ----

    /** "Taken" on a reminder: tells family, counts down stock. Returns tablets left when stock is tracked. */
    suspend fun medicineTaken(reminderId: String?, label: String): Int? {
        val left = reminderId?.let { settings.takeOne(it) }
        checkIn("Took $label 💊", EventKind.MEDICINE_TAKEN)
        return left
    }

    /** A reminder went unanswered: let family know so someone can call. */
    suspend fun reminderMissed(label: String, type: ReminderType) {
        if (type == ReminderType.MEDICINE) {
            checkIn("Hasn't marked $label as taken yet ⏰", EventKind.MEDICINE_MISSED, "Missed $label")
        } else {
            checkIn("Missed the $label ⏰", EventKind.CHECK_IN, "Missed $label")
        }
    }

    suspend fun setReminderStock(id: String, stock: Int?) = settings.setReminderStock(id, stock)

    // ---- Safe journey ----

    suspend fun startJourney(destination: String, minutes: Int) {
        val now = System.currentTimeMillis()
        val journey = Journey(destination, now, now + minutes * 60_000L)
        settings.setJourney(journey)
        val by = Days.minuteOfDay(journey.dueAt).let { ReminderTimes.format(it / 60, it % 60) }
        checkIn("Going to $destination 🚶 · expect me by $by", EventKind.JOURNEY, "Started trip to $destination")
    }

    suspend fun endJourney(arrived: Boolean) {
        val journey = settings.current().journey ?: return
        settings.setJourney(null)
        if (arrived) {
            checkIn("Reached ${journey.destination} ✅", EventKind.JOURNEY, "Reached ${journey.destination}")
        } else {
            log(EventKind.JOURNEY, "Cancelled trip to ${journey.destination}")
        }
    }

    /** The trip ran well past its time without "I've arrived": tell family. */
    suspend fun journeyOverdue() {
        val journey = settings.current().journey ?: return
        checkIn(
            "Hasn't confirmed reaching ${journey.destination} ⚠️",
            EventKind.JOURNEY,
            "Didn't confirm reaching ${journey.destination}",
        )
    }

    // ---- Mood ----

    suspend fun setMood(emoji: String) {
        val text = MOODS.firstOrNull { it.first == emoji }?.second ?: return
        settings.setMood(Days.epochDay(System.currentTimeMillis()), emoji)
        checkIn("$text $emoji", EventKind.MOOD)
    }

    // ---- Invites & connections ----

    suspend fun createInvite(inviteeName: String, relation: String, phone: String?, direction: ShareDirection): Result<Invite> =
        runCatching {
            if (!cloud.isConfigured) throw CloudNotConfiguredException()
            // The typed name/phone travel in the invite and become a local label once they accept.
            cloud.createInvite(settings.current().userName, inviteeName, relation, phone, direction)
        }

    suspend fun findInvite(code: String): Result<Invite?> = runCatching {
        if (!cloud.isConfigured) throw CloudNotConfiguredException()
        cloud.findInvite(code)
    }

    suspend fun acceptInvite(invite: Invite, theirRelation: String?): Result<Unit> = runCatching {
        val s = settings.current()
        cloud.acceptInvite(invite, s.userName)
        settings.putLabel(invite.fromUid, MemberLabel(relation = theirRelation))
        if (invite.direction.inviterSeesInvitee && !s.sharingEnabled) setSharingEnabled(true)
        if (invite.direction.inviterSeesInvitee) {
            // Give the new viewer a first location right away.
            refreshMyLocation()
        }
    }

    suspend fun declineInvite(invite: Invite): Result<Unit> = runCatching {
        cloud.declineInvite(invite, settings.current().userName)
    }

    suspend fun cancelInvite(code: String): Result<Unit> = runCatching { cloud.deleteInvite(code) }

    suspend fun stopSharingWith(viewer: Viewer): Result<Unit> = runCatching { cloud.removeViewer(viewer.uid) }

    suspend fun stopViewing(member: Member): Result<Unit> = runCatching {
        if (member.isDemo) settings.setDemoMode(false) else cloud.stopViewing(member.uid)
    }

    suspend fun updateLabel(uid: String, label: MemberLabel) = settings.replaceLabel(uid, label)

    /** When someone accepts one of my invites: remember who they are and, if asked, let them see me. */
    private suspend fun handleInviteResponses(invites: List<Invite>) {
        invites.filter { it.status == InviteStatus.ACCEPTED && it.acceptedBy != null }.forEach { invite ->
            if (!handledInvites.add(invite.code)) return@forEach
            val theirUid = invite.acceptedBy!!
            settings.putLabel(
                theirUid,
                MemberLabel(nickname = invite.inviteeName, relation = invite.relation, phone = invite.phone),
            )
            try {
                if (invite.direction.inviteeSeesInviter) {
                    cloud.addViewer(theirUid, invite.acceptedName ?: invite.inviteeName)
                }
                cloud.deleteInvite(invite.code)
                _events.tryEmit(AppEvent.InviteAccepted(invite.inviteeName))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't finish accepted invite ${invite.code}", e)
                handledInvites.remove(invite.code)
            }
        }
    }

    // ---- "Are you OK?" pings ----

    suspend fun sendPing(member: Member): Result<Unit> = runCatching {
        if (!cloud.isConfigured) throw CloudNotConfiguredException()
        cloud.sendPing(member.uid, settings.current().userName)
    }

    /** Answers a ping and also posts the answer as a check-in, so every viewer sees it. */
    suspend fun answerPing(pingId: String, response: PingResponse): Result<Unit> = runCatching {
        cloud.answerPing(pingId, response)
        checkIn(response.message)
    }

    private suspend fun handlePingReplies(pings: List<Ping>) {
        val now = System.currentTimeMillis()
        for (ping in pings) {
            val response = ping.response
            when {
                response != null && handledPings.add(ping.id) -> {
                    val name = members.value.firstOrNull { it.uid == ping.to }?.displayName ?: "Your family member"
                    _events.tryEmit(AppEvent.PingAnswered(name, response))
                    runCatching { cloud.deletePing(ping.id) }
                }
                response == null && now - ping.createdAt > PING_TTL_MS -> runCatching { cloud.deletePing(ping.id) }
            }
        }
    }

    // ---- Medical ID, reminders and other preferences ----

    suspend fun setMedical(info: MedicalInfo) {
        settings.setMedical(info)
        publish(MyStateUpdate(medical = info))
    }

    suspend fun setShareTrail(enabled: Boolean) {
        settings.setShareTrail(enabled)
        publish(MyStateUpdate(trail = if (enabled) settings.current().trail else emptyList()))
    }

    suspend fun addReminder(label: String, hour: Int, minute: Int, type: ReminderType, stock: Int? = null) =
        settings.addReminder(label, hour, minute, type, stock)

    suspend fun addCurfew(placeId: String, uid: String?, hour: Int, minute: Int) = settings.addCurfew(placeId, uid, hour, minute)
    suspend fun removeCurfew(id: String) = settings.removeCurfew(id)
    suspend fun setQuietHours(enabled: Boolean, start: Int, end: Int) = settings.setQuietHours(enabled, start, end)
    suspend fun setShakeToSos(enabled: Boolean) = settings.setShakeToSos(enabled)
    suspend fun setFallDetection(enabled: Boolean) = settings.setFallDetection(enabled)
    suspend fun setAutoArrivalCheckIn(enabled: Boolean) = settings.setAutoArrivalCheckIn(enabled)
    suspend fun setSpeedAlertKmh(kmh: Int) = settings.setSpeedAlertKmh(kmh)
    suspend fun setChargeReminder(enabled: Boolean) = settings.setChargeReminder(enabled)
    suspend fun setSimpleMode(enabled: Boolean) = settings.setSimpleMode(enabled)
    suspend fun setSimpleLanguage(language: SimpleLanguage) = settings.setSimpleLanguage(language)
    suspend fun setSimpleCallContact(name: String, number: String) = settings.setSimpleCallContact(name, number)
    suspend fun setSpeakAloud(enabled: Boolean) = settings.setSpeakAloud(enabled)

    suspend fun removeReminder(id: String) = settings.removeReminder(id)
    suspend fun setReminderEnabled(id: String, enabled: Boolean) = settings.setReminderEnabled(id, enabled)
    suspend fun setSeniorMode(enabled: Boolean) = settings.setSeniorMode(enabled)
    suspend fun setBatterySaver(enabled: Boolean) = settings.setBatterySaver(enabled)
    suspend fun setOfflineAlertHours(hours: Int) = settings.setOfflineAlertHours(hours)

    // ---- Local preferences ----

    suspend fun addPlaceHere(name: String, radiusMeters: Int): Result<Unit> = runCatching {
        val here = settings.current().lastLocation ?: refreshMyLocation().getOrThrow()
        settings.addPlace(name, here.lat, here.lng, radiusMeters)
    }

    suspend fun removePlace(id: String) = settings.removePlace(id)
    suspend fun addEmergencyNumber(number: String) = settings.addEmergencyNumber(number)
    suspend fun removeEmergencyNumber(number: String) = settings.removeEmergencyNumber(number)
    suspend fun setEmergencyDialNumber(number: String) = settings.setEmergencyDialNumber(number)
    suspend fun setDemoMode(enabled: Boolean) = settings.setDemoMode(enabled)
    suspend fun setArrivalAlerts(enabled: Boolean) = settings.setArrivalAlerts(enabled)
    suspend fun setLowBatteryAlerts(enabled: Boolean) = settings.setLowBatteryAlerts(enabled)

    private suspend fun publish(update: MyStateUpdate) {
        if (!cloud.isConfigured) return
        try {
            cloud.publishState(update)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't publish state", e)
        }
    }

    private fun RemoteMember.toMember(label: MemberLabel?) = Member(
        uid = uid,
        name = name,
        nickname = label?.nickname,
        relation = label?.relation,
        phone = label?.phone,
        location = location,
        sharing = sharing,
        sos = sos,
        sosAt = sosAt,
        checkInMessage = checkInMessage,
        checkInAt = checkInAt,
        trail = trail,
        medical = medical,
    )

    private companion object {
        const val TAG = "FamilyRepository"
        const val ADDRESS_REFRESH_METERS = 75.0
        const val ADDRESS_REFRESH_MS = 30 * 60_000L
        const val PING_TTL_MS = 24 * 60 * 60_000L
        const val PLACE_SLACK_METERS = 50.0
    }
}
