package com.parentstracker.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Helps people stop their phone's battery saver from closing the app (and with it, location sharing). */
object Reliability {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun brand(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** What to tap on this brand of phone. Menus differ by model, so the wording stays general. */
    fun steps(manufacturer: String = Build.MANUFACTURER): List<String> = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> listOf(
            "Open Settings → Apps → Manage apps → Parents Tracker.",
            "Turn on “Autostart”.",
            "Battery saver → choose “No restrictions”.",
            "In Recent apps, pull the app down to lock it.",
        )
        "oppo", "realme", "oneplus" -> listOf(
            "Open Settings → Apps → Parents Tracker → Battery usage.",
            "Turn on “Allow background activity” and “Allow auto launch”.",
            "In Recent apps, tap the menu on the app and choose “Lock”.",
        )
        "vivo", "iqoo" -> listOf(
            "Open Settings → Battery → Background power consumption management.",
            "Set Parents Tracker to “Allow”.",
            "Settings → Apps → Autostart → turn on Parents Tracker.",
        )
        "samsung" -> listOf(
            "Open Settings → Apps → Parents Tracker → Battery.",
            "Choose “Unrestricted”.",
            "Settings → Battery → Background usage limits: make sure the app isn't in “Sleeping apps”.",
        )
        "huawei", "honor" -> listOf(
            "Open Settings → Battery → App launch.",
            "Turn off “Manage automatically” for Parents Tracker and allow all three options.",
        )
        else -> listOf(
            "Open Settings → Apps → Parents Tracker → Battery.",
            "Choose “Unrestricted” (or “Don't optimise”).",
        )
    }

    fun openBatterySettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next screen.
            }
        }
    }
}
