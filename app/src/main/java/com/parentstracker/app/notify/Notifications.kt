package com.parentstracker.app.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.parentstracker.app.R
import com.parentstracker.app.location.LocationSharingService
import com.parentstracker.app.ui.MainActivity
import com.parentstracker.app.util.Perms

object Notifications {
    const val CHANNEL_SHARING = "location_sharing"
    const val CHANNEL_ALERTS = "family_alerts"
    const val SHARING_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SHARING, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Always shown while your location is visible to family"
                },
                NotificationChannel(CHANNEL_ALERTS, "Family alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "SOS, arrivals, departures, low battery and accepted invites"
                },
            ),
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sharingNotification(context: Context, viewerNames: List<String>): Notification {
        val pause = PendingIntent.getService(
            context,
            1,
            Intent(context, LocationSharingService::class.java).setAction(LocationSharingService.ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_SHARING)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle("Your location is being shared")
            .setContentText("Visible to ${describe(viewerNames)} · Tap to manage")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Pause sharing", pause)
            .build()
    }

    fun describe(names: List<String>): String = when (names.size) {
        0 -> "your family"
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]} and ${names.size - 1} others"
    }

    @SuppressLint("MissingPermission")
    fun alert(context: Context, id: Int, title: String, text: String) {
        if (!Perms.hasNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were turned off between the check and the post.
        }
    }
}
