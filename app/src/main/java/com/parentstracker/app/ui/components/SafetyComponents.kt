@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.parentstracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import com.parentstracker.app.ui.screens.medicalShareText
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parentstracker.app.data.MedicalInfo
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.Ping
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.Trail
import androidx.compose.foundation.background

/** "Find nearby help" chips: open Google Maps searching around a point. No API key needed. */
@Composable
fun NearbyHelpRow(lat: Double?, lng: Double?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Text("Find nearby help", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Hospital", "hospital", Icons.Filled.LocalHospital),
                Triple("Police", "police station", Icons.Filled.LocalPolice),
                Triple("Pharmacy", "pharmacy", Icons.Filled.LocalPharmacy),
            ).forEach { (label, query, icon) ->
                AssistChip(
                    onClick = { Intents.searchNearby(context, query, lat, lng) },
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}

/** Shown on the phone of someone who was asked "Are you OK?". */
@Composable
fun IncomingPingBanner(ping: Ping, now: Long, onOk: () -> Unit, onCallMe: () -> Unit) {
    Banner(
        icon = Icons.Filled.NotificationsActive,
        title = "${ping.fromName} is asking: are you OK?",
        message = "Asked ${Geo.timeAgo(ping.createdAt, now)}. Your answer is shared as a check-in.",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        BannerButton("Please call me", onCallMe)
        BannerButton("I'm OK 👍", onOk)
    }
}

/** Timeline of where someone has been over the last 24 hours. */
@Composable
fun TrailSheet(member: Member, places: List<SavedPlace>, now: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val stops = Trail.stops(member.trail)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("${member.displayName}'s day", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Places over the last 24 hours, oldest first",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            stops.forEachIndexed { index, stop ->
                val isLast = index == stops.lastIndex
                val place = Geo.placeFor(stop.lat, stop.lng, places)
                val until = if (isLast) "now" else Trail.clock(stop.to)
                val minutes = ((if (isLast) now else stop.to) - stop.from) / 60_000
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isLast) StatusColors.success else avatarColor(member.uid)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            place?.name ?: stop.address ?: "${Geo.coord(stop.lat)}, ${Geo.coord(stop.lng)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            when {
                                !isLast && stop.from == stop.to -> Trail.clock(stop.from)
                                minutes >= 5 -> "${Trail.clock(stop.from)} – $until · ${formatDuration(minutes)}"
                                else -> "${Trail.clock(stop.from)} – $until"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Trail.routeLink(stops)?.let { link ->
                OutlinedButton(onClick = { Intents.openUrl(context, link) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Replay route in Google Maps")
                }
            }
        }
    }
}

private fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** A family member's emergency details, with a one-tap call to their doctor. */
@Composable
fun MedicalInfoDialog(name: String, info: MedicalInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = StatusColors.sos) },
        title = { Text("$name's Medical ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MedicalLine("Blood group", info.bloodGroup)
                MedicalLine("Conditions", info.conditions)
                MedicalLine("Allergies", info.allergies)
                MedicalLine("Medicines", info.medicines)
                MedicalLine("Doctor", listOf(info.doctorName, info.doctorPhone).filter { it.isNotBlank() }.joinToString(" · "))
            }
        },
        confirmButton = {
            if (info.doctorPhone.isNotBlank()) {
                Button(onClick = { Intents.dial(context, info.doctorPhone) }) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call doctor")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Intents.shareText(context, medicalShareText(name, info), "Share Medical ID") }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun MedicalLine(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}
