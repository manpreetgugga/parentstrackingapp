package com.parentstracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parentstracker.app.data.LocationSnapshot
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.data.Viewer
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Geo

/** One-line status for a family member, e.g. "At Home · 2 min ago". */
fun memberStatus(member: Member, places: List<SavedPlace>, now: Long): Pair<String, Boolean> {
    val location = member.location ?: return "Waiting for their first location update…" to false
    val ago = Geo.timeAgo(location.timeMillis, now)
    val stale = now - location.timeMillis > Geo.STALE_AFTER_MS
    val text = when {
        !member.sharing -> "Paused sharing · last seen $ago"
        else -> {
            val place = Geo.placeFor(location.lat, location.lng, places)
            val base = if (place != null) "At ${place.name} · $ago" else "Updated $ago"
            if (stale) "$base · phone may be off or offline" else base
        }
    }
    return text to (stale || !member.sharing)
}

@Composable
fun MemberCard(
    member: Member,
    me: LocationSnapshot?,
    places: List<SavedPlace>,
    now: Long,
    onCall: (() -> Unit)?,
    onDirections: (() -> Unit)?,
    onShowOnMap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val location = member.location
    val (status, warn) = memberStatus(member, places, now)
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(member.displayName, member.uid)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            member.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!member.relation.isNullOrBlank() && member.relation != member.displayName) {
                            Spacer(Modifier.width(6.dp))
                            Tag(member.relation)
                        }
                        if (member.isDemo) {
                            Spacer(Modifier.width(6.dp))
                            Tag("DEMO", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (warn) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BatteryIndicator(location?.battery, location?.charging == true)
            }
            location?.address?.let { InfoRow(Icons.Filled.Place, it) }
            if (location != null && me != null) {
                val distance = Geo.distanceMeters(me.lat, me.lng, location.lat, location.lng)
                InfoRow(Icons.Filled.NearMe, "${Geo.formatDistance(distance)} from you")
            }
            if (member.checkInMessage != null && member.checkInAt > 0) {
                InfoRow(
                    Icons.Filled.CheckCircle,
                    "Checked in: ${member.checkInMessage} · ${Geo.timeAgo(member.checkInAt, now)}",
                    color = StatusColors.success,
                )
            }
            if (onCall != null || onDirections != null || onShowOnMap != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onCall?.let { CardAction(Icons.Filled.Call, "Call", it, Modifier.weight(1f)) }
                    onDirections?.let { CardAction(Icons.Filled.Directions, "Route", it, Modifier.weight(1f)) }
                    onShowOnMap?.let { CardAction(Icons.Filled.Map, "Map", it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

/**
 * The transparency banner shown to the person being tracked: who can see them, and a way to pause.
 * It is shown whenever anyone has access, so sharing is never silent.
 */
@Composable
fun SharingStatusBanner(
    viewers: List<Viewer>,
    sharingEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = viewers.map { it.label }
    val who = when (names.size) {
        0 -> ""
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} more"
    }
    when {
        viewers.isEmpty() -> Banner(
            icon = Icons.Filled.Lock,
            title = "Only you can see your location",
            message = "Nobody has access right now. You'll always see a banner here if someone does.",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )

        sharingEnabled -> Banner(
            icon = Icons.Filled.Visibility,
            title = "Your location is visible to $who",
            message = "They can see where you are, your address and battery level. A notification stays on while sharing.",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier,
        ) {
            BannerButton("Manage", onManage)
            BannerButton("Pause", onPause)
        }

        else -> Banner(
            icon = Icons.Filled.PauseCircle,
            title = "Location sharing is paused",
            message = "$who can't see your new location until you resume.",
            containerColor = StatusColors.warningContainer,
            contentColor = StatusColors.onWarningContainer,
            modifier = modifier,
        ) {
            BannerButton("Manage", onManage)
            BannerButton("Resume", onResume)
        }
    }
}

/** Keeps a colour readable on top of the SOS red. */
val OnSos = Color.White
