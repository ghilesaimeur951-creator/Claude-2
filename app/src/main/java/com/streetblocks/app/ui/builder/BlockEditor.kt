package com.streetblocks.app.ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockText
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.Catalog
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.SupportOption
import com.streetblocks.app.data.model.kgLabel
import com.streetblocks.app.data.model.timeLabel
import com.streetblocks.app.ui.ChipGroup
import com.streetblocks.app.ui.ChipItem
import com.streetblocks.app.ui.SectionTitle
import com.streetblocks.app.ui.Stepper
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import com.streetblocks.app.ui.theme.blockIcon
import com.streetblocks.app.ui.theme.parseHex

@Composable
fun BlockEditor(
    block: Block,
    bands: List<Band>,
    profile: EquipmentProfile,
    onChange: (Block) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val color = blockColor(block.type)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(color, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(blockIcon(block.type), null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("BLOC ${Catalog.typeLabel(block.type).uppercase()}", color = Color.White, fontWeight = FontWeight.Black)
                Text(BlockText.summary(block, bands, profile), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
        }

        when (block.type) {
            BlockType.REST -> RestEditor(block, onChange)
            BlockType.END -> {
                Spacer(Modifier.height(16.dp))
                Text("Ce bloc clôture automatiquement la séance : signal de fin, voix « Séance terminée », enregistrement dans l'historique. Les blocs placés après sont ignorés.")
            }
            BlockType.WARMUP -> WarmupEditor(block, bands, onChange)
            else -> ExerciseEditor(block, bands, profile, onChange)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dupliquer")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Supprimer")
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
        ) { Text("VALIDER", fontWeight = FontWeight.Black) }
    }
}

// ------------------------------------------------------------------ Repos

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestEditor(b: Block, onChange: (Block) -> Unit) {
    SectionTitle("Durée du repos")
    Text(timeLabel(b.workSec), fontSize = 56.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Stepper("Minutes", "${b.workSec / 60}", { onChange(b.copy(workSec = (b.workSec - 60).coerceAtLeast(5))) }, { onChange(b.copy(workSec = (b.workSec + 60).coerceAtMost(1800))) })
    Stepper("Secondes", "${b.workSec % 60}", { onChange(b.copy(workSec = (b.workSec - 5).coerceAtLeast(5))) }, { onChange(b.copy(workSec = (b.workSec + 5).coerceAtMost(1800))) })
    SectionTitle("Raccourcis")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 45, 60, 90, 120, 150, 180, 240, 300).forEach { s ->
            AssistChip(onClick = { onChange(b.copy(workSec = s)) }, label = { Text(timeLabel(s)) })
        }
    }
}

// ------------------------------------------------------------------ Échauffement

@Composable
private fun WarmupEditor(b: Block, bands: List<Band>, onChange: (Block) -> Unit) {
    SectionTitle("Type d'échauffement")
    ChipGroup(Catalog.warmupExercises.map { ChipItem(it.key, it.label) }, b.exercise, { onChange(b.copy(exercise = it)) }, blockColor(b.type))
    SectionTitle("Durée")
    Stepper("Durée totale", "${b.workSec / 60} min", { onChange(b.copy(workSec = (b.workSec - 60).coerceAtLeast(60))) }, { onChange(b.copy(workSec = (b.workSec + 60).coerceAtMost(1800))) })
    if (b.exercise == "ELASTIQUE") {
        SectionTitle("Élastique")
        BandChooser(b, bands, "MOBILITE", onChange)
    }
    val moves = Catalog.warmupMoves(b.exercise)
    val count = moves.size.coerceAtMost(maxOf(1, b.workSec / 20))
    SectionTitle("Déroulé guidé (${b.workSec / count} s par mouvement)")
    moves.take(count).forEachIndexed { i, m ->
        Text("${i + 1}. $m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
    }
}

// ------------------------------------------------------------------ Exercices

@Composable
private fun ExerciseEditor(b: Block, bands: List<Band>, profile: EquipmentProfile, onChange: (Block) -> Unit) {
    val accent = blockColor(b.type)

    SectionTitle("Exercice")
    ChipGroup(Catalog.exercises(b.type).map { ChipItem(it.key, it.label) }, b.exercise, { key ->
        val opt = Catalog.exercises(b.type).first { it.key == key }
        var n = b.copy(exercise = key)
        if (opt.timed) n = n.copy(timed = true)
        else if (b.type != BlockType.STATIC && Catalog.option(b)?.timed == true) n = n.copy(timed = false)
        opt.defaultSupport?.let { n = n.copy(support = it) }
        onChange(n)
    }, accent)
    if (b.type == BlockType.FREE && b.exercise == "CUSTOM") {
        OutlinedTextField(
            value = b.customName, onValueChange = { onChange(b.copy(customName = it)) },
            label = { Text("Nom de l'exercice") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    // Support / barre / position
    val supports: List<SupportOption> = when (b.type) {
        BlockType.PULLUP -> Catalog.bars.filter { it.key in profile.barsEnabled || it.key == b.support }
        BlockType.DIPS -> Catalog.dipsSupports.filter {
            it.key == b.support || (if (it.key == Catalog.BARRE_DROITE) profile.straightBarOwned else profile.parallelEnabled(it.key))
        }
        BlockType.PUSHUP -> Catalog.pushupPositions.filter {
            it.key == b.support || when (it.key) {
                Catalog.POS_PLATEFORME, Catalog.POS_MAINS_PLATEFORME -> profile.platformOwned
                Catalog.POS_BARRE_HANCHES -> Catalog.BAR_HANCHES in profile.barsEnabled
                Catalog.POS_BARRE_NOMBRIL -> Catalog.BAR_NOMBRIL in profile.barsEnabled
                else -> profile.floorOk
            }
        }
        else -> emptyList()
    }
    if (supports.isNotEmpty()) {
        SectionTitle(
            when (b.type) {
                BlockType.PULLUP -> "Barre"
                BlockType.DIPS -> "Support"
                else -> "Position"
            }
        )
        ChipGroup(supports.map { s ->
            val label = when (s.key) {
                Catalog.POS_PLATEFORME -> "Pieds surélevés (${profile.platformHeightCm} cm)"
                Catalog.POS_MAINS_PLATEFORME -> "Mains sur plateforme (${profile.platformHeightCm} cm)"
                else -> s.label
            }
            ChipItem(s.key, label)
        }, b.support, { onChange(b.copy(support = it)) }, accent)
        if (b.type == BlockType.PULLUP) {
            Catalog.barSuggestions[b.support]?.let {
                Text("Idéal pour : " + it.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (b.type == BlockType.DIPS && b.support != Catalog.BARRE_DROITE) {
            profile.parallels.firstOrNull { it.key == b.support }?.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    } else {
        BlockText.supportLabel(b, profile)?.let {
            Text("Équipement : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }

    if (b.type == BlockType.PULLUP) {
        SectionTitle("Prise")
        ChipGroup(Catalog.grips.map { ChipItem(it.key, it.label) }, b.grip, { onChange(b.copy(grip = it)) }, accent)
    }

    // Charge
    val canWeight = profile.vestOwned || profile.beltOwned
    val canBand = Catalog.supportsBand(b.type) && bands.isNotEmpty()
    SectionTitle("Charge")
    val modes = buildList {
        add(ChipItem(LoadMode.NONE.name, "Poids du corps"))
        if (canWeight || b.loadMode == LoadMode.WEIGHTED) add(ChipItem(LoadMode.WEIGHTED.name, if (b.type == BlockType.PULLUP || b.type == BlockType.DIPS) "Lesté" else "Lesté (gilet / ceinture)"))
        if (canBand || b.loadMode == LoadMode.BAND) add(ChipItem(LoadMode.BAND.name, "Assisté élastique"))
    }
    ChipGroup(modes, b.loadMode.name, { m ->
        val mode = LoadMode.valueOf(m)
        onChange(
            when (mode) {
                LoadMode.WEIGHTED -> b.copy(loadMode = mode, vestKg = if (b.vestKg == 0 && b.beltKg == 0.0 && profile.vestOwned) 5 else b.vestKg)
                LoadMode.BAND -> b.copy(loadMode = mode, bandId = b.bandId ?: defaultBand(b, bands)?.id)
                LoadMode.NONE -> b.copy(loadMode = mode)
            }
        )
    }, accent)

    when (b.loadMode) {
        LoadMode.WEIGHTED -> WeightEditor(b, profile, onChange)
        LoadMode.BAND -> BandChooser(b, bands, Catalog.bandUsageFor(b), onChange)
        LoadMode.NONE -> {}
    }

    // Volume
    SectionTitle("Séries & répétitions")
    Stepper("Séries", "${b.sets}", { onChange(b.copy(sets = (b.sets - 1).coerceAtLeast(1))) }, { onChange(b.copy(sets = (b.sets + 1).coerceAtMost(20))) })
    if (b.type != BlockType.STATIC) {
        ChipGroup(
            listOf(ChipItem("REPS", "Répétitions"), ChipItem("TIME", "Au temps")),
            if (b.timed) "TIME" else "REPS",
            { onChange(b.copy(timed = it == "TIME")) }, accent,
        )
    }
    if (b.timed) {
        Stepper("Durée d'une série", "${b.workSec} s", { onChange(b.copy(workSec = (b.workSec - 5).coerceAtLeast(5))) }, { onChange(b.copy(workSec = (b.workSec + 5).coerceAtMost(600))) })
    } else {
        Stepper("Répétitions", "${b.reps}", { onChange(b.copy(reps = (b.reps - 1).coerceAtLeast(1))) }, { onChange(b.copy(reps = (b.reps + 1).coerceAtMost(100))) })
        val auto = Catalog.option(b)?.secPerRep
        Stepper(
            "Durée estimée / rép.",
            if (b.secPerRep == 0) "Auto" else "${b.secPerRep} s",
            { onChange(b.copy(secPerRep = (b.secPerRep - 1).coerceAtLeast(0))) },
            { onChange(b.copy(secPerRep = (if (b.secPerRep == 0) (auto ?: 3) else b.secPerRep + 1).coerceAtMost(15))) },
            sub = "Fixe la durée de la série avant le repos automatique. Tu peux aussi toucher « Série terminée ».",
        )
    }
    if (b.sets > 1) {
        Stepper(
            "Repos entre séries", timeLabel(b.restBetweenSetsSec),
            { onChange(b.copy(restBetweenSetsSec = (b.restBetweenSetsSec - 15).coerceAtLeast(0))) },
            { onChange(b.copy(restBetweenSetsSec = (b.restBetweenSetsSec + 15).coerceAtMost(900))) },
        )
    }

    SectionTitle("Options")
    OutlinedTextField(
        value = b.tempo, onValueChange = { onChange(b.copy(tempo = it)) },
        label = { Text("Tempo / temps sous tension (ex : 3-1-1)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = b.note, onValueChange = { onChange(b.copy(note = it)) },
        label = { Text("Note") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

private fun defaultBand(b: Block, bands: List<Band>): Band? {
    val usage = Catalog.bandUsageFor(b)
    val compatible = bands.filter { usage == null || usage in it.usages }.ifEmpty { bands }
    return compatible.getOrNull(compatible.size / 2)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightEditor(b: Block, profile: EquipmentProfile, onChange: (Block) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        if (profile.vestOwned) {
            Stepper(
                "Gilet lesté", if (b.vestKg == 0) "—" else "${b.vestKg} kg",
                {
                    val v = b.vestKg - profile.vestStepKg
                    onChange(b.copy(vestKg = if (v < profile.vestMinKg) 0 else v))
                },
                {
                    val v = if (b.vestKg == 0) profile.vestMinKg else b.vestKg + profile.vestStepKg
                    onChange(b.copy(vestKg = v.coerceAtMost(profile.vestMaxKg)))
                },
                sub = "Réglable de ${profile.vestMinKg} à ${profile.vestMaxKg} kg",
            )
        }
        if (profile.beltOwned) {
            Stepper(
                "Ceinture lestée", if (b.beltKg == 0.0) "—" else kgLabel(b.beltKg),
                { onChange(b.copy(beltKg = (b.beltKg - 1).coerceAtLeast(0.0))) },
                { onChange(b.copy(beltKg = (b.beltKg + 1).coerceAtMost(profile.beltMaxKg.toDouble()))) },
                sub = "Charge libre",
            )
            val presets = profile.beltPresets()
            if (presets.isNotEmpty()) {
                Text("Avec tes poids :", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onChange(b.copy(beltKg = 0.0)) }, label = { Text("0") })
                    presets.forEach { kg ->
                        val desc = describePlates(kg, profile)
                        AssistChip(onClick = { onChange(b.copy(beltKg = kg)) }, label = { Text("${kgLabel(kg)}$desc") })
                    }
                }
            }
        }
        Text(
            "Charge ajoutée totale : +${kgLabel(b.vestKg + b.beltKg)}",
            fontWeight = FontWeight.Bold, color = Lime, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** « (blanc + noir) » pour une charge donnée, si une combinaison exacte existe. */
private fun describePlates(kg: Double, profile: EquipmentProfile): String {
    val units = profile.plates.flatMap { p -> List(p.quantity.coerceIn(0, 4)) { p } }
    val n = units.size
    for (mask in 1 until (1 shl n)) {
        var sum = 0.0
        val names = mutableListOf<String>()
        for (i in 0 until n) if (mask and (1 shl i) != 0) { sum += units[i].kg; names += units[i].name.removePrefix("Poids ").trimEnd('s') }
        if (kotlin.math.abs(sum - kg) < 0.01) return " (${names.joinToString(" + ")})"
    }
    return ""
}

@Composable
private fun BandChooser(b: Block, bands: List<Band>, usage: String?, onChange: (Block) -> Unit) {
    if (bands.isEmpty()) {
        Text("Aucun élastique : ajoute-les dans « Mon matériel ».", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val compatible = bands.filter { usage == null || usage in it.usages }
    val list = compatible.ifEmpty { bands }
    Column(Modifier.padding(top = 8.dp)) {
        ChipGroup(
            list.map { ChipItem(it.id.toString(), "${it.name} · ${it.resistanceLabel}", parseHex(it.colorHex)) },
            b.bandId?.toString(),
            { onChange(b.copy(bandId = it.toLong())) },
        )
        if (usage != null) {
            Text(
                "Élastiques compatibles « ${Catalog.usageLabel(usage)} » (du plus léger au plus fort). Plus la résistance est élevée, plus l'assistance est grande.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
