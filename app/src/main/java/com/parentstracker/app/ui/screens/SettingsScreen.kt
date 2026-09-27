@file:OptIn(ExperimentalMaterial3Api::class)

package com.parentstracker.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.BuildConfig
import com.parentstracker.app.data.CloudStatus
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.rememberPermissionSnapshot
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.Perms

private enum class SettingsDialog { NAME, PLACE, DIAL, SMS_CONTACT }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val cloudStatus by vm.cloudStatus.collectAsStateWithLifecycle()
    val perms = rememberPermissionSnapshot()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onPermissionsChanged()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 24.dp),
        ) {
            item { Group("Profile") }
            item {
                SettingRow(Icons.Filled.Person, "Your name", "${s.userName} · shown to your family", onClick = { dialog = SettingsDialog.NAME })
            }

            item { Group("Location sharing") }
            item {
                SwitchRow(
                    icon = Icons.Filled.Visibility,
                    title = "Share my location",
                    subtitle = if (s.sharingEnabled) "People you've allowed can see you" else "Paused: nobody gets your updates",
                    checked = s.sharingEnabled,
                    onChange = vm::setSharingEnabled,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("Update every", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "More often is more accurate but uses more battery",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        UserSettings.INTERVAL_CHOICES.forEachIndexed { index, minutes ->
                            SegmentedButton(
                                selected = s.updateIntervalMinutes == minutes,
                                onClick = { vm.setInterval(minutes) },
                                shape = SegmentedButtonDefaults.itemShape(index, UserSettings.INTERVAL_CHOICES.size),
                            ) { Text("$minutes min") }
                        }
                    }
                }
            }
            item {
                SettingRow(
                    icon = Icons.Filled.LocationOn,
                    title = "Location permission",
                    subtitle = when {
                        !perms.location -> "Not allowed. Tap to allow"
                        !perms.precise -> "Approximate only. Tap to allow precise location"
                        else -> "Allowed"
                    },
                    onClick = {
                        if (!perms.location || !perms.precise) foregroundLauncher.launch(Perms.foregroundRequest) else Intents.appSettings(context)
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Filled.Sync,
                    title = "Allow all the time",
                    subtitle = if (perms.background) {
                        "On: sharing restarts by itself after the phone restarts"
                    } else {
                        "Off. Recommended on parents' phones so sharing survives restarts"
                    },
                    onClick = {
                        when {
                            !perms.location -> foregroundLauncher.launch(Perms.foregroundRequest)
                            !perms.background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> Intents.appSettings(context)
                        }
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    subtitle = if (perms.notifications) "Allowed" else "Off. Tap to allow SOS and arrival alerts",
                    onClick = {
                        if (!perms.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            foregroundLauncher.launch(Perms.foregroundRequest)
                        } else {
                            Intents.appSettings(context)
                        }
                    },
                )
            }

            item { Group("Places") }
            item {
                Text(
                    "Get alerts when family arrive at or leave these places.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(s.places, key = { it.id }) { place ->
                SettingRow(
                    icon = Icons.Filled.Place,
                    title = place.name,
                    subtitle = "Radius ${place.radiusMeters} m",
                    trailing = {
                        IconButton(onClick = { vm.removePlace(place.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${place.name}") }
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Filled.AddLocationAlt,
                    title = "Save my current location as a place",
                    subtitle = "e.g. Home, Mom's house, Gurudwara, Office",
                    onClick = { dialog = SettingsDialog.PLACE },
                )
            }

            item { Group("Emergency") }
            item {
                SettingRow(Icons.Filled.Call, "Emergency call number", s.emergencyDialNumber, onClick = { dialog = SettingsDialog.DIAL })
            }
            items(s.emergencyNumbers, key = { "sms-$it" }) { number ->
                SettingRow(
                    icon = Icons.Filled.Sms,
                    title = number,
                    subtitle = "Gets a text with your location when you press SOS",
                    trailing = {
                        IconButton(onClick = { vm.removeEmergencyNumber(number) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove $number") }
                    },
                )
            }
            item {
                SettingRow(Icons.Filled.PersonAdd, "Add emergency SMS contact", null, onClick = { dialog = SettingsDialog.SMS_CONTACT })
            }

            item { Group("Alerts about family") }
            item {
                SwitchRow(Icons.Filled.Home, "Arrivals & departures", "When someone arrives at or leaves a saved place", s.arrivalAlerts, vm::setArrivalAlerts)
            }
            item {
                SwitchRow(Icons.Filled.BatteryAlert, "Low battery", "When a family member's phone drops to 15%", s.lowBatteryAlerts, vm::setLowBatteryAlerts)
            }

            item { Group("Setup status") }
            item {
                val status = cloudStatus
                SettingRow(
                    icon = if (status is CloudStatus.Connected) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                    title = "Family sync (Firebase)",
                    subtitle = when (status) {
                        CloudStatus.NotConfigured -> "Not set up. Add app/google-services.json and rebuild (see README)"
                        CloudStatus.Connecting -> "Connecting…"
                        is CloudStatus.Connected -> "Connected"
                        is CloudStatus.Error -> status.message
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Filled.Map,
                    title = "Google Maps API key",
                    subtitle = if (vm.mapsConfigured) "Added: the live map is on" else "Not added yet. Put MAPS_API_KEY in local.properties and rebuild",
                )
            }
            item {
                SwitchRow(Icons.Filled.Group, "Show demo family", "Preview the app with pretend family members", s.demoMode, vm::setDemoMode)
            }

            item { Group("About") }
            item {
                SettingRow(
                    Icons.Filled.Shield,
                    "Privacy",
                    "Your location only goes to people you approve. Anyone who shares with you sees a banner and a notification the whole time.",
                )
            }
            item { SettingRow(Icons.Filled.Info, "Version", BuildConfig.VERSION_NAME) }
        }
    }

    when (dialog) {
        SettingsDialog.NAME -> TextInputDialog(
            title = "Your name",
            label = "Name",
            initial = s.userName,
            capitalization = KeyboardCapitalization.Words,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.setName(it)
                dialog = null
            },
        )
        SettingsDialog.DIAL -> TextInputDialog(
            title = "Emergency call number",
            label = "Number (112 works across India and Europe)",
            initial = s.emergencyDialNumber,
            keyboardType = KeyboardType.Phone,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.setEmergencyDialNumber(it)
                dialog = null
            },
        )
        SettingsDialog.SMS_CONTACT -> TextInputDialog(
            title = "Emergency SMS contact",
            label = "Phone number",
            initial = "",
            keyboardType = KeyboardType.Phone,
            confirmLabel = "Add",
            onDismiss = { dialog = null },
            onConfirm = {
                vm.addEmergencyNumber(it)
                dialog = null
            },
        )
        SettingsDialog.PLACE -> AddPlaceDialog(
            onDismiss = { dialog = null },
            onConfirm = { name, radius ->
                vm.addPlaceHere(name, radius)
                dialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    confirmLabel: String = "Save",
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPlaceDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("Home") }
    var radius by remember { mutableIntStateOf(150) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your current location will be saved. Stand at the place you want to save.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Text("Size", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 150, 300, 500).forEach { r ->
                        FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r m") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), radius) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
