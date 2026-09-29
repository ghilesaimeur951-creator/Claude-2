package com.streetblocks.app.ui.programs

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NextPlan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.EditScope
import com.streetblocks.app.data.model.PlanStatus
import com.streetblocks.app.data.model.PlannedSession
import com.streetblocks.app.data.model.Planning
import com.streetblocks.app.data.model.Program
import com.streetblocks.app.data.model.SessionSource
import com.streetblocks.app.engine.StepBuilder
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.Pill
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed class Row_ {
    abstract val key: String
    data class Week(val start: LocalDate, val index: Int, val done: Int, val total: Int) : Row_() { override val key = "w$start" }
    data class Day(val date: LocalDate, val sessions: List<PlannedSession>, val training: Boolean) : Row_() { override val key = "d$date" }
}

private data class ScopeRequest(val title: String, val detail: String?, val following: Int, val action: suspend (EditScope) -> Int)
private data class DateRequest(val title: String, val initial: LocalDate, val action: suspend (LocalDate) -> Unit)
private data class SourceRequest(val title: String, val action: suspend (SessionSource) -> Unit)

private fun buildRows(p: Program, planned: List<PlannedSession>): List<Row_> {
    val first = listOfNotNull(p.start, planned.minOfOrNull { it.date }).min()
    val last = listOfNotNull(p.end, planned.maxOfOrNull { it.date }).max()
    val byDate = planned.groupBy { it.date }
    val rows = mutableListOf<Row_>()
    var w = Planning.weekStart(first)
    var idx = 1
    while (!w.isAfter(last)) {
        val weekSessions = planned.filter { !it.date.isBefore(w) && it.date.isBefore(w.plusDays(7)) }
        rows += Row_.Week(w, idx, weekSessions.count { it.status == PlanStatus.DONE }, weekSessions.size)
        for (i in 0L until 7L) {
            val d = w.plusDays(i)
            if (d.isBefore(first) || d.isAfter(last)) continue
            rows += Row_.Day(d, byDate[d].orEmpty(), d.dayOfWeek in p.trainingDays)
        }
        w = w.plusDays(7)
        idx++
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(programId: Long, onBack: () -> Unit, onEditPlanned: (Long) -> Unit, onOpenSession: () -> Unit) {
    val c = LocalAppContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val program by remember(programId) { c.programs.program(programId) }.collectAsStateWithLifecycle(null)
    val planned by remember(programId) { c.programs.planned(programId) }.collectAsStateWithLifecycle(emptyList())
    val bands by c.equipment.bands.collectAsStateWithLifecycle(emptyList())
    val profile by c.equipment.profile.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    var selected by remember { mutableStateOf<PlannedSession?>(null) }
    var scopeReq by remember { mutableStateOf<ScopeRequest?>(null) }
    var dateReq by remember { mutableStateOf<DateRequest?>(null) }
    var sourceReq by remember { mutableStateOf<SourceRequest?>(null) }
    var timeFor by remember { mutableStateOf<PlannedSession?>(null) }
    var repeatWeek by remember { mutableStateOf<LocalDate?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    /** Exécute une action avec choix de portée si des séances suivantes existent. */
    fun scoped(p: PlannedSession, title: String, detail: String? = null, action: suspend (EditScope) -> Int) {
        val n = Planning.following(planned, p).size
        if (n == 0) scope.launch { action(EditScope.THIS); toast("Séance modifiée") }
        else scopeReq = ScopeRequest(title, detail, n, action)
    }

    val p = program
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p?.name ?: "Programme", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Modifier nom, objectif, fin") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; editInfo = true })
                            DropdownMenuItem(text = { Text("Supprimer le programme") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(pad))
            return@Scaffold
        }
        val rows = remember(p, planned) { buildRows(p, planned) }
        val stats = remember(planned) { Planning.stats(planned, today) }
        val listState = rememberLazyListState()
        LaunchedEffect(rows.isNotEmpty()) {
            val i = rows.indexOfFirst { it is Row_.Day && !it.date.isBefore(today) }
            if (i > 2) listState.scrollToItem(i - 1 + 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item(key = "header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            listOf(p.goal, Planning.period(p.start, p.end)).filter { it.isNotBlank() }.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Entraînement : " + Planning.week.filter { it in p.trainingDays }.joinToString(", ") { Planning.dayShort(it) } +
                                "  ·  Repos : " + Planning.week.filter { it !in p.trainingDays }.joinToString(", ") { Planning.dayShort(it) },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Stat("${stats.done}/${stats.total}", "faites")
                            Stat("${(stats.progress * 100).toInt()} %", "avancement")
                            Stat("${stats.skipped}", "sautées")
                            Stat("${stats.late}", "en retard", if (stats.late > 0) MaterialTheme.colorScheme.secondary else Color.White)
                        }
                        LinearProgressIndicator(
                            progress = { stats.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(10.dp),
                            color = Lime, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
                        )
                        stats.next?.let { n ->
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("PROCHAINE SÉANCE", style = MaterialTheme.typography.labelMedium, color = Lime)
                                    Text("${Planning.relative(n.date, today)}${Planning.timeLabel(n.timeMinutes)?.let { " · $it" } ?: ""}", fontWeight = FontWeight.Bold)
                                    Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FilledIconButton(
                                    onClick = { scope.launch { if (c.launchPlanned(n.id)) onOpenSession() else toast("Séance vide : ajoute des exercices") } },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Color.Black),
                                ) { Icon(Icons.Filled.PlayArrow, "Démarrer", Modifier.size(32.dp)) }
                            }
                        }
                    }
                }
            }
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is Row_.Week -> WeekHeader(
                        row,
                        onDuplicate = {
                            dateReq = DateRequest("Copier la semaine ${row.index} vers la semaine du…", row.start.plusDays(7)) { target ->
                                val n = c.programs.duplicateWeek(p.id, row.start, target)
                                toast(if (n > 0) "$n séances copiées" else "Rien à copier")
                            }
                        },
                        onRepeat = { repeatWeek = row.start },
                    )
                    is Row_.Day -> DayRow(
                        row, today,
                        durationMin = { s -> (StepBuilder.totalSec(StepBuilder.build(s.blocks, bands, profile, settings)) + 59) / 60 },
                        onSession = { selected = it },
                        onStart = { s -> scope.launch { if (c.launchPlanned(s.id)) onOpenSession() else toast("Séance vide : ajoute des exercices") } },
                        onAdd = {
                            sourceReq = SourceRequest("Séance du ${Planning.dateLabel(row.date)}") { src ->
                                val id = c.programs.add(p.id, row.date, src, null)
                                if (src is SessionSource.Empty) onEditPlanned(id)
                            }
                        },
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ actions sur une séance
    selected?.let { s ->
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        Planning.dateLong(s.date) + (Planning.timeLabel(s.timeMinutes)?.let { " · $it" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val following = Planning.following(planned, s).size
                    if (following > 0 && s.editable) {
                        Text("Créneau répété : $following séances suivantes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                fun close() { selected = null }
                if (s.status == PlanStatus.DONE) {
                    Text(
                        "Séance réalisée${s.completedAt?.let { " le " + java.text.SimpleDateFormat("d MMM 'à' HH:mm", java.util.Locale.FRANCE).format(java.util.Date(it)) } ?: ""}. " +
                            "Elle et son historique sont préservés : les modifications du programme ne la touchent pas.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Action(Icons.Filled.Edit, "Voir le contenu") { close(); onEditPlanned(s.id) }
                    Action(Icons.Filled.ContentCopy, "Dupliquer vers une autre date") {
                        close(); dateReq = DateRequest("Copier vers le…", s.date.plusDays(7)) { d -> c.programs.duplicateTo(s.id, d); toast("Séance copiée") }
                    }
                    Action(Icons.Filled.Restore, "Remettre à faire (l'historique reste enregistré)") {
                        close(); scope.launch { c.programs.setStatus(s.id, PlanStatus.PLANNED) }
                    }
                } else {
                    Action(Icons.Filled.PlayArrow, "Démarrer maintenant") {
                        close(); scope.launch { if (c.launchPlanned(s.id)) onOpenSession() else toast("Séance vide : ajoute des exercices") }
                    }
                    Action(Icons.Filled.Edit, "Personnaliser (exercices, séries, réps, charges, repos)") { close(); onEditPlanned(s.id) }
                    Action(Icons.Filled.SwapHoriz, "Remplacer par une autre séance") {
                        close()
                        sourceReq = SourceRequest("Remplacer par…") { src ->
                            scoped(s, "Remplacer la séance", "Le contenu sera remplacé par une copie de la séance choisie.") { sc -> c.programs.replaceWith(s.id, sc, src) }
                        }
                    }
                    Action(Icons.Filled.Schedule, "Changer l'horaire") { close(); timeFor = s }
                    Action(Icons.Filled.CalendarMonth, "Déplacer à une autre date") {
                        close()
                        dateReq = DateRequest("Déplacer au…", s.date) { d ->
                            scoped(s, "Déplacer la séance", "Avec « les suivantes », elles sont décalées du même nombre de jours.") { sc -> c.programs.move(s.id, d, sc) }
                        }
                    }
                    Action(Icons.Filled.NextPlan, "Reporter au lendemain") {
                        close()
                        scoped(s, "Reporter d'un jour") { sc -> c.programs.move(s.id, s.date.plusDays(1), sc) }
                    }
                    if (s.date.isBefore(today)) {
                        Action(Icons.Filled.Redo, "Reporter à aujourd'hui") {
                            close(); scope.launch { c.programs.move(s.id, today, EditScope.THIS); toast("Séance reportée à aujourd'hui") }
                        }
                    }
                    Action(Icons.Filled.ContentCopy, "Dupliquer vers une autre date") {
                        close(); dateReq = DateRequest("Copier vers le…", s.date.plusDays(1)) { d -> c.programs.duplicateTo(s.id, d); toast("Séance copiée") }
                    }
                    Action(Icons.Filled.Check, "Marquer comme faite") { close(); scope.launch { c.programs.setStatus(s.id, PlanStatus.DONE) } }
                    if (s.status == PlanStatus.SKIPPED) {
                        Action(Icons.Filled.Restore, "Replanifier") { close(); scope.launch { c.programs.setStatus(s.id, PlanStatus.PLANNED) } }
                    } else {
                        Action(Icons.Filled.SkipNext, "Sauter cette séance") { close(); scope.launch { c.programs.setStatus(s.id, PlanStatus.SKIPPED) } }
                    }
                    Action(Icons.Filled.Delete, "Supprimer") {
                        close(); scoped(s, "Supprimer la séance") { sc -> c.programs.delete(s.id, sc) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ dialogues
    scopeReq?.let { r ->
        ScopeDialog(r.title, r.following, detail = r.detail, onDismiss = { scopeReq = null }, onPick = { sc ->
            scopeReq = null
            scope.launch {
                val n = r.action(sc)
                toast(if (sc == EditScope.THIS) "Séance modifiée" else "$n séances modifiées")
            }
        })
    }
    dateReq?.let { r ->
        DatePickDialog(r.initial, r.title, { dateReq = null }) { d -> dateReq = null; scope.launch { r.action(d) } }
    }
    sourceReq?.let { r ->
        SourcePickerDialog(r.title, { sourceReq = null }) { src, _ -> sourceReq = null; scope.launch { r.action(src) } }
    }
    timeFor?.let { s ->
        TimePickDialog(s.timeMinutes, { timeFor = null }) { t ->
            timeFor = null
            scoped(s, "Changer l'horaire") { sc -> c.programs.edit(s.id, sc) { it.copy(timeMinutes = t) } }
        }
    }
    repeatWeek?.let { w ->
        AlertDialog(
            onDismissRequest = { repeatWeek = null },
            title = { Text("Répéter cette semaine ?") },
            text = {
                Text(
                    "L'organisation de la semaine du ${Planning.shortDate(w)} (jours, séances, horaires, contenus) sera copiée sur toutes les semaines suivantes jusqu'à la fin du programme. " +
                        "Les séances non réalisées de ces semaines seront remplacées ; les séances réalisées sont conservées."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repeatWeek = null
                    scope.launch {
                        val n = c.programs.repeatWeek(programId, w)
                        toast(if (n > 0) "Semaine répétée" else "Aucune séance dans cette semaine")
                    }
                }) { Text("Répéter") }
            },
            dismissButton = { TextButton(onClick = { repeatWeek = null }) { Text("Annuler") } },
        )
    }
    if (editInfo && p != null) {
        var name by remember { mutableStateOf(p.name) }
        var goal by remember { mutableStateOf(p.goal) }
        var end by remember { mutableStateOf(p.end) }
        var pickEnd by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editInfo = false },
            title = { Text("Programme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                    OutlinedTextField(goal, { goal = it }, label = { Text("Objectif") }, singleLine = true)
                    Text("Fin : ${Planning.dateLabel(end)} (modifier)", color = Lime, modifier = Modifier.clickable { pickEnd = true }.padding(vertical = 6.dp))
                    Text(
                        "Changer la date de fin ne crée ni ne supprime de séance : utilise « Répéter cette semaine » pour remplir une période prolongée.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editInfo = false; scope.launch { c.programs.updateInfo(p.id, name, goal, end) } }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { editInfo = false }) { Text("Annuler") } },
        )
        if (pickEnd) DatePickDialog(end, "Fin du programme", { pickEnd = false }) { end = it; pickEnd = false }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce programme ?") },
            text = { Text("Les séances planifiées seront supprimées. L'historique des séances déjà réalisées est conservé.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.programs.deleteProgram(programId); onBack() } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color = Color.White) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null, tint = Lime) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun WeekHeader(w: Row_.Week, onDuplicate: () -> Unit, onRepeat: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SEMAINE ${w.index}", style = MaterialTheme.typography.labelLarge, color = Lime)
            Text(
                "${Planning.shortDate(w.start)} – ${Planning.shortDate(w.start.plusDays(6))} · ${w.done}/${w.total} faites",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options de la semaine") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Dupliquer la semaine vers…") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Répéter sur les semaines suivantes") }, leadingIcon = { Icon(Icons.Filled.EventRepeat, null) }, onClick = { menu = false; onRepeat() })
            }
        }
    }
}

@Composable
private fun DayRow(
    row: Row_.Day,
    today: LocalDate,
    durationMin: (PlannedSession) -> Int,
    onSession: (PlannedSession) -> Unit,
    onStart: (PlannedSession) -> Unit,
    onAdd: () -> Unit,
) {
    val isToday = row.date == today
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Column(
            Modifier.width(52.dp)
                .background(if (isToday) Lime else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fg = if (isToday) Color.Black else if (row.date.isBefore(today)) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
            Text(Planning.dayShort(row.date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = fg)
            Text("${row.date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, color = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row.sessions.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                    Text(
                        if (row.training) "Aucune séance" else "Repos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Ajouter une séance", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            row.sessions.forEach { s -> SessionCard(s, today, durationMin(s), { onSession(s) }, { onStart(s) }) }
        }
    }
}

@Composable
private fun SessionCard(s: PlannedSession, today: LocalDate, minutes: Int, onClick: () -> Unit, onStart: () -> Unit) {
    val late = s.status == PlanStatus.PLANNED && s.date.isBefore(today)
    val accent = s.blocks.firstOrNull { it.isExercise }?.type?.let(::blockColor) ?: blockColor(BlockType.WARMUP)
    val (label, pillColor, pillText) = when {
        s.status == PlanStatus.DONE -> Triple("FAITE ✓", Lime, Color.Black)
        s.status == PlanStatus.SKIPPED -> Triple("SAUTÉE", MaterialTheme.colorScheme.surfaceVariant, Color.White)
        late -> Triple("EN RETARD", MaterialTheme.colorScheme.secondary, Color.Black)
        else -> Triple("À FAIRE", MaterialTheme.colorScheme.surfaceVariant, Color.White)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(64.dp).background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    s.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (s.status == PlanStatus.SKIPPED) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                )
                Text(
                    listOfNotNull(Planning.timeLabel(s.timeMinutes), "${s.blocks.count { it.isExercise }} exercices", "~$minutes min").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Pill(label, pillColor, Modifier.padding(top = 4.dp), textColor = pillText)
            }
            if (s.status == PlanStatus.PLANNED && !s.date.isAfter(today)) {
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.padding(end = 8.dp).size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Color.Black),
                ) { Icon(Icons.Filled.PlayArrow, "Démarrer") }
            }
        }
    }
}
