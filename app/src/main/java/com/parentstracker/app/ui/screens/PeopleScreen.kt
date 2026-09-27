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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.data.CloudStatus
import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.InviteStatus
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.data.ShareDirection
import com.parentstracker.app.data.Viewer
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.Avatar
import com.parentstracker.app.ui.components.Banner
import com.parentstracker.app.ui.components.BannerButton
import com.parentstracker.app.ui.components.RelationChips
import com.parentstracker.app.ui.components.SectionHeader
import com.parentstracker.app.ui.components.memberStatus
import com.parentstracker.app.ui.components.rememberNow
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.InviteCodes

@Composable
fun PeopleScreen(vm: AppViewModel, onJoin: () -> Unit, onShowOnMap: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val members by vm.members.collectAsStateWithLifecycle()
    val viewers by vm.viewers.collectAsStateWithLifecycle()
    val invites by vm.invites.collectAsStateWithLifecycle()
    val cloudStatus by vm.cloudStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = rememberNow()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmStopSharing by remember { mutableStateOf<Viewer?>(null) }
    var confirmStopViewing by remember { mutableStateOf<Member?>(null) }
    var editing by remember { mutableStateOf<Member?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                actions = {
                    TextButton(onClick = onJoin) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enter code")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Add person") },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val status = cloudStatus
            if (!vm.cloudConfigured) {
                item(key = "cloud") {
                    Banner(
                        icon = Icons.Filled.CloudOff,
                        title = "Family sync isn't set up yet",
                        message = "Inviting people on other phones needs Firebase (see “Family sync” in the README). Meanwhile you can preview everything with a demo family.",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        BannerButton(if (s.demoMode) "Hide demo family" else "Show demo family") { vm.setDemoMode(!s.demoMode) }
                    }
                }
            } else if (status is CloudStatus.Error) {
                item(key = "cloud-error") {
                    Banner(
                        icon = Icons.Filled.CloudOff,
                        title = "Family sync problem",
                        message = status.message,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            item(key = "h-members") { SectionHeader("People you can see (${members.size})") }
            if (members.isEmpty()) {
                item(key = "no-members") {
                    Hint("No one yet. Tap “Add person” and choose “See their location”. They'll get a code and decide whether to share.")
                }
            }
            items(members, key = { "m-" + it.uid }) { member ->
                MemberRow(
                    member = member,
                    places = s.places,
                    now = now,
                    onMap = if (member.location != null) { { onShowOnMap(member.uid) } } else null,
                    onCall = member.phone?.let { phone -> { Intents.dial(context, phone) } },
                    onEdit = if (member.isDemo) null else { { editing = member } },
                    onRemove = { confirmStopViewing = member },
                    onPing = { vm.sendPing(member) },
                )
            }

            item(key = "h-viewers") { SectionHeader("Who can see you (${viewers.size})") }
            if (viewers.isEmpty()) {
                item(key = "no-viewers") { Hint("Nobody can see your location. 🔒") }
            }
            items(viewers, key = { "v-" + it.uid }) { viewer ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(viewer.label) },
                        supportingContent = {
                            Text(if (s.sharingEnabled) "Can see your live location" else "Sharing paused, can't see updates")
                        },
                        leadingContent = { Avatar(viewer.name, viewer.uid, 40.dp) },
                        trailingContent = { TextButton(onClick = { confirmStopSharing = viewer }) { Text("Stop") } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            if (invites.isNotEmpty()) {
                item(key = "h-invites") { SectionHeader("Invites you sent") }
                items(invites, key = { "i-" + it.code }) { invite ->
                    InviteCard(
                        invite = invite,
                        now = now,
                        onShare = { Intents.shareText(context, InviteCodes.message(invite), "Send invite") },
                        onRemove = { vm.cancelInvite(invite.code) },
                    )
                }
            }

            item(key = "join") {
                Spacer(Modifier.height(8.dp))
                OutlinedCard(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Got an invite code?", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Enter the code someone sent you. You'll see what they're asking for before anything is shared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }

    confirmStopSharing?.let { viewer ->
        AlertDialog(
            onDismissRequest = { confirmStopSharing = null },
            title = { Text("Stop sharing with ${viewer.name}?") },
            text = { Text("${viewer.name} won't see your location anymore. To share again, they'll need to send a new invite.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopSharingWith(viewer)
                    confirmStopSharing = null
                }) { Text("Stop sharing") }
            },
            dismissButton = { TextButton(onClick = { confirmStopSharing = null }) { Text("Cancel") } },
        )
    }

    confirmStopViewing?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmStopViewing = null },
            title = { Text(if (member.isDemo) "Hide the demo family?" else "Stop seeing ${member.displayName}?") },
            text = {
                Text(
                    if (member.isDemo) {
                        "The pretend family members will be removed. You can turn them back on in Settings."
                    } else {
                        "You'll be removed from ${member.displayName}'s sharing list. They can invite you again later."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopViewing(member)
                    confirmStopViewing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmStopViewing = null }) { Text("Cancel") } },
        )
    }

    editing?.let { member ->
        EditLabelDialog(
            member = member,
            onDismiss = { editing = null },
            onSave = { nickname, relation, phone ->
                vm.updateLabel(member.uid, nickname, relation, phone)
                editing = null
            },
        )
    }

    if (showAdd) AddPersonSheet(vm = vm, onDismiss = { showAdd = false })
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun MemberRow(
    member: Member,
    places: List<SavedPlace>,
    now: Long,
    onMap: (() -> Unit)?,
    onCall: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
    onPing: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (status, warn) = memberStatus(member, places, now)
    OutlinedCard(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(member.displayName + (member.relation?.takeIf { it != member.displayName }?.let { " · $it" } ?: ""))
            },
            supportingContent = {
                Text(status, color = if (warn) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingContent = { Avatar(member.displayName, member.uid, 40.dp) },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ask if they're OK") },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onPing()
                            },
                        )
                        onMap?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Show on map") },
                                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                        onCall?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Call") },
                                leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                        onEdit?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Edit name, relation, phone") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (member.isDemo) "Hide demo family" else "Stop seeing their location") },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private fun directionText(direction: ShareDirection) = when (direction) {
    ShareDirection.VIEW_THEM -> "You'll see their location"
    ShareDirection.SHARE_MINE -> "They'll see your location"
    ShareDirection.MUTUAL -> "You'll see each other"
}

@Composable
private fun InviteCard(invite: Invite, now: Long, onShare: () -> Unit, onRemove: () -> Unit) {
    val expired = invite.isExpired(now)
    val pending = invite.status == InviteStatus.PENDING && !expired
    val statusText = when {
        invite.status == InviteStatus.DECLINED -> "Declined by ${invite.acceptedName ?: invite.inviteeName}"
        expired -> "Expired. Create a new invite if you still need one."
        invite.status == InviteStatus.ACCEPTED -> "Accepted, connecting…"
        else -> "Waiting for them to accept · expires in ${((invite.expiresAt - now) / 3_600_000).coerceAtLeast(1)} h"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "For ${invite.inviteeName}" + invite.relation.takeIf { it.isNotBlank() && it != invite.inviteeName }?.let { " ($it)" }.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(directionText(invite.direction), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    invite.code,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else StatusColors.warning,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) { Text(if (pending) "Cancel invite" else "Remove") }
                if (pending) TextButton(onClick = onShare) { Text("Send again") }
            }
        }
    }
}

@Composable
private fun EditLabelDialog(member: Member, onDismiss: () -> Unit, onSave: (String, String?, String) -> Unit) {
    var nickname by remember { mutableStateOf(member.nickname ?: member.name) }
    var relation by remember { mutableStateOf(member.relation) }
    var phone by remember { mutableStateOf(member.phone.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${member.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Name shown to you") },
                    singleLine = true,
                )
                Text("Relation", style = MaterialTheme.typography.labelLarge)
                RelationChips(selected = relation, onSelect = { relation = if (relation == it) null else it })
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (for the Call button)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Text(
                    "Only saved on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(nickname, relation, phone) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
