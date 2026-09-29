package com.streetblocks.app.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.streetblocks.app.AppContainer
import com.streetblocks.app.data.model.DaySlot
import com.streetblocks.app.data.model.PlannedSession
import com.streetblocks.app.data.model.Planning
import com.streetblocks.app.data.model.Program
import com.streetblocks.app.data.model.ProgramLength
import com.streetblocks.app.data.model.ProgramSpec
import com.streetblocks.app.data.model.ProgramStats
import com.streetblocks.app.data.model.SessionSource
import com.streetblocks.app.ui.ChipGroup
import com.streetblocks.app.ui.ChipItem
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.SectionTitle
import com.streetblocks.app.ui.containerViewModel
import com.streetblocks.app.ui.theme.Lime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class ProgramCard(val program: Program, val stats: ProgramStats)

class ProgramsViewModel(val c: AppContainer) : ViewModel() {
    val cards: StateFlow<List<ProgramCard>> = combine(c.programs.programs, c.programs.allPlanned) { ps, planned ->
        val byProgram = planned.groupBy { it.programId }
        ps.map { ProgramCard(it, Planning.stats(byProgram[it.id].orEmpty())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ------------------------------------------------------------------ Liste

@Composable
fun ProgramsScreen(onCreate: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = containerViewModel { ProgramsViewModel(it) }
    val cards by vm.cards.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("MES PROGRAMMES", style = MaterialTheme.typography.headlineMedium, color = Lime)
                Text(
                    "Planifie tes séances sur une semaine, un mois ou plus. Chaque séance planifiée garde sa propre configuration.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cards.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = Lime, modifier = Modifier.size(40.dp))
                            Text("Aucun programme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                "Exemple : lundi, mercredi et vendredi pendant deux mois, avec une séance différente chaque jour.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onCreate, modifier = Modifier.padding(top = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
                            ) { Text("Créer mon premier programme", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            items(cards, key = { it.program.id }) { card -> ProgramRow(card, today) { onOpen(card.program.id) } }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Lime, contentColor = Color.Black,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Nouveau programme", fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable
private fun ProgramRow(card: ProgramCard, today: LocalDate, onClick: () -> Unit) {
    val p = card.program
    val s = card.stats
    val state = when {
        today.isBefore(p.start) -> "À venir"
        today.isAfter(p.end) -> "Terminé"
        else -> "En cours"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(p.goal, Planning.period(p.start, p.end)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                com.streetblocks.app.ui.Pill(state, if (state == "En cours") Lime else MaterialTheme.colorScheme.surfaceVariant, textColor = if (state == "En cours") Color.Black else Color.White)
            }
            Text(
                Planning.week.filter { it in p.trainingDays }.joinToString(" · ") { Planning.dayShort(it) },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp),
                color = Lime, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text("${s.done}/${s.total} séances faites", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (s.late > 0) Text("${s.late} en retard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            s.next?.let { n ->
                Text(
                    "Prochaine : ${Planning.relative(n.date, today)}${Planning.timeLabel(n.timeMinutes)?.let { " à $it" } ?: ""} — ${n.name}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Lime,
                    modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Assistant de création

private data class SlotDraft(val source: SessionSource = SessionSource.Empty, val label: String = "Nouvelle séance (à construire)", val time: Int? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramWizardScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(Planning.goals.first()) }
    var length by remember { mutableStateOf(ProgramLength.TWO_MONTHS) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var customEnd by remember { mutableStateOf(LocalDate.now().plusWeeks(4)) }
    val days = remember { mutableStateMapOf(DayOfWeek.MONDAY to SlotDraft(), DayOfWeek.WEDNESDAY to SlotDraft(), DayOfWeek.FRIDAY to SlotDraft()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickSourceFor by remember { mutableStateOf<DayOfWeek?>(null) }
    var pickTimeFor by remember { mutableStateOf<DayOfWeek?>(null) }
    var creating by remember { mutableStateOf(false) }

    val end = if (length == ProgramLength.CUSTOM) customEnd else length.endFor(start)
    val count = Planning.datesFor(start, end, days.keys).size
    val valid = days.isNotEmpty() && !end.isBefore(start)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouveau programme") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Nom du programme") },
                placeholder = { Text("ex : Prise de force automne") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Objectif")
            ChipGroup(Planning.goals.map { ChipItem(it, it) }, goal, { goal = it })

            SectionTitle("Période")
            ChipGroup(ProgramLength.entries.map { ChipItem(it.name, it.label) }, length.name, { length = ProgramLength.valueOf(it) })
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateBox("Début", start, Modifier.weight(1f)) { pickStart = true }
                DateBox("Fin", end, Modifier.weight(1f)) { length = ProgramLength.CUSTOM; customEnd = end; pickEnd = true }
            }

            SectionTitle("Jours d'entraînement")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Planning.week.forEach { d ->
                    FilterChip(
                        selected = d in days,
                        onClick = { if (d in days) days.remove(d) else days[d] = SlotDraft() },
                        label = { Text(Planning.dayShort(d)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lime, selectedLabelColor = Color.Black),
                    )
                }
            }
            val rest = Planning.week.filter { it !in days }
            Text(
                if (rest.isEmpty()) "Aucun jour de repos" else "Repos : " + rest.joinToString(", ") { Planning.dayShort(it) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (days.isNotEmpty()) {
                SectionTitle("Organisation de la semaine (répétée sur toute la période)")
                Planning.week.filter { it in days }.forEach { d ->
                    val slot = days.getValue(d)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).background(Lime, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(Planning.dayShort(d), color = Color.Black, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).clickable { pickSourceFor = d }) {
                                Text(slot.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Toucher pour choisir la séance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { pickTimeFor = d }.padding(6.dp)) {
                                Icon(Icons.Filled.Schedule, null, tint = if (slot.time != null) Lime else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Planning.timeLabel(slot.time) ?: "Heure", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text(
                    "Chaque date reçoit sa propre copie de la séance : tu pourras ensuite personnaliser chaque occurrence (exercices, séries, répétitions, charges, repos).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("$count séances planifiées", style = MaterialTheme.typography.titleMedium, color = Lime)
                    Text(Planning.period(start, end), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = {
                    creating = true
                    scope.launch {
                        val id = c.programs.create(
                            ProgramSpec(
                                name = name.ifBlank { "$goal – ${Planning.week.filter { it in days }.joinToString("/") { Planning.dayShort(it) }}" },
                                goal = goal, start = start, end = end,
                                slots = days.map { (d, s) -> DaySlot(d, s.source, s.time) },
                            )
                        )
                        onCreated(id)
                    }
                },
                enabled = valid && !creating,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
            ) { Text("CRÉER LE PROGRAMME", fontWeight = FontWeight.Black) }
        }
    }

    if (pickStart) DatePickDialog(start, "Début du programme", { pickStart = false }) {
        start = it
        if (customEnd.isBefore(it)) customEnd = it.plusWeeks(4).minusDays(1)
        pickStart = false
    }
    if (pickEnd) DatePickDialog(customEnd, "Fin du programme", { pickEnd = false }) { customEnd = it; pickEnd = false }
    pickSourceFor?.let { d ->
        SourcePickerDialog("Séance du ${Planning.dayLong(d).lowercase()}", { pickSourceFor = null }) { src, label ->
            days[d] = days.getValue(d).copy(source = src, label = if (src is SessionSource.Empty) "Nouvelle séance (à construire)" else label)
            pickSourceFor = null
        }
    }
    pickTimeFor?.let { d ->
        TimePickDialog(days[d]?.time, { pickTimeFor = null }) { t ->
            days[d] = days.getValue(d).copy(time = t)
            pickTimeFor = null
        }
    }
}

@Composable
private fun DateBox(label: String, date: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Planning.dateLabel(date), fontWeight = FontWeight.Bold)
    }
}
