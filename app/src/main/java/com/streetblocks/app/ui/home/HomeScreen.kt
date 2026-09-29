package com.streetblocks.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.streetblocks.app.AppContainer
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.SessionRecord
import com.streetblocks.app.data.model.Templates
import com.streetblocks.app.data.model.Workout
import com.streetblocks.app.data.model.WorkoutTemplate
import com.streetblocks.app.engine.StepBuilder
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.containerViewModel
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WorkoutCardData(val workout: Workout, val durationMin: Int, val exerciseCount: Int)

class HomeViewModel(val c: AppContainer) : ViewModel() {
    val cards: StateFlow<List<WorkoutCardData>> =
        combine(c.workouts.workouts, c.equipment.bands, c.equipment.profile, c.settings.settings) { ws, bands, profile, s ->
            ws.map { w ->
                val steps = StepBuilder.build(w.blocks, bands, profile, s)
                WorkoutCardData(w, (StepBuilder.totalSec(steps) + 59) / 60, w.blocks.count { it.isExercise })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionRecord>> =
        c.sessions.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun duplicate(id: Long) = viewModelScope.launch { c.workouts.duplicate(id) }
    fun delete(id: Long) = viewModelScope.launch { c.workouts.delete(id) }
}

private fun weekStart(): Long = Calendar.getInstance().apply {
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onTemplates: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenProgram: (Long) -> Unit = {},
) {
    val vm = containerViewModel { HomeViewModel(it) }
    val planned by vm.c.programs.allPlanned.collectAsStateWithLifecycle(emptyList())
    val cards by vm.cards.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val engine by vm.c.engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Workout?>(null) }

    fun start(id: Long) {
        scope.launch {
            if (vm.c.launchSession(id)) onOpenSession()
            else Toast.makeText(ctx, "Ajoute au moins un exercice à la séance", Toast.LENGTH_SHORT).show()
        }
    }

    val featured = cards.maxByOrNull { it.workout.lastPerformedAt ?: 0L } ?: cards.firstOrNull()
    val thisWeek = sessions.count { it.startedAt >= weekStart() }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("STREET BLOCKS", style = MaterialTheme.typography.headlineMedium, color = Lime)
                    Text(
                        SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$thisWeek", style = MaterialTheme.typography.headlineMedium)
                    Text("séance${if (thisWeek > 1) "s" else ""} cette semaine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (engine.running || engine.finished) {
            item {
                Card(
                    onClick = onOpenSession,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (engine.running) "SÉANCE EN COURS" else "SÉANCE TERMINÉE", color = Color.Black, fontWeight = FontWeight.Black)
                            Text(engine.workoutName, color = Color.Black)
                        }
                        Text("OUVRIR ›", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        val today = java.time.LocalDate.now()
        val todays = planned.filter { it.date == today && it.status == com.streetblocks.app.data.model.PlanStatus.PLANNED }
        val upcoming = planned.filter { it.status == com.streetblocks.app.data.model.PlanStatus.PLANNED && it.date.isAfter(today) }
            .minByOrNull { it.date.toEpochDay() * 2000 + (it.timeMinutes ?: 0) }
        if (todays.isNotEmpty() || upcoming != null) {
            item {
                ProgramTodayCard(
                    todays = todays, upcoming = upcoming,
                    onStart = { id ->
                        scope.launch {
                            if (vm.c.launchPlanned(id)) onOpenSession()
                            else Toast.makeText(ctx, "Séance vide : ajoute des exercices", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenProgram = onOpenProgram,
                )
            }
        }

        item {
            HeroCard(featured, onStart = { featured?.let { start(it.workout.id) } }, onCreate = onCreate)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Créer")
                }
                OutlinedButton(onClick = onTemplates, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Modèles")
                }
            }
        }

        item {
            Text("MES SÉANCES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        if (cards.isEmpty()) {
            item {
                Text(
                    "Aucune séance pour l'instant. Crée ta première séance par blocs ou pars d'un modèle (force, hypertrophie, muscle-up…).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(cards, key = { it.workout.id }) { card ->
            WorkoutRow(
                card,
                onStart = { start(card.workout.id) },
                onEdit = { onEdit(card.workout.id) },
                onDuplicate = { vm.duplicate(card.workout.id) },
                onDelete = { confirmDelete = card.workout },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Supprimer « ${w.name} » ?") },
            text = { Text("L'historique des séances réalisées est conservé.") },
            confirmButton = { TextButton(onClick = { vm.delete(w.id); confirmDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProgramTodayCard(
    todays: List<com.streetblocks.app.data.model.PlannedSession>,
    upcoming: com.streetblocks.app.data.model.PlannedSession?,
    onStart: (Long) -> Unit,
    onOpenProgram: (Long) -> Unit,
) {
    val P = com.streetblocks.app.data.model.Planning
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (todays.isNotEmpty()) "AUJOURD'HUI DANS TON PROGRAMME" else "PROCHAINE SÉANCE PLANIFIÉE", color = Lime, style = MaterialTheme.typography.labelLarge)
            (todays.ifEmpty { listOfNotNull(upcoming) }).forEach { s ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onOpenProgram(s.programId) }) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(P.relative(s.date), P.timeLabel(s.timeMinutes), "${s.blocks.count { it.isExercise }} exercices").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (todays.isNotEmpty()) {
                        FilledIconButton(
                            onClick = { onStart(s.id) },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Color.Black),
                        ) { Icon(Icons.Filled.PlayArrow, "Démarrer", Modifier.size(30.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(featured: WorkoutCardData?, onStart: () -> Unit, onCreate: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF2A3A00), Color(0xFF12141A))),
                RoundedCornerShape(24.dp),
            )
            .padding(20.dp)
    ) {
        Column {
            Text(if (featured != null) "PRÊT À T'ENTRAÎNER ?" else "BIENVENUE", color = Lime, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                featured?.workout?.name ?: "Construis ta séance comme un puzzle",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (featured != null) {
                Text(
                    "${featured.exerciseCount} exercices · ~${featured.durationMin} min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BlockStrip(featured.workout)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = if (featured != null) onStart else onCreate,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
            ) {
                Icon(if (featured != null) Icons.Filled.PlayArrow else Icons.Filled.Add, null, Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (featured != null) "DÉMARRER LA SÉANCE" else "CRÉER UNE SÉANCE", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Petite frise colorée représentant les blocs d'une séance. */
@Composable
private fun BlockStrip(w: Workout) {
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        w.blocks.take(24).forEach { b ->
            val weight = when (b.type) {
                BlockType.REST, BlockType.END -> 0.5f
                else -> 1f
            }
            Box(Modifier.weight(weight).height(10.dp).background(blockColor(b.type), RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun WorkoutRow(
    card: WorkoutCardData,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val last = card.workout.lastPerformedAt?.let { "Dernière : " + SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(it)) } ?: "Jamais réalisée"
                Text(
                    "${card.exerciseCount} exercices · ~${card.durationMin} min · $last",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BlockStrip(card.workout)
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Color.Black),
            ) { Icon(Icons.Filled.PlayArrow, "Démarrer", Modifier.size(30.dp)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Modifier") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Dupliquer") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Modèles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modèles de séances") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Choisis un modèle : il est copié dans tes séances et reste entièrement modifiable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(Templates.all, key = { it.key }) { t ->
                TemplateCard(t) { scope.launch { onCreated(c.createFromTemplate(t)) } }
            }
        }
    }
}

@Composable
private fun TemplateCard(t: WorkoutTemplate, onUse: () -> Unit) {
    val blocks = remember(t.key) { t.blocks() }
    Card(
        onClick = onUse,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Text(t.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                blocks.forEach { b ->
                    Box(Modifier.weight(if (b.type == BlockType.REST || b.type == BlockType.END) 0.5f else 1f).height(10.dp).background(blockColor(b.type), RoundedCornerShape(3.dp)))
                }
            }
            Text("UTILISER CE MODÈLE ›", color = Lime, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
