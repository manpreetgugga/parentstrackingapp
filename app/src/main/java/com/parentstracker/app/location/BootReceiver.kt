package com.parentstracker.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parentstracker.app.appContainer
import com.parentstracker.app.util.Perms
import kotlinx.coroutines.launch

/** Resumes location sharing after a reboot or app update, if it was on and "Allow all the time" is granted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = context.appContainer
        val pending = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settings.current()
                val shouldResume = settings.onboardingDone &&
                    settings.sharingEnabled &&
                    settings.lastViewerCount > 0 &&
                    container.cloud.isConfigured &&
                    Perms.hasBackgroundLocation(context)
                if (shouldResume) LocationSharingService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
