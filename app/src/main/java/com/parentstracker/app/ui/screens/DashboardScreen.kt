@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.parentstracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.data.CHECK_IN_MESSAGES
import com.parentstracker.app.data.LocationSnapshot
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.PingResponse
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.Banner
import com.parentstracker.app.ui.components.BannerButton
import com.parentstracker.app.ui.components.BatteryIndicator
import com.parentstracker.app.ui.components.IncomingPingBanner
import com.parentstracker.app.ui.components.InfoRow
import com.parentstracker.app.ui.components.MedicalInfoDialog
import com.parentstracker.app.ui.components.NearbyHelpRow
import com.parentstracker.app.ui.components.TrailSheet
import com.parentstracker.app.ui.components.MemberCard
import com.parentstracker.app.ui.components.OnSos
import com.parentstracker.app.ui.components.SectionHeader
import com.parentstracker.app.ui.components.SharingStatusBanner
import com.parentstracker.app.ui.components.rememberNow
import com.parentstracker.app.ui.rememberPermissionSnapshot
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.Perms
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenMap: (String?) -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val members by vm.members.collectAsStateWithLifecycle()
    val viewers by vm.viewers.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val incomingPings by vm.incomingPings.collectAsStateWithLifecycle()
    val outgoingPings by vm.outgoingPings.collectAsStateWithLifecycle()
    val sosRequested by vm.sosRequested.collectAsStateWithLifecycle()
    val perms = rememberPermissionSnapshot()
    val context = LocalContext.current
    val now = rememberNow()
    var showSosDialog by rememberSaveable { mutableStateOf(false) }
    var trailFor by remember { mutableStateOf<Member?>(null) }
    var medicalFor by remember { mutableStateOf<Member?>(null) }

    // The home-screen widget's SOS button lands here: confirm before sending.
    LaunchedEffect(sosRequested) {
        if (sosRequested) {
            showSosDialog = true
            vm.consumeSosRequest()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }

    LaunchedEffect(perms.location) { if (perms.location) vm.refreshIfStale() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hi, ${s.userName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Here's where everyone is",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.refreshLocation() }) { Icon(Icons.Filled.Refresh, "Refresh my location") }
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fun LazyListScope.familySection() {
                item(key = "family-header") {
                    SectionHeader("Family (${members.size})") {
                        TextButton(onClick = onOpenPeople) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
                if (members.isEmpty()) {
                    item(key = "empty") { EmptyFamilyCard(onOpenPeople) }
                }
                items(members, key = { it.uid }) { member ->
                    val location = member.location
                    MemberCard(
                        member = member,
                        me = s.lastLocation,
                        places = s.places,
                        now = now,
                        onCall = member.phone?.let { phone -> { Intents.dial(context, phone) } },
                        onDirections = location?.let { l -> { Intents.directions(context, l.lat, l.lng) } },
                        onShowOnMap = if (location != null) { { onOpenMap(member.uid) } } else null,
                        waitingPingAt = outgoingPings.firstOrNull { it.to == member.uid }?.createdAt,
                        onPing = { vm.sendPing(member) },
                        onTrail = { trailFor = member },
                        onMedical = { medicalFor = member },
                    )
                }
            }

            // Emergencies first.
            items(members.filter { it.sos }, key = { "sos-" + it.uid }) { member ->
                val near = member.location?.address?.let { " · near $it" }.orEmpty()
                Banner(
                    icon = Icons.Filled.Warning,
                    title = "SOS from ${member.displayName}",
                    message = "Sent ${Geo.timeAgo(member.sosAt, now)}$near",
                    containerColor = StatusColors.sos,
                    contentColor = OnSos,
                ) {
                    member.phone?.let { phone -> BannerButton("Call") { Intents.dial(context, phone) } }
                    member.location?.let { l ->
                        BannerButton("Hospitals") { Intents.searchNearby(context, "hospital", l.lat, l.lng) }
                        BannerButton("Directions") { Intents.directions(context, l.lat, l.lng) }
                    }
                }
            }
            items(incomingPings, key = { "ping-" + it.id }) { ping ->
                IncomingPingBanner(
                    ping = ping,
                    now = now,
                    onOk = { vm.answerPing(ping, PingResponse.OK) },
                    onCallMe = { vm.answerPing(ping, PingResponse.CALL_ME) },
                )
            }
            if (s.sosActive) {
                item(key = "my-sos") {
                    Banner(
                        icon = Icons.Filled.Warning,
                        title = "Your SOS is on",
                        message = if (vm.cloudConfigured && viewers.isNotEmpty()) {
                            "Your family has been alerted with your location."
                        } else {
                            "Call for help or text your emergency contacts."
                        },
                        containerColor = StatusColors.sos,
                        contentColor = OnSos,
                    ) {
                        BannerButton("Call ${s.emergencyDialNumber}") { Intents.dial(context, s.emergencyDialNumber) }
                        BannerButton("I'm safe") { vm.cancelSos() }
                    }
                }
            }

            // Setup problems.
            if (!perms.location) {
                item(key = "perm") {
                    Banner(
                        icon = Icons.Filled.LocationOff,
                        title = "Location permission needed",
                        message = "Allow location so your family can see where you are, and so you can see how far away they are.",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        BannerButton("Allow") { permissionLauncher.launch(Perms.foregroundRequest) }
                    }
                }
            } else if (!perms.notifications) {
                item(key = "notifications") {
                    Banner(
                        icon = Icons.Filled.NotificationsOff,
                        title = "Notifications are off",
                        message = "Turn them on to get SOS, arrival and low-battery alerts.",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        BannerButton("Turn on") { permissionLauncher.launch(Perms.foregroundRequest) }
                    }
                }
            }

            // Who can see me — always visible so sharing is never silent.
            if (vm.cloudConfigured) {
                item(key = "sharing") {
                    SharingStatusBanner(
                        viewers = viewers,
                        sharingEnabled = s.sharingEnabled,
                        onPause = { vm.setSharingEnabled(false) },
                        onResume = { vm.setSharingEnabled(true) },
                        onManage = onOpenPeople,
                    )
                }
            } else {
                item(key = "cloud") {
                    Banner(
                        icon = Icons.Filled.CloudOff,
                        title = "Family sync isn't set up yet",
                        message = "Your location, map, places and SOS work now. Connecting family phones needs Firebase (see README).",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        BannerButton(if (s.demoMode) "Hide demo family" else "Try demo family") { vm.setDemoMode(!s.demoMode) }
                    }
                }
            }

            // Family first when there is someone to watch over; otherwise lead with this phone's own status.
            val familyFirst = members.isNotEmpty()
            if (familyFirst) familySection()
            item(key = "me") {
                MyLocationCard(
                    location = s.lastLocation,
                    places = s.places,
                    now = now,
                    hasPermission = perms.location,
                    refreshing = refreshing,
                    onRefresh = { vm.refreshLocation() },
                    onGrant = { permissionLauncher.launch(Perms.foregroundRequest) },
                    onShare = { l ->
                        val where = l.address?.let { "$it\n" }.orEmpty()
                        Intents.shareText(context, "📍 ${s.userName}'s location:\n$where${Geo.mapsLink(l.lat, l.lng)}", "Share my location")
                    },
                    onOpenMap = { onOpenMap(null) },
                )
            }

            item(key = "safety") {
                SafetyCard(
                    settings = s,
                    now = now,
                    onCheckIn = vm::checkIn,
                    onSos = { showSosDialog = true },
                    onOpenSettings = onOpenSettings,
                )
            }
            if (!familyFirst) familySection()
        }
    }

    trailFor?.let { member -> TrailSheet(member, s.places, now, onDismiss = { trailFor = null }) }
    medicalFor?.let { member ->
        member.medical?.let { info -> MedicalInfoDialog(member.displayName, info, onDismiss = { medicalFor = null }) }
    }

    if (showSosDialog) {
        SosDialog(
            settings = s,
            alertsFamily = vm.cloudConfigured && viewers.isNotEmpty(),
            onDismiss = { showSosDialog = false },
            onCall = { Intents.dial(context, s.emergencyDialNumber) },
            onSend = {
                showSosDialog = false
                vm.triggerSos()
                if (s.emergencyNumbers.isNotEmpty()) Intents.sms(context, s.emergencyNumbers, sosMessage(s))
            },
        )
    }
}

private fun sosMessage(s: UserSettings): String = buildString {
    append("🚨 SOS from ${s.userName}: I need help.")
    s.lastLocation?.let { l ->
        append("\nMy last location")
        l.address?.let { append(": $it") }
        append("\n${Geo.mapsLink(l.lat, l.lng)}")
    }
    if (!s.medical.isEmpty) append("\nMedical ID: ${s.medical.summary()}")
    append("\n(sent from Parents Tracker)")
}

@Composable
private fun MyLocationCard(
    location: LocationSnapshot?,
    places: List<SavedPlace>,
    now: Long,
    hasPermission: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onGrant: () -> Unit,
    onShare: (LocationSnapshot) -> Unit,
    onOpenMap: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "My location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (location != null) {
                    Text(
                        Geo.timeAgo(location.timeMillis, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (location == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !hasPermission -> "Allow location to see where you are."
                        refreshing -> "Finding you…"
                        else -> "We don't have your location yet. Make sure Location (GPS) is on, then refresh."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (hasPermission) {
                    OutlinedButton(onClick = onRefresh, enabled = !refreshing) { Text("Refresh") }
                } else {
                    Button(onClick = onGrant) { Text("Allow location") }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(location.address ?: "Address not available", style = MaterialTheme.typography.bodyLarge)
                Geo.placeFor(location.lat, location.lng, places)?.let { InfoRow(Icons.Filled.Home, "You're at ${it.name}") }
                InfoRow(
                    Icons.Filled.GpsFixed,
                    String.format(Locale.US, "%.5f, %.5f · ±%d m", location.lat, location.lng, location.accuracy.roundToInt()),
                )
                BatteryIndicator(location.battery, location.charging, Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Filled.Refresh, "Refresh", onRefresh, enabled = !refreshing, modifier = Modifier.weight(1f))
                    SmallAction(Icons.Filled.Share, "Share", { onShare(location) }, modifier = Modifier.weight(1f))
                    SmallAction(Icons.Filled.Map, "Map", onOpenMap, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun SafetyCard(
    settings: UserSettings,
    now: Long,
    onCheckIn: (String) -> Unit,
    onSos: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Quick check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Let your family know how you are with one tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CHECK_IN_MESSAGES.forEach { message ->
                    AssistChip(onClick = { onCheckIn(message) }, label = { Text(message) })
                }
            }
            if (settings.checkInMessage != null && settings.checkInAt > 0) {
                Text(
                    "Last check-in: ${settings.checkInMessage} · ${Geo.timeAgo(settings.checkInAt, now)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSos,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusColors.sos, contentColor = OnSos),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (settings.sosActive) "SOS is on" else "SOS — I need help",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (settings.emergencyNumbers.isEmpty()) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Add emergency SMS contacts")
                }
            }
            NearbyHelpRow(settings.lastLocation?.lat, settings.lastLocation?.lng, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun EmptyFamilyCard(onOpenPeople: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("No family members yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Invite Mom, Dad or anyone you care about. They'll see your request and choose whether to share.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenPeople) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add family")
            }
        }
    }
}

@Composable
private fun SosDialog(
    settings: UserSettings,
    alertsFamily: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onSend: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusColors.sos) },
        title = { Text("Send SOS?") },
        text = {
            Text(
                buildString {
                    append("This will:")
                    if (alertsFamily) append("\n• Alert everyone who can see you, with your location")
                    if (settings.emergencyNumbers.isNotEmpty()) {
                        append("\n• Open a text message with your location to ${settings.emergencyNumbers.size} emergency contact(s)")
                    } else {
                        append("\n• Show an SOS banner (add emergency SMS contacts in Settings to text them too)")
                    }
                    append("\n\nIn danger? Call ${settings.emergencyDialNumber} right away.")
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = StatusColors.sos, contentColor = OnSos),
            ) { Text("Send SOS") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCall) { Text("Call ${settings.emergencyDialNumber}") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
