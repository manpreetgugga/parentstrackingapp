@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.parentstracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parentstracker.app.data.MedicalInfo
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.SavedPlace
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.Reliability

/** Plain-text Medical ID for WhatsApp/SMS, e.g. to send to a new doctor or a relative. */
internal fun medicalShareText(name: String, info: MedicalInfo): String = buildString {
    append("🩺 Medical ID: $name\n")
    if (info.bloodGroup.isNotBlank()) append("Blood group: ${info.bloodGroup}\n")
    if (info.conditions.isNotBlank()) append("Conditions: ${info.conditions}\n")
    if (info.allergies.isNotBlank()) append("Allergies: ${info.allergies}\n")
    if (info.medicines.isNotBlank()) append("Medicines: ${info.medicines}\n")
    val doctor = listOf(info.doctorName, info.doctorPhone).filter { it.isNotBlank() }.joinToString(" · ")
    if (doctor.isNotBlank()) append("Doctor: $doctor\n")
    append("(shared from Parents Tracker)")
}

@Composable
internal fun SimpleContactDialog(initialName: String, initialNumber: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who should “Call” ring?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Usually your own number, so Mom or Dad can reach you with one tap.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name shown on the button") },
                    placeholder = { Text("e.g. Manpreet, Beta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, number) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun QuietHoursDialog(start: Int, end: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val from = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
    val to = rememberTimePickerState(initialHour = end / 60, initialMinute = end % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = from)
                Text("Until", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = to)
                Text(
                    "SOS, “Are you OK?”, fall checks and medicine reminders still ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(from.hour * 60 + from.minute, to.hour * 60 + to.minute) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun CurfewDialog(
    places: List<SavedPlace>,
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int, Int) -> Unit,
) {
    var placeId by remember { mutableStateOf(places.first().id) }
    var uid by remember { mutableStateOf<String?>(null) }
    val time = rememberTimePickerState(initialHour = 20, initialMinute = 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("“Expected by” alert") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Who", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = uid == null, onClick = { uid = null }, label = { Text("Anyone") })
                    members.forEach { m -> FilterChip(selected = uid == m.uid, onClick = { uid = m.uid }, label = { Text(m.displayName) }) }
                }
                Text("Should be at", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.forEach { p -> FilterChip(selected = placeId == p.id, onClick = { placeId = p.id }, label = { Text(p.name) }) }
                }
                Text("By", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = time)
                Text(
                    "Checked every day for 3 hours after this time, using their live location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(placeId, uid, time.hour, time.minute) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Step-by-step help for phones that close background apps (common on Xiaomi, Oppo, Vivo, Realme, Samsung). */
@Composable
internal fun ReliabilityDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
        title = { Text("Keep sharing working") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On ${Reliability.brand()} phones:", style = MaterialTheme.typography.titleSmall)
                Reliability.steps().forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "Do this on your parents' phone too. Without it, the phone may stop sending locations and alerts after a while.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { Reliability.openBatterySettings(context) }) { Text("Battery settings") } },
        dismissButton = {
            Row {
                TextButton(onClick = { Intents.appSettings(context) }) { Text("App settings") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}
