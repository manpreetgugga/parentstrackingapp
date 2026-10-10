package com.parentstracker.app.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    const val CHANNEL_QUIET = "quiet_alerts"
    const val CHANNEL_SAFETY = "safety_checks"
    const val SHARING_NOTIFICATION_ID = 1001
    const val SAFETY_CHECK_ID = 2001
    const val JOURNEY_ID = 2002
    const val CHARGE_ID = 2003
    const val SOS_SENT_ID = 2004

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SAFETY, "Fall & shake safety checks", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "“Are you OK?” countdowns after a fall or a shake, before SOS is sent"
                },
                NotificationChannel(CHANNEL_QUIET, "Alerts during quiet hours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Arrivals, low battery and similar alerts that arrive during your quiet hours, without sound"
                },
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

    /** A family alert; [quiet] ones (during quiet hours) arrive without sound. */
    fun alert(context: Context, id: Int, title: String, text: String, quiet: Boolean = false) {
        val builder = builder(context, if (quiet) CHANNEL_QUIET else CHANNEL_ALERTS, title, text)
        if (quiet) builder.setPriority(NotificationCompat.PRIORITY_LOW)
        post(context, id, builder.build())
    }

    /** After a fall or a hard shake: "Are you OK?", with a countdown to an automatic SOS. */
    fun safetyCheck(context: Context, title: String, text: String, deadline: Long) {
        val notification = builder(context, CHANNEL_SAFETY, title, text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(deadline)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(0, "I'm OK", ActionReceiver.safetyOk(context))
            .addAction(0, "Send SOS now", ActionReceiver.safetySos(context))
            .build()
        post(context, SAFETY_CHECK_ID, notification)
    }

    fun sosSent(context: Context, reason: String) {
        post(
            context,
            SOS_SENT_ID,
            builder(context, CHANNEL_SAFETY, "🚨 SOS sent", "$reason Your family has been alerted with your location. Open the app to call for help.")
                .setContentIntent(sosIntent(context))
                .build(),
        )
    }

    /** "Did you reach …?" when a trip's expected time has passed. */
    fun journeyDue(context: Context, destination: String) {
        val notification = builder(context, CHANNEL_ALERTS, "Did you reach $destination?", "Let your family know you arrived safely.")
            .addAction(0, "Reached ✅", ActionReceiver.journeyArrived(context))
            .addAction(0, "I need help", sosIntent(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        post(context, JOURNEY_ID, notification)
    }

    /** Second nudge when a reminder wasn't answered; the same id replaces the first one. */
    fun reminderFollowUp(context: Context, reminder: Reminder) {
        val id = reminderNotificationId(reminder.id)
        val (title, action) = when (reminder.type) {
            ReminderType.MEDICINE -> "💊 Did you take ${reminder.label}?" to "Taken"
            ReminderType.CHECK_IN -> "Are you OK? (${reminder.label})" to "I'm OK 👍"
        }
        val notification = builder(context, CHANNEL_REMINDERS, title, "Your family has been told it's still pending.")
            .addAction(0, action, ActionReceiver.reminderDone(context, reminder, id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        post(context, id, notification)
    }

    fun refill(context: Context, label: String, left: Int) {
        alert(
            context,
            ("refill$label").hashCode(),
            if (left == 0) "💊 $label has run out" else "💊 Only $left $label left",
            "Time to buy more. You can update the count in Settings → Reminders.",
        )
    }

    fun chargeReminder(context: Context, level: Int) {
        post(
            context,
            CHARGE_ID,
            builder(context, CHANNEL_REMINDERS, "🔋 Please charge your phone ($level%)", "So your family can keep seeing that you're safe.").build(),
        )
    }

    /** Opens the app on the SOS confirmation (same link the widget's SOS button uses). */
    private fun sosIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2,
        Intent(Intent.ACTION_VIEW, Uri.parse("parentstracker://sos"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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
