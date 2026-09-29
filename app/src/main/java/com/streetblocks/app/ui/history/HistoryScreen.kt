package com.streetblocks.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.streetblocks.app.AppContainer
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.ExerciseLog
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.Progression
import com.streetblocks.app.data.model.SessionRecord
import com.streetblocks.app.data.model.kgLabel
import com.streetblocks.app.data.model.timeLabel
import com.streetblocks.app.ui.Pill
import com.streetblocks.app.ui.containerViewModel
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProgressPoint(val time: Long, val value: Double, val label: String)

data class ProgressItem(
    val key: String,
    val name: String,
    val type: BlockType,
    val metric: String,
    val points: List<ProgressPoint>,
    val best: String,
    val lastLabel: String,
    val nextLabel: String,
)

class HistoryViewModel(val c: AppContainer) : ViewModel() {
    val sessions: StateFlow<List<SessionRecord>> =
        c.sessions.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progress: StateFlow<List<ProgressItem>> =
        combine(c.sessions.sessions, c.settings.settings, c.equipment.profile, c.equipment.bands) { sessions, s, profile, bands ->
            val entries = sessions.sortedBy { it.startedAt }
                .flatMap { r -> r.logs.filter { it.setsDone > 0 }.map { r.startedAt to it } }
            entries.groupBy { it.second.key }.map { (key, list) ->
                val last = list.last().second
                val weighted = list.any { it.second.addedKg > 0 }
                val metric = when {
                    last.timed -> "Durée par série (s)"
                    weighted -> "Charge ajoutée (kg)"
                    else -> "Répétitions par série"
                }
                val points = list.map { (t, l) ->
                    val v = when {
                        l.timed -> l.workSec.toDouble()
                        weighted -> l.addedKg
                        else -> l.repsDone.toDouble()
                    }
                    ProgressPoint(t, v, perf(l))
                }
                val bestLog = list.maxWith(compareBy<Pair<Long, ExerciseLog>>({ it.second.addedKg }, { if (it.second.timed) it.second.workSec else it.second.repsDone })).second
                ProgressItem(
                    key = key, name = last.name, type = last.type, metric = metric, points = points,
                    best = perf(bestLog), lastLabel = perf(last),
                    nextLabel = Progression.advise(last, s, profile, bands).next,
                )
            }.sortedByDescending { it.points.last().time }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { c.sessions.delete(id) }

    companion object {
        fun perf(l: ExerciseLog) = Progression.performanceLabel(l.setsDone, l.repsDone, l.timed, l.workSec, l.loadMode, l.addedKg, l.bandName)
    }
}

@Composable
fun HistoryScreen() {
    val vm = containerViewModel { HistoryViewModel(it) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<SessionRecord?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("HISTORIQUE", style = MaterialTheme.typography.headlineMedium, color = Lime, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Séances réalisées") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Progression") })
        }
        if (tab == 0) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sessions.isEmpty()) item { Empty("Aucune séance réalisée pour l'instant. Lance une séance depuis l'accueil !") }
                items(sessions, key = { it.id }) { s -> SessionCard(s, onDelete = { toDelete = s }) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progress.isEmpty()) item { Empty("La progression apparaîtra après ta première séance.") }
                items(progress, key = { it.key }) { p -> ProgressCard(p) }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer cette séance de l'historique ?") },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
}

private val dateFmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE)

@Composable
private fun SessionCard(s: SessionRecord, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.workoutName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        dateFmt.format(Date(s.startedAt)).replaceFirstChar { it.uppercase() } + " · " + timeLabel(s.durationSec),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.completed) Pill("TERMINÉE", Lime, textColor = androidx.compose.ui.graphics.Color.Black)
                else Pill("PARTIELLE", MaterialTheme.colorScheme.secondary, textColor = androidx.compose.ui.graphics.Color.Black)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (expanded) {
                s.logs.forEach { l ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(blockColor(l.type), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(l.name, modifier = Modifier.weight(1f))
                        Text(HistoryViewModel.perf(l), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    "${s.logs.size} exercices · ${s.logs.sumOf { it.setsDone }} séries — touche pour le détail",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(p: ProgressItem) {
    val color = blockColor(p.type)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${p.points.size} séance${if (p.points.size > 1) "s" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (p.points.size >= 2) {
                Text(p.metric, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Sparkline(p.points.map { it.value }, color, Modifier.fillMaxWidth().height(64.dp).padding(vertical = 6.dp))
            }
            Text("Dernière : ${p.lastLabel}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("Record : ${p.best}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Prochaine séance : ${p.nextLabel}", color = Lime, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        fun y(v: Double) = (size.height - ((v - min) / range * size.height * 0.85 + size.height * 0.075)).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v -> drawCircle(color, 4.dp.toPx(), Offset(i * stepX, y(v))) }
    }
}
