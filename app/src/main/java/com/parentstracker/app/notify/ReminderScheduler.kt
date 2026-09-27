package com.parentstracker.app.notify

import android.app.AlarmManager
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
        val alarms = alarms ?: return
        val at = ReminderTimes.nextTrigger(reminder.hour, reminder.minute, System.currentTimeMillis())
        val intent = ActionReceiver.reminderAlarm(context, reminder.id)
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

    /** Makes the scheduled alarms match [reminders]; [previousIds] are cancelled if they were removed. */
    fun sync(reminders: List<Reminder>, previousIds: Set<String>) {
        val currentIds = reminders.map { it.id }.toSet()
        (previousIds - currentIds).forEach(::cancel)
        reminders.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    private companion object {
        const val TAG = "ReminderScheduler"
        const val WINDOW_MS = 10 * 60_000L
    }
}
