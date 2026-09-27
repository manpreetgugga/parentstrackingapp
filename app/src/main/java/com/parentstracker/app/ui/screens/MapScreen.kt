@file:OptIn(ExperimentalMaterial3Api::class)

package com.parentstracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.parentstracker.app.data.DemoData
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.Avatar
import com.parentstracker.app.ui.components.MedicalInfoDialog
import com.parentstracker.app.ui.components.MemberCard
import com.parentstracker.app.ui.components.TrailSheet
import com.parentstracker.app.ui.components.SectionHeader
import com.parentstracker.app.ui.components.avatarColor
import com.parentstracker.app.ui.components.memberStatus
import com.parentstracker.app.ui.components.rememberNow
import com.parentstracker.app.ui.rememberPermissionSnapshot
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.Intents
import kotlinx.coroutines.launch

private const val ME = "me"

@Composable
fun MapScreen(vm: AppViewModel, focusUid: String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val members by vm.members.collectAsStateWithLifecycle()
    if (vm.mapsConfigured) {
        LiveMap(vm, s, members, focusUid)
    } else {
        MapSetupPlaceholder(s, members)
    }
}

/** Shown until a Google Maps API key is added: setup steps plus "open in Google Maps" links (no key needed). */
@Composable
private fun MapSetupPlaceholder(s: UserSettings, members: List<Member>) {
    val context = LocalContext.current
    val now = rememberNow()
    Scaffold(topBar = { TopAppBar(title = { Text("Map") }) }) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("The live family map is ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "It switches on as soon as a Google Maps API key is added to the app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        listOf(
                            "In Google Cloud Console, enable “Maps SDK for Android”.",
                            "Create an API key and restrict it to Android app com.parentstracker.app.",
                            "Add MAPS_API_KEY=your_key to local.properties.",
                            "Rebuild and install the app.",
                        ).forEachIndexed { index, step ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Open in Google Maps") }
            item {
                Text(
                    "Meanwhile, tap anyone to see them in the Google Maps app. No key is needed for that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.lastLocation?.let { me ->
                item(key = ME) {
                    OpenInMapsRow(
                        name = "You",
                        seed = ME,
                        subtitle = me.address ?: "Updated ${Geo.timeAgo(me.timeMillis, now)}",
                        onClick = { Intents.openInMaps(context, me.lat, me.lng, s.userName) },
                        avatarName = s.userName,
                    )
                }
            }
            val located = members.filter { it.location != null }
            items(located, key = { it.uid }) { member ->
                val l = member.location!!
                OpenInMapsRow(
                    name = member.displayName,
                    seed = member.uid,
                    subtitle = l.address ?: memberStatus(member, s.places, now).first,
                    onClick = { Intents.openInMaps(context, l.lat, l.lng, member.displayName) },
                )
            }
            if (s.lastLocation == null && located.isEmpty()) {
                item {
                    Text(
                        "No locations yet. Refresh your location on the Home tab or add family on the People tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenInMapsRow(name: String, seed: String, subtitle: String, onClick: () -> Unit, avatarName: String = name) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = { Text(subtitle, maxLines = 2) },
            leadingContent = { Avatar(avatarName, seed, 40.dp) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Google Maps") },
        )
    }
}

@Composable
private fun LiveMap(vm: AppViewModel, s: UserSettings, members: List<Member>, focusUid: String?) {
    val context = LocalContext.current
    val perms = rememberPermissionSnapshot()
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    val me = s.lastLocation
    val located = members.filter { it.location != null }

    fun positionOf(uid: String?): LatLng? = when (uid) {
        null -> null
        ME -> me?.let { LatLng(it.lat, it.lng) }
        else -> members.firstOrNull { it.uid == uid }?.location?.let { LatLng(it.lat, it.lng) }
    }

    val camera = rememberCameraPositionState {
        val start = positionOf(focusUid) ?: positionOf(ME) ?: located.firstOrNull()?.location?.let { LatLng(it.lat, it.lng) }
        position = if (start != null) {
            CameraPosition.fromLatLngZoom(start, 14f)
        } else {
            CameraPosition.fromLatLngZoom(LatLng(DemoData.DEFAULT_LAT, DemoData.DEFAULT_LNG), 4f)
        }
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selected by rememberSaveable(focusUid) { mutableStateOf(focusUid) }
    var trailFor by remember { mutableStateOf<Member?>(null) }
    var medicalFor by remember { mutableStateOf<Member?>(null) }

    fun fitEveryone() {
        val points = listOfNotNull(positionOf(ME)) + located.mapNotNull { m -> m.location?.let { LatLng(it.lat, it.lng) } }
        when (points.size) {
            0 -> Unit
            1 -> scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(points[0], 15f), 700) }
            else -> {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                scope.launch { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 160), 700) }
            }
        }
    }

    LaunchedEffect(mapLoaded, selected) {
        if (!mapLoaded) return@LaunchedEffect
        val target = positionOf(selected)
        if (target != null) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(target, 15f), 700)
        } else if (selected == null && focusUid == null) {
            fitEveryone()
        }
    }

    val placeColor = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = perms.location),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            contentPadding = PaddingValues(top = 96.dp, bottom = if (selected != null) 280.dp else 72.dp),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { selected = null },
        ) {
            s.places.forEach { place ->
                Circle(
                    center = LatLng(place.lat, place.lng),
                    radius = place.radiusMeters.toDouble(),
                    fillColor = placeColor.copy(alpha = 0.15f),
                    strokeColor = placeColor,
                    strokeWidth = 3f,
                )
            }
            // The selected person's path over the last 24 hours.
            members.firstOrNull { it.uid == selected }?.let { member ->
                val path = member.trail.map { LatLng(it.lat, it.lng) } +
                    listOfNotNull(member.location?.let { LatLng(it.lat, it.lng) })
                if (path.size > 1) {
                    Polyline(points = path, color = avatarColor(member.uid), width = 10f, geodesic = true)
                }
            }
            located.forEach { member ->
                val l = member.location!!
                key(member.uid) {
                    PersonMarker(
                        position = LatLng(l.lat, l.lng),
                        title = member.displayName,
                        snippet = memberStatus(member, s.places, now).first,
                        hue = hueFor(member.uid),
                        onClick = { selected = member.uid },
                    )
                }
            }
            // The blue dot shows "me" when location is allowed; otherwise show the last known spot.
            if (me != null && !perms.location) {
                PersonMarker(
                    position = LatLng(me.lat, me.lng),
                    title = "You",
                    snippet = "Updated ${Geo.timeAgo(me.timeMillis, now)}",
                    hue = BitmapDescriptorFactory.HUE_AZURE,
                    onClick = { selected = ME },
                )
            }
        }

        Surface(
            modifier = Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Family map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { selected = ME }, enabled = me != null) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Show me")
                }
                IconButton(onClick = {
                    selected = null
                    fitEveryone()
                }) {
                    Icon(Icons.Filled.ZoomOutMap, contentDescription = "Show everyone")
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp)) {
            val selectedMember = members.firstOrNull { it.uid == selected }
            if (selectedMember != null) {
                val l = selectedMember.location
                MemberCard(
                    member = selectedMember,
                    me = me,
                    places = s.places,
                    now = now,
                    onCall = selectedMember.phone?.let { phone -> { Intents.dial(context, phone) } },
                    onDirections = l?.let { { Intents.directions(context, it.lat, it.lng) } },
                    onShowOnMap = null,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onPing = { vm.sendPing(selectedMember) },
                    onTrail = { trailFor = selectedMember },
                    onMedical = { medicalFor = selectedMember },
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (me != null) {
                    item(key = ME) {
                        ElevatedFilterChip(
                            selected = selected == ME,
                            onClick = { selected = ME },
                            label = { Text("You") },
                            leadingIcon = { Avatar(s.userName, ME, 24.dp) },
                        )
                    }
                }
                items(located, key = { it.uid }) { member ->
                    ElevatedFilterChip(
                        selected = selected == member.uid,
                        onClick = { selected = member.uid },
                        label = { Text(member.displayName) },
                        leadingIcon = { Avatar(member.displayName, member.uid, 24.dp) },
                    )
                }
            }
        }
    }

    trailFor?.let { member -> TrailSheet(member, s.places, now, onDismiss = { trailFor = null }) }
    medicalFor?.let { member ->
        member.medical?.let { info -> MedicalInfoDialog(member.displayName, info, onDismiss = { medicalFor = null }) }
    }
}

@Composable
@GoogleMapComposable
private fun PersonMarker(position: LatLng, title: String, snippet: String, hue: Float, onClick: () -> Unit) {
    val state = remember { MarkerState(position = position) }
    LaunchedEffect(position) { state.position = position }
    val icon = remember(hue) { BitmapDescriptorFactory.defaultMarker(hue) }
    Marker(
        state = state,
        title = title,
        snippet = snippet,
        icon = icon,
        onClick = {
            onClick()
            false
        },
    )
}

/** Marker colour matching the person's avatar colour. */
private fun hueFor(seed: String): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(avatarColor(seed).toArgb(), hsv)
    return hsv[0]
}
