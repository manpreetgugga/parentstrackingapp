package com.parentstracker.app.notify

import android.content.Context
import com.parentstracker.app.data.EventKind
import com.parentstracker.app.data.FamilyRepository

/**
 * "Are you OK?" countdown after a fall or a hard shake. The person can answer "I'm OK" from the
 * notification; if nobody answers before the deadline, an alarm sends the SOS to family.
 * The alarm survives the app being closed, so the SOS still goes out.
 */
class SafetyCheck(
    private val context: Context,
    private val repository: FamilyRepository,
    private val scheduler: ReminderScheduler,
) {
    enum class Reason(val title: String, val text: String, val timeoutMs: Long) {
        FALL("Did you fall? Are you OK?", "If you don't answer, SOS will be sent to your family automatically.", 60_000L),
        SHAKE("Shake detected: sending SOS", "Tap “I'm OK” if this was a mistake.", 10_000L),
    }

    @Volatile
    private var activeUntil = 0L

    suspend fun start(reason: Reason) {
        val now = System.currentTimeMillis()
        if (now < activeUntil) return
        val deadline = now + reason.timeoutMs
        activeUntil = deadline
        Notifications.safetyCheck(context, reason.title, reason.text, deadline)
        scheduler.scheduleAt(deadline, ActionReceiver.safetyTimeout(context))
        repository.log(EventKind.SOS, if (reason == Reason.FALL) "Possible fall detected" else "Shake-to-SOS triggered", "Safety check started")
    }

    suspend fun cancel(answeredOk: Boolean) {
        activeUntil = 0L
        scheduler.cancel(ActionReceiver.safetyTimeout(context))
        Notifications.cancel(context, Notifications.SAFETY_CHECK_ID)
        if (answeredOk) repository.log(EventKind.SOS, "Safety check: answered “I'm OK”", "No SOS sent")
    }

    suspend fun sendSos(reason: String) {
        cancel(answeredOk = false)
        repository.triggerSos(reason)
        Notifications.sosSent(context, reason)
    }
}
