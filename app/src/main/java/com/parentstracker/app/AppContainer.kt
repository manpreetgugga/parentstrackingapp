package com.parentstracker.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.parentstracker.app.data.CloudBackend
import com.parentstracker.app.data.FamilyRepository
import com.parentstracker.app.data.FirebaseBackend
import com.parentstracker.app.data.LocalOnlyBackend
import com.parentstracker.app.data.SettingsStore
import com.parentstracker.app.location.LocationSharingService
import com.parentstracker.app.location.LocationTracker
import com.parentstracker.app.location.SafetySensorMonitor
import com.parentstracker.app.notify.ActionReceiver
import com.parentstracker.app.notify.AlertMonitor
import com.parentstracker.app.notify.Notifications
import com.parentstracker.app.notify.ReminderScheduler
import com.parentstracker.app.notify.SafetyCheck
import com.parentstracker.app.util.Speaker
import com.parentstracker.app.widget.QuickActionsWidget
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-rolled dependency container, created once in [ParentsTrackerApp]. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val tracker = LocationTracker(context)
    val cloud: CloudBackend = createCloudBackend(context)
    val repository = FamilyRepository(appScope, settings, cloud, tracker)
    val reminderScheduler = ReminderScheduler(context)
    val speaker = Speaker(context)
    val safetyCheck = SafetyCheck(context, repository, reminderScheduler)
    private val alertMonitor = AlertMonitor(context, repository)
    private val sensorMonitor = SafetySensorMonitor(
        context,
        onShake = { appScope.launch { safetyCheck.start(SafetyCheck.Reason.SHAKE) } },
        onFall = { appScope.launch { safetyCheck.start(SafetyCheck.Reason.FALL) } },
    )
    private val inForeground = MutableStateFlow(false)

    fun start() {
        repository.start()
        alertMonitor.start(appScope)
        // Shake-to-SOS and fall detection listen to the accelerometer only while switched on.
        appScope.launch {
            repository.settingsState.filterNotNull().map { it.shakeToSos to it.fallDetection }.distinctUntilChanged().collect { (shake, fall) ->
                withContext(Dispatchers.Main) { sensorMonitor.update(shake, fall) }
            }
        }
        // "Going somewhere": ask "Did you reach …?" at the expected time; clear the alarms when the trip ends.
        appScope.launch {
            repository.settingsState.filterNotNull().map { it.journey }.distinctUntilChanged().collect { journey ->
                when {
                    journey == null -> {
                        reminderScheduler.cancel(ActionReceiver.journeyDue(context))
                        reminderScheduler.cancel(ActionReceiver.journeyGrace(context))
                        Notifications.cancel(context, Notifications.JOURNEY_ID)
                    }
                    // A trip already past its time (app restarted) keeps the alarms it already has.
                    journey.dueAt > System.currentTimeMillis() -> {
                        reminderScheduler.cancel(ActionReceiver.journeyGrace(context))
                        reminderScheduler.scheduleAt(journey.dueAt, ActionReceiver.journeyDue(context))
                    }
                }
            }
        }
        // Start the sharing service whenever it's needed and the app is on screen
        // (Android only lets location services start from the foreground).
        appScope.launch {
            combine(repository.shouldShare, inForeground) { should, foreground -> should == true && foreground }
                .collect { if (it) LocationSharingService.start(context) }
        }
        // Keep reminder alarms in step with the saved reminders (also re-arms them after a reboot).
        appScope.launch {
            var previousIds = emptySet<String>()
            repository.settingsState.filterNotNull().map { it.reminders }.distinctUntilChanged().collect { reminders ->
                reminderScheduler.sync(reminders, previousIds)
                previousIds = reminders.map { it.id }.toSet()
            }
        }
        // Show the latest check-in on the home-screen widget.
        appScope.launch {
            repository.settingsState.filterNotNull().map { it.checkInAt }.distinctUntilChanged().collect {
                QuickActionsWidget.refreshAll(context)
            }
        }
    }

    fun onForegroundChanged(foreground: Boolean) {
        inForeground.value = foreground
        if (foreground) {
            appScope.launch { repository.connect() }
            // Re-arm reminders in case "Alarms & reminders" was just allowed or revoked.
            appScope.launch { reminderScheduler.sync(settings.current().reminders, emptySet()) }
        }
    }

    /** Call after permissions change: starts sharing if it was waiting on a permission. */
    fun refreshSharing() {
        if (repository.shouldShare.value == true) LocationSharingService.start(context)
    }

    private companion object {
        fun createCloudBackend(context: Context): CloudBackend =
            if (BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseBackend()
            } else {
                LocalOnlyBackend()
            }
    }
}
