package com.parentstracker.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import com.parentstracker.app.data.Reminder
import com.parentstracker.app.util.ReminderTimes

/**
 * Schedules daily reminders. Uses exact alarms when the phone allows them ("Alarms & reminders"
 * permission), otherwise a 10-minute window. Each alarm reschedules itself for the next day.
 */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    fun schedule(reminder: Reminder) {
        val at = ReminderTimes.nextTrigger(reminder.hour, reminder.minute, System.currentTimeMillis())
        scheduleAt(at, ActionReceiver.reminderAlarm(context, reminder.id))
    }

    /** "Did you take it?" follow-up, [FOLLOW_UP_MS] after the reminder. Cancelled by "Taken". */
    fun scheduleFollowUp(reminder: Reminder) {
        scheduleAt(System.currentTimeMillis() + FOLLOW_UP_MS, ActionReceiver.reminderFollowUp(context, reminder.id))
    }

    fun cancelFollowUp(reminderId: String) {
        alarms?.cancel(ActionReceiver.reminderFollowUp(context, reminderId))
    }

    /** One-shot alarm (safety-check timeouts, trip deadlines), exact when the phone allows it. */
    fun scheduleAt(at: Long, intent: PendingIntent) {
        val alarms = alarms ?: return
        try {
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission was revoked between the check and the call.
            Log.w(TAG, "Falling back to a windowed reminder", e)
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
        }
    }

    fun cancel(reminderId: String) {
        alarms?.cancel(ActionReceiver.reminderAlarm(context, reminderId))
    }

    fun cancel(intent: PendingIntent) {
        alarms?.cancel(intent)
    }

    /** Makes the scheduled alarms match [reminders]; [previousIds] are cancelled if they were removed. */
    fun sync(reminders: List<Reminder>, previousIds: Set<String>) {
        val currentIds = reminders.map { it.id }.toSet()
        (previousIds - currentIds).forEach(::cancel)
        reminders.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    private companion object {
        const val TAG = "ReminderScheduler"
        const val WINDOW_MS = 10 * 60_000L
        const val FOLLOW_UP_MS = 45 * 60_000L
    }
}
