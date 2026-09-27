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
import com.parentstracker.app.notify.AlertMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Hand-rolled dependency container, created once in [ParentsTrackerApp]. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val tracker = LocationTracker(context)
    val cloud: CloudBackend = createCloudBackend(context)
    val repository = FamilyRepository(appScope, settings, cloud, tracker)
    private val alertMonitor = AlertMonitor(context, repository)
    private val inForeground = MutableStateFlow(false)

    fun start() {
        repository.start()
        alertMonitor.start(appScope)
        // Start the sharing service whenever it's needed and the app is on screen
        // (Android only lets location services start from the foreground).
        appScope.launch {
            combine(repository.shouldShare, inForeground) { should, foreground -> should == true && foreground }
                .collect { if (it) LocationSharingService.start(context) }
        }
    }

    fun onForegroundChanged(foreground: Boolean) {
        inForeground.value = foreground
        if (foreground) appScope.launch { repository.connect() }
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
