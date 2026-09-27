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
import com.parentstracker.app.data.PingResponse
import com.parentstracker.app.data.Reminder
import com.parentstracker.app.data.ReminderType
import com.parentstracker.app.location.LocationSharingService
import com.parentstracker.app.ui.MainActivity
import com.parentstracker.app.util.Perms

object Notifications {
    const val CHANNEL_SHARING = "location_sharing"
    const val CHANNEL_ALERTS = "family_alerts"
    const val CHANNEL_REMINDERS = "reminders"
    const val SHARING_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SHARING, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Always shown while your location is visible to family"
                },
                NotificationChannel(CHANNEL_ALERTS, "Family alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "SOS, arrivals, departures, low battery, no updates, pings and accepted invites"
                },
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Medicine and daily check-in reminders"
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

    fun alert(context: Context, id: Int, title: String, text: String) {
        post(context, id, builder(context, CHANNEL_ALERTS, title, text).build())
    }

    /** "Manpreet is asking: are you OK?" with one-tap answers. */
    fun ping(context: Context, pingId: String, fromName: String) {
        val id = pingNotificationId(pingId)
        val notification = builder(context, CHANNEL_ALERTS, "$fromName is asking: are you OK?", "Tap an answer. It's shared with your family as a check-in.")
            .addAction(0, "I'm OK 👍", ActionReceiver.pingReply(context, pingId, PingResponse.OK, id))
            .addAction(0, "Please call me", ActionReceiver.pingReply(context, pingId, PingResponse.CALL_ME, id))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        post(context, id, notification)
    }

    fun reminder(context: Context, reminder: Reminder) {
        val id = reminderNotificationId(reminder.id)
        val (title, text, action) = when (reminder.type) {
            ReminderType.MEDICINE -> Triple("💊 Time for ${reminder.label}", "Tap “Taken” to let your family know.", "Taken")
            ReminderType.CHECK_IN -> Triple("Good day! ${reminder.label}", "Let your family know you're OK with one tap.", "I'm OK 👍")
        }
        val notification = builder(context, CHANNEL_REMINDERS, title, text)
            .addAction(0, action, ActionReceiver.reminderDone(context, reminder, id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        post(context, id, notification)
    }

    fun pingNotificationId(pingId: String) = ("ping$pingId").hashCode()

    fun reminderNotificationId(reminderId: String) = ("reminder$reminderId").hashCode()

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun builder(context: Context, channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        if (!Perms.hasNotifications(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were turned off between the check and the post.
        }
    }
}
