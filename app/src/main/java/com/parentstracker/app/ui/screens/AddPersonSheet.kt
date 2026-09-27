@file:OptIn(ExperimentalMaterial3Api::class)

package com.parentstracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.ShareDirection
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.RelationChips
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.InviteCodes
import kotlinx.coroutines.launch

@Composable
fun AddPersonSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("Mom") }
    var phone by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(ShareDirection.VIEW_THEM) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<Invite?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            val invite = created
            if (invite != null) {
                InviteCreated(
                    invite = invite,
                    onShare = { Intents.shareText(context, InviteCodes.message(invite), "Send invite") },
                    onSms = invite.phone?.let { number -> { Intents.sms(context, listOf(number), InviteCodes.message(invite)) } },
                    onDone = onDismiss,
                )
                return@Column
            }

            Text("Add a family member", style = MaterialTheme.typography.headlineSmall)
            Text(
                "They'll get an invite code. Nothing is shared until they see exactly what you're asking for and tap Allow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Their name") },
                placeholder = { Text("e.g. Mom") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("They are your…", style = MaterialTheme.typography.labelLarge)
            RelationChips(selected = relation, onSelect = { relation = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number (optional)") },
                supportingText = { Text("For the Call button, and to text them the invite") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("What would you like?", style = MaterialTheme.typography.labelLarge)
            DirectionOption(
                value = ShareDirection.VIEW_THEM,
                selected = direction,
                title = "See their location",
                subtitle = "Best for parents. They'll always see a banner saying you can see them.",
                onSelect = { direction = it },
            )
            DirectionOption(
                value = ShareDirection.SHARE_MINE,
                selected = direction,
                title = "Share my location with them",
                subtitle = "They'll see where you are.",
                onSelect = { direction = it },
            )
            DirectionOption(
                value = ShareDirection.MUTUAL,
                selected = direction,
                title = "Both: see each other",
                subtitle = "You'll see each other's location.",
                onSelect = { direction = it },
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        vm.createInvite(name, relation, phone, direction)
                            .onSuccess { created = it }
                            .onFailure { error = it.message ?: "Couldn't create the invite" }
                        busy = false
                    }
                },
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Create invite")
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    value: ShareDirection,
    selected: ShareDirection,
    title: String,
    subtitle: String,
    onSelect: (ShareDirection) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InviteCreated(invite: Invite, onShare: () -> Unit, onSms: (() -> Unit)?, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusColors.success, modifier = Modifier.size(48.dp))
        Text("Invite ready for ${invite.inviteeName}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Send them this code. It works for 48 hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Text(
                invite.code.chunked(3).joinToString(" "),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            "On their phone: Parents Tracker → People → Enter code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Send invite (WhatsApp, SMS…)")
        }
        onSms?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Sms, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Text it to ${invite.phone}")
            }
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
}
