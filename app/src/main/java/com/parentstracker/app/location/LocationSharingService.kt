package com.parentstracker.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parentstracker.app.appContainer
import com.parentstracker.app.notify.Notifications
import com.parentstracker.app.util.Perms
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps publishing this phone's location while someone is allowed to see it.
 * Always runs with a visible notification ("Your location is being shared") so the person being
 * tracked knows about it and can pause from the notification.
 */
class LocationSharingService : LifecycleService() {

    private val container by lazy { appContainer }
    private lateinit var fused: FusedLocationProviderClient
    private var observing = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lifecycleScope.launch { container.repository.onLocation(location) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PAUSE) {
            container.appScope.launch { container.repository.setSharingEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        val viewerNames = container.repository.viewers.value.map { it.name }
        if (!Perms.hasLocation(this) || !enterForeground(viewerNames)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            observe()
        }
        return START_STICKY
    }

    private fun enterForeground(viewerNames: List<String>): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(
            this,
            Notifications.SHARING_NOTIFICATION_ID,
            Notifications.sharingNotification(this, viewerNames),
            type,
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't start location sharing in the foreground", e)
        false
    }

    private fun observe() {
        val repository = container.repository

        // Stop as soon as nobody should receive our location (paused, or the last viewer removed).
        lifecycleScope.launch {
            val decided = withTimeoutOrNull(45_000) { repository.shouldShare.filterNotNull().first() }
            if (decided != true) {
                stopSelf()
                return@launch
            }
            repository.shouldShare.collect { if (it == false) stopSelf() }
        }
        lifecycleScope.launch {
            repository.settingsState.filterNotNull()
                .map { it.updateIntervalMinutes }
                .distinctUntilChanged()
                .collect { requestUpdates(it) }
        }
        lifecycleScope.launch {
            repository.viewers
                .map { list -> list.map { it.name } }
                .distinctUntilChanged()
                .collect { updateNotification(it) }
        }
        lifecycleScope.launch {
            container.tracker.currentLocation()?.let { repository.onLocation(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(minutes: Int) {
        if (!Perms.hasLocation(this)) {
            stopSelf()
            return
        }
        fused.removeLocationUpdates(callback)
        val intervalMs = minutes * 60_000L
        val priority = if (minutes <= 5) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(viewerNames: List<String>) {
        if (!Perms.hasNotifications(this)) return
        try {
            NotificationManagerCompat.from(this)
                .notify(Notifications.SHARING_NOTIFICATION_ID, Notifications.sharingNotification(this, viewerNames))
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't update notification", e)
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationSharingService"
        const val ACTION_PAUSE = "com.parentstracker.app.action.PAUSE_SHARING"

        fun start(context: Context) {
            if (!Perms.hasLocation(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocationSharingService::class.java))
            } catch (e: Exception) {
                // Android blocks starting from the background in some states; we retry next time the app opens.
                Log.w(TAG, "Couldn't start location sharing", e)
            }
        }
    }
}
