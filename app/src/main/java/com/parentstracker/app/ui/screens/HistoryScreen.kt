@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.parentstracker.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parentstracker.app.data.EventKind
import com.parentstracker.app.data.LogEvent
import com.parentstracker.app.ui.AppViewModel
import com.parentstracker.app.ui.components.rememberNow
import com.parentstracker.app.ui.theme.StatusColors
import com.parentstracker.app.util.Days
import com.parentstracker.app.util.Intents
import com.parentstracker.app.util.Trail
import com.parentstracker.app.util.WeeklySummary

private enum class HistoryFilter(val label: String, val kinds: Set<EventKind>) {
    ALL("All", EventKind.entries.toSet()),
    SAFETY("Safety", setOf(EventKind.SOS)),
    FAMILY("Family alerts", setOf(EventKind.FAMILY_ALERT)),
    MEDICINE("Medicine", setOf(EventKind.MEDICINE_TAKEN, EventKind.MEDICINE_MISSED)),
    ME("My check-ins", setOf(EventKind.CHECK_IN, EventKind.JOURNEY, EventKind.MOOD)),
}

/** Everything that happened on this phone: SOS, check-ins, medicines, trips and family alerts. */
@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    val now = rememberNow()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val summary = remember(s.events, s.moods, now / 3_600_000) { WeeklySummary.compute(s.events, s.moods, now) }
    val events = s.events.filter { it.kind in filter.kinds }.sortedByDescending { it.time }
    val today = Days.epochDay(now)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (s.events.isNotEmpty()) {
                        IconButton(onClick = { Intents.shareText(context, historyText(s.userName, summary, events, today), "Share activity") }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Clear history") }
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") { WeeklySummaryCard(summary) }
            item(key = "filters") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (events.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Check-ins, medicines, trips, SOS and family alerts will show up here. History stays on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            events.groupBy { Days.epochDay(it.time) }.forEach { (day, dayEvents) ->
                item(key = "day-$day") {
                    Text(
                        Days.label(day, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(dayEvents, key = { "e-" + it.time + it.title.hashCode() }) { EventRow(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear activity history?") },
            text = { Text("This only clears the list on this phone. Nothing is changed for your family.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WeeklySummaryCard(summary: WeeklySummary) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("${summary.checkIns}", "check-ins")
                Stat(summary.adherencePercent?.let { "$it%" } ?: "–", "medicines taken")
                Stat("${summary.journeys}", "trips")
                Stat("${summary.sos}", "SOS", if (summary.sos > 0) StatusColors.sos else null)
            }
            if (summary.medicinesTaken + summary.medicinesMissed > 0) {
                Text(
                    "${summary.medicinesTaken} doses taken · ${summary.medicinesMissed} missed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (summary.moods.any { it.second != null }) {
                Spacer(Modifier.height(12.dp))
                Text("Mood", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    summary.moods.forEach { (day, mood) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(mood ?: "·", fontSize = 24.sp)
                            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (summary.familyAlerts > 0) {
                Text(
                    "${summary.familyAlerts} family alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EventRow(event: LogEvent) {
    val (icon, tint) = iconFor(event)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            event.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(Trail.clock(event.time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun iconFor(event: LogEvent): Pair<ImageVector, Color> = when (event.kind) {
    EventKind.SOS -> Icons.Filled.Warning to StatusColors.sos
    EventKind.CHECK_IN -> Icons.Filled.CheckCircle to StatusColors.success
    EventKind.MEDICINE_TAKEN -> Icons.Filled.Medication to StatusColors.success
    EventKind.MEDICINE_MISSED -> Icons.Filled.Medication to StatusColors.warning
    EventKind.FAMILY_ALERT -> Icons.Filled.Notifications to MaterialTheme.colorScheme.primary
    EventKind.JOURNEY -> Icons.AutoMirrored.Filled.DirectionsWalk to MaterialTheme.colorScheme.tertiary
    EventKind.MOOD -> (if (event.title.contains("unwell") || event.title.contains("Not feeling")) Icons.Filled.MoodBad else Icons.Filled.Mood) to
        MaterialTheme.colorScheme.secondary
}

private fun historyText(name: String, summary: WeeklySummary, events: List<LogEvent>, today: Long): String = buildString {
    append("Parents Tracker activity for $name\n")
    append("Last 7 days: ${summary.checkIns} check-ins")
    summary.adherencePercent?.let { append(", $it% of medicines taken") }
    append(", ${summary.journeys} trips, ${summary.sos} SOS\n")
    events.take(100).groupBy { Days.epochDay(it.time) }.forEach { (day, list) ->
        append("\n${Days.label(day, today)}\n")
        list.forEach { e -> append("• ${Trail.clock(e.time)}  ${e.title}${e.detail?.let { " ($it)" }.orEmpty()}\n") }
    }
}
