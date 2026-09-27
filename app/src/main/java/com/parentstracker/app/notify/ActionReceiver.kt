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
import com.parentstracker.app.widget.QuickActionsWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles taps that happen outside the app: notification buttons (answer a ping, mark a reminder done),
 * the home-screen widget's "I'm OK" button, and reminder alarms firing.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val repository = container.repository
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
                            val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                            val message = if (intent.getStringExtra(EXTRA_TYPE) == ReminderType.MEDICINE.name) {
                                "Took $label 💊"
                            } else {
                                CHECK_IN_MESSAGES.first()
                            }
                            repository.checkIn(message)
                        }

                        ACTION_REMINDER_FIRE -> {
                            val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return@withTimeoutOrNull
                            val reminder = container.settings.current().reminders.firstOrNull { it.id == id }
                            if (reminder != null && reminder.enabled) {
                                Notifications.reminder(context, reminder)
                                container.reminderScheduler.schedule(reminder) // same time tomorrow
                            }
                        }

                        ACTION_QUICK_OK -> {
                            repository.checkIn(CHECK_IN_MESSAGES.first())
                            QuickActionsWidget.refreshAll(context)
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
        const val ACTION_QUICK_OK = "com.parentstracker.app.action.QUICK_OK"
        private const val EXTRA_PING_ID = "ping_id"
        private const val EXTRA_RESPONSE = "response"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_TYPE = "type"

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
                .putExtra(EXTRA_LABEL, reminder.label)
                .putExtra(EXTRA_TYPE, reminder.type.name)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
        )

        fun reminderAlarm(context: Context, reminderId: String) = broadcast(
            context,
            ("fire$reminderId").hashCode(),
            Intent(ACTION_REMINDER_FIRE).putExtra(EXTRA_REMINDER_ID, reminderId),
        )

        fun quickOk(context: Context) = broadcast(context, ACTION_QUICK_OK.hashCode(), Intent(ACTION_QUICK_OK))
    }
}
