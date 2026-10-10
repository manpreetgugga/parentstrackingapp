@file:OptIn(ExperimentalFoundationApi::class)

package com.parentstracker.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.data.CHECK_IN_MESSAGES
import com.parentstracker.app.data.Reminder
import com.parentstracker.app.data.ReminderType
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.rememberNow
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Geo
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.ReminderTimes
import com.parentstracker.app.util.SimpleText
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF2E7D32)
private val CallBlue = Color(0xFF1565C0)
private val MedicinePurple = Color(0xFF6A1B9A)

/**
 * Simple mode for parents who find phones confusing: four huge buttons, very little text, and
 * every action read aloud in English, Hindi or Punjabi. Leaving it needs a long press.
 */
@Composable
fun SimpleModeScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val members by vm.members.collectAsStateWithLifecycle()
    val sosRequested by vm.sosRequested.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = rememberNow()
    val text = SimpleText(s.simpleLanguage)
    var showMedicines by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmHelp by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }

    // The widget's SOS button: one big confirmation instead of the regular dialog.
    LaunchedEffect(sosRequested) {
        if (sosRequested) {
            confirmHelp = true
            vm.consumeSosRequest()
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(4_000)
            flash = null
        }
    }

    val callNumber = s.simpleCallNumber.ifBlank { members.firstOrNull { !it.phone.isNullOrBlank() && !it.isDemo }?.phone.orEmpty() }
    val callName = s.simpleCallName.ifBlank { members.firstOrNull { it.phone == callNumber }?.displayName.orEmpty() }
    val medicines = s.reminders.filter { it.enabled && it.type == ReminderType.MEDICINE }
    val nextMedicine = medicines.minByOrNull { ReminderTimes.nextTrigger(it.hour, it.minute, now) }

    fun sendHelp() {
        vm.triggerSos()
        vm.speak(text.helpSent)
        flash = text.helpSent
        if (s.emergencyNumbers.isNotEmpty()) Intents.sms(context, s.emergencyNumbers, sosMessage(s))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${text.greeting(LocalTime.now().hour)}, ${s.userName}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (s.checkInMessage != null && s.checkInAt > 0) {
                    Text(
                        "${text.lastCheckIn}: ${s.checkInMessage} · ${Geo.timeAgo(s.checkInAt, now)}",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Deliberately small and long-press only, so parents don't leave simple mode by accident.
            Box(
                Modifier
                    .size(56.dp)
                    .combinedClickable(onClick = { vm.speak(text.holdToExit) }, onLongClick = { confirmExit = true }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = text.holdToExit, tint = MaterialTheme.colorScheme.outline)
            }
        }

        flash?.let { message ->
            Surface(color = StatusColors.success, contentColor = Color.White, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text(message, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
            }
        }

        if (s.sosActive) {
            Surface(color = StatusColors.sos, contentColor = Color.White, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🆘 ${text.helpSent}", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { Intents.dial(context, s.emergencyDialNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = StatusColors.sos),
                        ) { Text("📞 ${s.emergencyDialNumber}", fontSize = 20.sp) }
                        Button(
                            onClick = { vm.cancelSos() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OkGreen),
                        ) { Text(text.imSafe, fontSize = 20.sp) }
                    }
                }
            }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(
                emoji = "👍",
                label = text.ok,
                color = OkGreen,
                modifier = Modifier.weight(1f),
                onClick = {
                    vm.checkIn(CHECK_IN_MESSAGES.first())
                    vm.speak(text.okDone)
                    flash = text.okDone
                },
            )
            BigTile(
                emoji = "📞",
                label = if (callName.isNotBlank()) text.call(callName) else text.callFamily,
                color = CallBlue,
                modifier = Modifier.weight(1f),
                onClick = {
                    vm.speak(text.calling(callName.ifBlank { "" }).trim())
                    Intents.dial(context, callNumber)
                },
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(
                emoji = "💊",
                label = text.medicine,
                sublabel = nextMedicine?.let { ReminderTimes.format(it.hour, it.minute) + " · " + it.label },
                color = MedicinePurple,
                modifier = Modifier.weight(1f),
                onClick = {
                    vm.speak(
                        nextMedicine?.let { text.nextMedicine(it.label, ReminderTimes.format(it.hour, it.minute)) } ?: text.noMedicine,
                    )
                    if (medicines.isNotEmpty()) showMedicines = true
                },
            )
            HoldForHelpTile(text = text, modifier = Modifier.weight(1f), onSpeak = vm::speak, onHelp = ::sendHelp)
        }
    }

    if (showMedicines) {
        AlertDialog(
            onDismissRequest = { showMedicines = false },
            title = { Text("💊 ${text.medicine}", fontSize = 26.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    medicines.forEach { reminder -> MedicineRow(reminder, text) {
                        vm.medicineTaken(reminder)
                        vm.speak(text.tookDone(reminder.label))
                        flash = text.tookDone(reminder.label)
                        showMedicines = false
                    } }
                }
            },
            confirmButton = { TextButton(onClick = { showMedicines = false }) { Text("✖", fontSize = 24.sp) } },
        )
    }

    if (confirmHelp) {
        AlertDialog(
            onDismissRequest = { confirmHelp = false },
            title = { Text("🆘 ${text.help}?", fontSize = 28.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmHelp = false
                        sendHelp()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusColors.sos, contentColor = Color.White),
                ) { Text(text.help, fontSize = 24.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmHelp = false }) { Text("✖", fontSize = 24.sp) } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave simple mode?") },
            text = { Text("The full app with maps, settings and family details will open. You can turn simple mode back on in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.setSimpleMode(false)
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun BigTile(
    emoji: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = color,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxHeight(),
    ) {
        TileContent(emoji, label, sublabel)
    }
}

@Composable
private fun TileContent(emoji: String, label: String, sublabel: String?) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
        if (sublabel != null) {
            Text(sublabel, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Help needs a 3-second press, so a pocket tap or a curious grandchild can't send an SOS. */
@Composable
private fun HoldForHelpTile(text: SimpleText, modifier: Modifier, onSpeak: (String) -> Unit, onHelp: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    // The gesture handler lives across recompositions; always call the latest callbacks.
    val help by rememberUpdatedState(onHelp)
    val speak by rememberUpdatedState(onSpeak)
    Surface(
        color = StatusColors.sos,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        speak(text.keepHolding)
                        val job = scope.launch {
                            val start = System.currentTimeMillis()
                            while (true) {
                                progress = ((System.currentTimeMillis() - start) / HOLD_MS.toFloat()).coerceAtMost(1f)
                                if (progress >= 1f) break
                                delay(16)
                            }
                            help()
                        }
                        tryAwaitRelease()
                        if (job.isActive) {
                            job.cancel()
                            speak(text.holdForHelp)
                        }
                        progress = 0f
                    },
                )
            },
    ) {
        Box {
            TileContent("🆘", text.help, if (progress > 0f) text.keepHolding else text.holdForHelp)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().height(10.dp).align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun MedicineRow(reminder: Reminder, text: SimpleText, onTaken: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("${reminder.label} · ${ReminderTimes.format(reminder.hour, reminder.minute)}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = onTaken,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OkGreen, contentColor = Color.White),
        ) { Text("✅ ${text.tookIt}", fontSize = 22.sp) }
    }
}

private const val HOLD_MS = 3_000L
