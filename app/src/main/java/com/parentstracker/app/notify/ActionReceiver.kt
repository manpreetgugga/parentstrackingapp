package com.parentstracker.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.parentstracker.app.appContainer
import com.parentstracker.app.data.CHECK_IN_MESSAGES
import com.parentstracker.app.data.PingResponse
import com.parentstracker.app.data.Reminder
import com.parentstracker.app.data.ReminderType
import com.parentstracker.app.util.SimpleText
import com.parentstracker.app.widget.QuickActionsWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles taps that happen outside the app: notification buttons (answer a ping, mark a reminder done,
 * answer a safety check or a trip), the home-screen widget's "I'm OK" button, and alarms firing
 * (reminders and their follow-ups, safety-check timeouts, trip deadlines).
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val repository = container.repository
        val scheduler = container.reminderScheduler
        val pending = goAsync()
        container.appScope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    when (intent.action) {
                        ACTION_PING_REPLY -> {
                            val pingId = intent.getStringExtra(EXTRA_PING_ID) ?: return@withTimeoutOrNull
                            val response = intent.getStringExtra(EXTRA_RESPONSE)
                                ?.let { runCatching { PingResponse.valueOf(it) }.getOrNull() } ?: PingResponse.OK
                            Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                            repository.answerPing(pingId, response)
                        }

                        ACTION_REMINDER_DONE -> {
                            Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
                            reminderId?.let { scheduler.cancelFollowUp(it) }
                            val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                            if (intent.getStringExtra(EXTRA_TYPE) == ReminderType.MEDICINE.name) {
                                val left = repository.medicineTaken(reminderId, label)
                                if (left != null && left <= REFILL_AT) Notifications.refill(context, label, left)
                            } else {
                                repository.checkIn(CHECK_IN_MESSAGES.first())
                            }
                        }

                        ACTION_REMINDER_FIRE -> {
                            val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return@withTimeoutOrNull
                            val settings = container.settings.current()
                            val reminder = settings.reminders.firstOrNull { it.id == id }
                            if (reminder != null && reminder.enabled) {
                                Notifications.reminder(context, reminder)
                                if (settings.speakAloud) {
                                    val text = SimpleText(settings.simpleLanguage)
                                    container.speaker.speak(
                                        if (reminder.type == ReminderType.MEDICINE) text.medicineTime(reminder.label) else text.checkInTime,
                                        settings.simpleLanguage.localeTag,
                                    )
                                }
                                scheduler.scheduleFollowUp(reminder)
                                scheduler.schedule(reminder) // same time tomorrow
                            }
                        }

                        ACTION_REMINDER_FOLLOW_UP -> {
                            val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return@withTimeoutOrNull
                            val reminder = container.settings.current().reminders.firstOrNull { it.id == id }
                            if (reminder != null && reminder.enabled) {
                                Notifications.reminderFollowUp(context, reminder)
                                repository.reminderMissed(reminder.label, reminder.type)
                            }
                        }

                        ACTION_QUICK_OK -> {
                            repository.checkIn(CHECK_IN_MESSAGES.first())
                            QuickActionsWidget.refreshAll(context)
                        }

                        ACTION_SAFETY_OK -> container.safetyCheck.cancel(answeredOk = true)

                        ACTION_SAFETY_SOS -> container.safetyCheck.sendSos("You asked for help.")

                        ACTION_SAFETY_TIMEOUT -> container.safetyCheck.sendSos("No answer to the safety check.")

                        ACTION_JOURNEY_DUE -> {
                            val journey = container.settings.current().journey ?: return@withTimeoutOrNull
                            Notifications.journeyDue(context, journey.destination)
                            scheduler.scheduleAt(System.currentTimeMillis() + JOURNEY_GRACE_MS, journeyGrace(context))
                        }

                        ACTION_JOURNEY_GRACE -> {
                            if (container.settings.current().journey != null) repository.journeyOverdue()
                        }

                        ACTION_JOURNEY_ARRIVED -> {
                            Notifications.cancel(context, Notifications.JOURNEY_ID)
                            repository.endJourney(arrived = true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Action ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ActionReceiver"
        const val ACTION_PING_REPLY = "com.parentstracker.app.action.PING_REPLY"
        const val ACTION_REMINDER_DONE = "com.parentstracker.app.action.REMINDER_DONE"
        const val ACTION_REMINDER_FIRE = "com.parentstracker.app.action.REMINDER_FIRE"
        const val ACTION_REMINDER_FOLLOW_UP = "com.parentstracker.app.action.REMINDER_FOLLOW_UP"
        const val ACTION_QUICK_OK = "com.parentstracker.app.action.QUICK_OK"
        const val ACTION_SAFETY_OK = "com.parentstracker.app.action.SAFETY_OK"
        const val ACTION_SAFETY_SOS = "com.parentstracker.app.action.SAFETY_SOS"
        const val ACTION_SAFETY_TIMEOUT = "com.parentstracker.app.action.SAFETY_TIMEOUT"
        const val ACTION_JOURNEY_DUE = "com.parentstracker.app.action.JOURNEY_DUE"
        const val ACTION_JOURNEY_GRACE = "com.parentstracker.app.action.JOURNEY_GRACE"
        const val ACTION_JOURNEY_ARRIVED = "com.parentstracker.app.action.JOURNEY_ARRIVED"
        private const val EXTRA_PING_ID = "ping_id"
        private const val EXTRA_RESPONSE = "response"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_TYPE = "type"

        /** Warn about a refill when this many tablets (or fewer) are left. */
        const val REFILL_AT = 5

        /** How long after "Did you reach …?" before family is told the trip wasn't confirmed. */
        const val JOURNEY_GRACE_MS = 20 * 60_000L

        private fun broadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent.setClass(context, ActionReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun pingReply(context: Context, pingId: String, response: PingResponse, notificationId: Int) = broadcast(
            context,
            (pingId + response.name).hashCode(),
            Intent(ACTION_PING_REPLY)
                .putExtra(EXTRA_PING_ID, pingId)
                .putExtra(EXTRA_RESPONSE, response.name)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
        )

        fun reminderDone(context: Context, reminder: Reminder, notificationId: Int) = broadcast(
            context,
            ("done" + reminder.id).hashCode(),
            Intent(ACTION_REMINDER_DONE)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_LABEL, reminder.label)
                .putExtra(EXTRA_TYPE, reminder.type.name)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
        )

        fun reminderAlarm(context: Context, reminderId: String) = broadcast(
            context,
            ("fire$reminderId").hashCode(),
            Intent(ACTION_REMINDER_FIRE).putExtra(EXTRA_REMINDER_ID, reminderId),
        )

        fun reminderFollowUp(context: Context, reminderId: String) = broadcast(
            context,
            ("followup$reminderId").hashCode(),
            Intent(ACTION_REMINDER_FOLLOW_UP).putExtra(EXTRA_REMINDER_ID, reminderId),
        )

        fun quickOk(context: Context) = broadcast(context, ACTION_QUICK_OK.hashCode(), Intent(ACTION_QUICK_OK))

        fun safetyOk(context: Context) = broadcast(context, ACTION_SAFETY_OK.hashCode(), Intent(ACTION_SAFETY_OK))

        fun safetySos(context: Context) = broadcast(context, ACTION_SAFETY_SOS.hashCode(), Intent(ACTION_SAFETY_SOS))

        fun safetyTimeout(context: Context) = broadcast(context, ACTION_SAFETY_TIMEOUT.hashCode(), Intent(ACTION_SAFETY_TIMEOUT))

        fun journeyDue(context: Context) = broadcast(context, ACTION_JOURNEY_DUE.hashCode(), Intent(ACTION_JOURNEY_DUE))

        fun journeyGrace(context: Context) = broadcast(context, ACTION_JOURNEY_GRACE.hashCode(), Intent(ACTION_JOURNEY_GRACE))

        fun journeyArrived(context: Context) = broadcast(context, ACTION_JOURNEY_ARRIVED.hashCode(), Intent(ACTION_JOURNEY_ARRIVED))
    }
}
