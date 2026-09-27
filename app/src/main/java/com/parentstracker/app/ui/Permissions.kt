package com.parentstracker.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parentstracker.app.util.Perms

data class PermissionSnapshot(
    val location: Boolean,
    val precise: Boolean,
    val background: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean = true,
) {
    companion object {
        fun read(context: Context) = PermissionSnapshot(
            location = Perms.hasLocation(context),
            precise = Perms.hasPreciseLocation(context),
            background = Perms.hasBackgroundLocation(context),
            notifications = Perms.hasNotifications(context),
            exactAlarms = Perms.hasExactAlarms(context),
        )
    }
}

/** Current permission state, re-read every time the screen resumes (e.g. after a permission dialog). */
@Composable
fun rememberPermissionSnapshot(): PermissionSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(PermissionSnapshot.read(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) snapshot = PermissionSnapshot.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return snapshot
}
