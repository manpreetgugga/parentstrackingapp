@file:OptIn(ExperimentalMaterial3Api::class)

package com.parentstracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.data.CloudStatus
import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.InviteStatus
import com.parentstracker.app.data.ShareDirection
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.Avatar
import com.parentstracker.app.ui.components.Banner
import com.parentstracker.app.ui.components.RelationChips
import com.parentstracker.app.util.InviteCodes
import com.parentstracker.app.util.Perms
import kotlinx.coroutines.launch

@Composable
fun JoinScreen(vm: AppViewModel, initialCode: String?, onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloudStatus by vm.cloudStatus.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf(InviteCodes.normalize(initialCode.orEmpty())) }
    var invite by remember { mutableStateOf<Invite?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
        onFinished()
    }

    fun lookup() {
        if (!InviteCodes.isValid(code)) {
            error = "Codes have ${InviteCodes.LENGTH} letters and numbers"
            return
        }
        scope.launch {
            busy = true
            error = null
            vm.findInvite(code)
                .onSuccess { found ->
                    invite = found
                    if (found == null) error = "No invite found for $code. Check the code with the person who sent it."
                }
                .onFailure { error = it.message ?: "Couldn't look up the code" }
            busy = false
        }
    }

    LaunchedEffect(Unit) { if (InviteCodes.isValid(code) && vm.cloudConfigured) lookup() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (invite == null) "Enter invite code" else "Location request") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val current = invite
            if (current == null) {
                CodeEntry(
                    code = code,
                    onCodeChange = {
                        code = InviteCodes.normalize(it)
                        error = null
                    },
                    busy = busy,
                    error = error,
                    cloudConfigured = vm.cloudConfigured,
                    onSubmit = { lookup() },
                )
            } else {
                ConsentContent(
                    invite = current,
                    isOwnInvite = current.fromUid == (cloudStatus as? CloudStatus.Connected)?.uid,
                    busy = busy,
                    error = error,
                    onAccept = { relation ->
                        scope.launch {
                            busy = true
                            error = null
                            vm.acceptInvite(current, relation)
                                .onSuccess {
                                    if (current.direction.inviterSeesInvitee && !Perms.hasLocation(context)) {
                                        permissionLauncher.launch(Perms.foregroundRequest)
                                    } else {
                                        vm.onPermissionsChanged()
                                        onFinished()
                                    }
                                }
                                .onFailure { error = it.message ?: "Couldn't accept the invite" }
                            busy = false
                        }
                    },
                    onDecline = {
                        scope.launch {
                            busy = true
                            vm.declineInvite(current)
                                .onSuccess { onFinished() }
                                .onFailure { error = it.message ?: "Couldn't decline the invite" }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CodeEntry(
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    cloudConfigured: Boolean,
    onSubmit: () -> Unit,
) {
    Icon(Icons.Filled.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(12.dp))
    Text("Enter the code you received", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(
        "Someone in your family sent you a ${InviteCodes.LENGTH}-character code. You'll see exactly what they're asking for before anything is shared.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    if (!cloudConfigured) {
        Banner(
            icon = Icons.Filled.CloudOff,
            title = "Family sync isn't set up in this app",
            message = "Invite codes need Firebase to be configured (see README).",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(16.dp))
    }
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Invite code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        enabled = code.length == InviteCodes.LENGTH && !busy && cloudConfigured,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Continue")
    }
}

/**
 * The consent screen: the person being asked sees who is asking, exactly what will be shared,
 * and how they stay in control, before anything is shared.
 */
@Composable
private fun ConsentContent(
    invite: Invite,
    isOwnInvite: Boolean,
    busy: Boolean,
    error: String?,
    onAccept: (String?) -> Unit,
    onDecline: () -> Unit,
) {
    val from = invite.fromName
    val theySeeYou = invite.direction.inviterSeesInvitee
    val youSeeThem = invite.direction.inviteeSeesInviter
    val title = when (invite.direction) {
        ShareDirection.VIEW_THEM -> "$from wants to see your location"
        ShareDirection.SHARE_MINE -> "$from wants to share their location with you"
        ShareDirection.MUTUAL -> "$from wants you to see each other's location"
    }
    var relation by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(from, invite.fromUid, 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            "Invite code ${invite.code}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))

    val blocked = when {
        isOwnInvite -> "This is your own invite. Send the code to your family member; they enter it on their phone."
        invite.status != InviteStatus.PENDING -> "This invite was already answered. Ask $from to send a new one if needed."
        invite.isExpired() -> "This invite has expired. Invites last 48 hours; ask $from to send a new one."
        else -> null
    }
    if (blocked != null) {
        Banner(
            icon = if (invite.isExpired()) Icons.Filled.Schedule else Icons.Filled.Info,
            title = "Can't use this invite",
            message = blocked,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        return
    }

    if (theySeeYou) {
        ConsentSection(
            Icons.Filled.Visibility,
            "What $from will see",
            listOf(
                "Your live location and street address",
                "Your phone's battery level",
                "When your location was last updated",
                "SOS alerts and check-ins you send",
            ),
        )
    }
    if (youSeeThem) {
        ConsentSection(
            Icons.Filled.Place,
            "What you will see",
            listOf("$from's live location and address", "Their battery level and when they last updated"),
        )
    }
    ConsentSection(
        Icons.Filled.Shield,
        "You stay in control",
        buildList {
            if (theySeeYou) {
                add("A banner in this app and a notification on your phone always show while your location is shared")
                add("Pause or stop sharing anytime from the banner, the notification or the People tab")
            }
            if (youSeeThem) add("You can stop seeing $from anytime")
            add("Nothing is shared unless you tap Allow")
        },
    )

    Text("What is $from to you? (optional)", style = MaterialTheme.typography.labelLarge)
    RelationChips(selected = relation, onSelect = { relation = if (relation == it) null else it })

    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onAccept(relation) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(if (theySeeYou) "Allow: share my location" else "Allow")
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onDecline, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Decline")
    }
    if (theySeeYou) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Android will ask for location permission next. For reliable sharing, choose “While using the app”, then later “Allow all the time” in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConsentSection(icon: ImageVector, title: String, points: List<String>) {
    OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            points.forEach { point ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
