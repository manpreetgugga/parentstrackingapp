package com.parentstracker.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context) =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPreciseLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Allow all the time" — needed to restart sharing after a reboot. */
    fun hasBackgroundLocation(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasLocation(context)
        }

    fun hasNotifications(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    /** Whether reminders can fire at the exact minute (Android 12+ "Alarms & reminders"). */
    fun hasExactAlarms(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Asked for together: precise + approximate location, and notifications on Android 13+. */
    val foregroundRequest: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
