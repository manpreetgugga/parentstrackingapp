@file:OptIn(ExperimentalMaterial3Api::class)

package com.parentstracker.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.ui.graphics.Color
import com.parentstracker.app.BuildConfig
import com.parentstracker.app.appContainer
import com.parentstracker.app.data.BLOOD_GROUPS
import com.parentstracker.app.data.Reminder
import com.parentstracker.app.data.SimpleLanguage
import com.parentstracker.app.notify.ActionReceiver
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.QuietHours
import com.parentstracker.app.util.Reliability
import com.parentstracker.app.util.SimpleText
import com.parentstracker.app.data.CloudStatus
import com.parentstracker.app.data.MedicalInfo
import com.parentstracker.app.data.ReminderType
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.rememberPermissionSnapshot
import com.parentstracker.app.util.BatterySaver
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.ReminderTimes
import com.parentstracker.app.util.Perms

private enum class SettingsDialog { NAME, PLACE, DIAL, SMS_CONTACT, MEDICAL, REMINDER, SIMPLE_CONTACT, QUIET_HOURS, CURFEW, RELIABILITY }

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenHistory: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val members by vm.members.collectAsStateWithLifecycle()
    var stockFor by remember { mutableStateOf<Reminder?>(null) }
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

            item {
                SwitchRow(
                    Icons.Filled.TextIncrease,
                    "Senior mode (large text)",
                    "Bigger text and buttons, easier to read",
                    s.seniorMode,
                    vm::setSeniorMode,
                )
            }
            item {
                SettingRow(
                    Icons.Filled.History,
                    "Activity history",
                    "Check-ins, medicines, trips, SOS and alerts, with a weekly summary",
                    onClick = onOpenHistory,
                )
            }

            item { Group("Simple mode for parents") }
            item {
                SwitchRow(
                    Icons.Filled.Accessibility,
                    "Simple mode",
                    "Just 4 big buttons: I'm OK, Call, Medicine, Help. Spoken aloud. Hold the lock icon to leave.",
                    s.simpleMode,
                    vm::setSimpleMode,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("Language for simple mode and voice", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SimpleLanguage.entries.forEachIndexed { index, language ->
                            SegmentedButton(
                                selected = s.simpleLanguage == language,
                                onClick = {
                                    vm.setSimpleLanguage(language)
                                    if (s.speakAloud) context.appContainer.speaker.speak(SimpleText(language).ok, language.localeTag)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, SimpleLanguage.entries.size),
                            ) { Text(language.label) }
                        }
                    }
                }
            }
            item {
                SettingRow(
                    Icons.Filled.Call,
                    "“Call” button",
                    if (s.simpleCallNumber.isBlank()) {
                        "Not set: uses the first family member with a phone number"
                    } else {
                        "${s.simpleCallName.ifBlank { "Family" }} · ${s.simpleCallNumber}"
                    },
                    onClick = { dialog = SettingsDialog.SIMPLE_CONTACT },
                )
            }
            item {
                SwitchRow(
                    Icons.Filled.RecordVoiceOver,
                    "Speak aloud",
                    "Read buttons and medicine reminders out loud, for parents who don't read easily",
                    s.speakAloud,
                    vm::setSpeakAloud,
                )
            }

            item { Group("Safety") }
            item {
                SwitchRow(
                    Icons.Filled.Vibration,
                    "Shake to send SOS",
                    "Shake the phone hard 3 times. You get 10 seconds to cancel before your family is alerted.",
                    s.shakeToSos,
                    vm::setShakeToSos,
                )
            }
            item {
                SwitchRow(
                    Icons.Filled.PersonalInjury,
                    "Fall detection",
                    "After a hard fall, the phone asks “Are you OK?”. No answer in 60 seconds sends an SOS.",
                    s.fallDetection,
                    vm::setFallDetection,
                )
            }
            item {
                SwitchRow(
                    Icons.Filled.WhereToVote,
                    "Automatic arrival check-in",
                    "Tell family “Reached Home 🏠” when you arrive at one of your saved places",
                    s.autoArrivalCheckIn,
                    vm::setAutoArrivalCheckIn,
                )
            }
            item {
                SwitchRow(
                    Icons.Filled.BatteryChargingFull,
                    "Remind me to charge",
                    "A reminder at 20% battery, so family keeps getting your location",
                    s.chargeReminder,
                    vm::setChargeReminder,
                )
            }
            item {
                SettingRow(
                    Icons.Filled.Bolt,
                    "Keep the app running in the background",
                    if (Reliability.isIgnoringBatteryOptimizations(context)) {
                        "Battery optimisation is off for this app. Good."
                    } else {
                        "Some phones stop the app to save battery. Tap for steps for ${Reliability.brand()}."
                    },
                    onClick = { dialog = SettingsDialog.RELIABILITY },
                )
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
                SwitchRow(
                    Icons.Filled.Timeline,
                    "Share today's trail",
                    "Family can see where you've been in the last 24 hours",
                    s.shareTrail,
                    vm::setShareTrail,
                )
            }
            item {
                SwitchRow(
                    Icons.Filled.BatterySaver,
                    "Smart battery saver",
                    "Below ${BatterySaver.LOW_PERCENT}%, update every ${BatterySaver.SAVER_INTERVAL_MINUTES} min and send a last location before the phone dies",
                    s.batterySaver,
                    vm::setBatterySaver,
                )
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

            item { Group("Medical ID") }
            item {
                SettingRow(
                    icon = Icons.Filled.LocalHospital,
                    title = "My Medical ID",
                    subtitle = if (s.medical.isEmpty) {
                        "Not added. Shared with your family and included in SOS texts"
                    } else {
                        s.medical.summary()
                    },
                    onClick = { dialog = SettingsDialog.MEDICAL },
                    trailing = if (s.medical.isEmpty) {
                        null
                    } else {
                        {
                            IconButton(onClick = { Intents.shareText(context, medicalShareText(s.userName, s.medical), "Share Medical ID") }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Medical ID")
                            }
                        }
                    },
                )
            }

            item { Group("Reminders") }
            items(s.reminders, key = { "reminder-" + it.id }) { reminder ->
                ListItem(
                    headlineContent = {
                        Text(
                            if (reminder.type == ReminderType.MEDICINE) "💊 ${reminder.label}" else reminder.label,
                        )
                    },
                    supportingContent = {
                        val stock = reminder.stock
                        Text(
                            "Every day at ${ReminderTimes.format(reminder.hour, reminder.minute)}" +
                                when {
                                    reminder.type != ReminderType.MEDICINE -> " · one-tap “I'm OK”"
                                    stock != null -> " · $stock left · tap to update"
                                    else -> " · “Taken” tells your family · tap to track tablets"
                                },
                            color = if (stock != null && stock <= ActionReceiver.REFILL_AT) StatusColors.warning else Color.Unspecified,
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                    modifier = if (reminder.type == ReminderType.MEDICINE) Modifier.clickable { stockFor = reminder } else Modifier,
                    trailingContent = {
                        Row {
                            Switch(checked = reminder.enabled, onCheckedChange = { vm.setReminderEnabled(reminder.id, it) })
                            IconButton(onClick = { vm.removeReminder(reminder.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete reminder")
                            }
                        }
                    },
                )
            }
            if (s.reminders.isNotEmpty() && !perms.exactAlarms) {
                item {
                    SettingRow(
                        icon = Icons.Filled.Alarm,
                        title = "Allow on-time reminders",
                        subtitle = "Off: reminders may arrive up to 10 min late. Tap and allow “Alarms & reminders”.",
                        onClick = { Intents.exactAlarmSettings(context) },
                    )
                }
            }
            item {
                SettingRow(
                    icon = Icons.Filled.AddAlarm,
                    title = "Add reminder",
                    subtitle = "Medicine times or a daily “I'm OK” check-in",
                    onClick = { dialog = SettingsDialog.REMINDER },
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
            items(s.curfews, key = { "curfew-" + it.id }) { rule ->
                val place = s.places.firstOrNull { it.id == rule.placeId }
                val who = rule.uid?.let { uid -> members.firstOrNull { it.uid == uid }?.displayName ?: "Someone" } ?: "Anyone"
                SettingRow(
                    icon = Icons.Filled.AccessAlarm,
                    title = "$who at ${place?.name ?: "a removed place"} by ${ReminderTimes.format(rule.hour, rule.minute)}",
                    subtitle = "Alert if they aren't there yet",
                    trailing = {
                        IconButton(onClick = { vm.removeCurfew(rule.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove rule") }
                    },
                )
            }
            if (s.places.isNotEmpty()) {
                item {
                    SettingRow(
                        icon = Icons.Filled.AccessAlarm,
                        title = "Add an “expected by” alert",
                        subtitle = "e.g. alert me if Mom isn't Home by 8:00 PM",
                        onClick = { dialog = SettingsDialog.CURFEW },
                    )
                }
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
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Icon(Icons.Filled.SignalWifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("No-update alert", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "When someone's phone stops updating (switched off, no battery or no internet)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        UserSettings.OFFLINE_ALERT_CHOICES.forEachIndexed { index, hours ->
                            SegmentedButton(
                                selected = s.offlineAlertHours == hours,
                                onClick = { vm.setOfflineAlertHours(hours) },
                                shape = SegmentedButtonDefaults.itemShape(index, UserSettings.OFFLINE_ALERT_CHOICES.size),
                            ) { Text(if (hours == 0) "Off" else "$hours h") }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("Fast driving alert", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "When someone is moving faster than this",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        UserSettings.SPEED_ALERT_CHOICES.forEachIndexed { index, kmh ->
                            SegmentedButton(
                                selected = s.speedAlertKmh == kmh,
                                onClick = { vm.setSpeedAlertKmh(kmh) },
                                shape = SegmentedButtonDefaults.itemShape(index, UserSettings.SPEED_ALERT_CHOICES.size),
                            ) { Text(if (kmh == 0) "Off" else "$kmh") }
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    Icons.Filled.Bedtime,
                    "Quiet hours",
                    "${QuietHours.format(s.quietStartMinute)} – ${QuietHours.format(s.quietEndMinute)}: arrivals and battery alerts arrive silently. SOS always rings.",
                    s.quietHoursEnabled,
                ) { vm.setQuietHours(it, s.quietStartMinute, s.quietEndMinute) }
            }
            if (s.quietHoursEnabled) {
                item {
                    SettingRow(Icons.Filled.Schedule, "Change quiet hours", null, onClick = { dialog = SettingsDialog.QUIET_HOURS })
                }
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
        SettingsDialog.MEDICAL -> MedicalEditDialog(
            initial = s.medical,
            onDismiss = { dialog = null },
            onSave = {
                vm.setMedical(it)
                dialog = null
            },
        )
        SettingsDialog.REMINDER -> AddReminderDialog(
            onDismiss = { dialog = null },
            onSave = { label, hour, minute, type, stock ->
                vm.addReminder(label, hour, minute, type, stock)
                dialog = null
            },
        )
        SettingsDialog.SIMPLE_CONTACT -> SimpleContactDialog(
            initialName = s.simpleCallName,
            initialNumber = s.simpleCallNumber,
            onDismiss = { dialog = null },
            onSave = { name, number ->
                vm.setSimpleCallContact(name, number)
                dialog = null
            },
        )
        SettingsDialog.QUIET_HOURS -> QuietHoursDialog(
            start = s.quietStartMinute,
            end = s.quietEndMinute,
            onDismiss = { dialog = null },
            onSave = { start, end ->
                vm.setQuietHours(true, start, end)
                dialog = null
            },
        )
        SettingsDialog.CURFEW -> CurfewDialog(
            places = s.places,
            members = members.filterNot { it.isDemo },
            onDismiss = { dialog = null },
            onSave = { placeId, uid, hour, minute ->
                vm.addCurfew(placeId, uid, hour, minute)
                dialog = null
            },
        )
        SettingsDialog.RELIABILITY -> ReliabilityDialog(onDismiss = { dialog = null })
        SettingsDialog.PLACE -> AddPlaceDialog(
            onDismiss = { dialog = null },
            onConfirm = { name, radius ->
                vm.addPlaceHere(name, radius)
                dialog = null
            },
        )
        null -> Unit
    }

    stockFor?.let { reminder ->
        TextInputDialog(
            title = "Tablets of ${reminder.label} left",
            label = "Number of tablets (leave empty to stop counting)",
            initial = reminder.stock?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            allowEmpty = true,
            onDismiss = { stockFor = null },
            onConfirm = {
                vm.setReminderStock(reminder.id, it.toIntOrNull()?.coerceIn(0, 9_999))
                stockFor = null
            },
        )
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
    allowEmpty: Boolean = false,
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
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = allowEmpty || value.isNotBlank()) { Text(confirmLabel) } },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicalEditDialog(initial: MedicalInfo, onDismiss: () -> Unit, onSave: (MedicalInfo) -> Unit) {
    var info by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My Medical ID") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Shared only with people who can see your location, and added to your SOS texts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Blood group", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BLOOD_GROUPS.forEach { group ->
                        FilterChip(
                            selected = info.bloodGroup == group,
                            onClick = { info = info.copy(bloodGroup = if (info.bloodGroup == group) "" else group) },
                            label = { Text(group) },
                        )
                    }
                }
                MedicalField("Conditions", "e.g. diabetes, BP", info.conditions) { info = info.copy(conditions = it) }
                MedicalField("Allergies", "e.g. penicillin", info.allergies) { info = info.copy(allergies = it) }
                MedicalField("Medicines", "e.g. Metformin 500 mg", info.medicines) { info = info.copy(medicines = it) }
                MedicalField("Doctor's name", "", info.doctorName) { info = info.copy(doctorName = it) }
                OutlinedTextField(
                    value = info.doctorPhone,
                    onValueChange = { info = info.copy(doctorPhone = it) },
                    label = { Text("Doctor's phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(info.trimmed()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun MedicalInfo.trimmed() = MedicalInfo(
    bloodGroup.trim(), conditions.trim(), allergies.trim(), medicines.trim(), doctorName.trim(), doctorPhone.trim(),
)

@Composable
private fun MedicalField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint.isNotEmpty()) { { Text(hint) } } else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, ReminderType, Int?) -> Unit) {
    var type by remember { mutableStateOf(ReminderType.MEDICINE) }
    var label by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    val time = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = false)
    val finalLabel = label.trim().ifEmpty { if (type == ReminderType.CHECK_IN) "Morning check-in" else "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == ReminderType.MEDICINE, onClick = { type = ReminderType.MEDICINE }, label = { Text("💊 Medicine") })
                    FilterChip(selected = type == ReminderType.CHECK_IN, onClick = { type = ReminderType.CHECK_IN }, label = { Text("👍 Daily check-in") })
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(if (type == ReminderType.MEDICINE) "Medicine name" else "Name (optional)") },
                    placeholder = { Text(if (type == ReminderType.MEDICINE) "e.g. BP tablet" else "Morning check-in") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeInput(state = time)
                if (type == ReminderType.MEDICINE) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { v -> stock = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Tablets left (optional)") },
                        supportingText = { Text("Counts down with each “Taken” and warns at ${ActionReceiver.REFILL_AT}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    if (type == ReminderType.MEDICINE) {
                        "Tapping “Taken” on the reminder lets your family know. If it isn't tapped within 45 minutes, you're reminded again and your family is told."
                    } else {
                        "One tap on the reminder tells your family you're OK."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(finalLabel, time.hour, time.minute, type, if (type == ReminderType.MEDICINE) stock.toIntOrNull() else null) },
                enabled = finalLabel.isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
