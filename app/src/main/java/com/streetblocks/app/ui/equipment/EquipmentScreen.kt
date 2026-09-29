package com.streetblocks.app.ui.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Catalog
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.PlateSet
import com.streetblocks.app.data.model.kgLabel
import com.streetblocks.app.ui.ChipItem
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.MultiChipGroup
import com.streetblocks.app.ui.Stepper
import com.streetblocks.app.ui.SwitchRow
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.parseHex
import kotlinx.coroutines.launch

private val bandPalette = listOf("#FDD835", "#FB8C00", "#E53935", "#D81B60", "#8E24AA", "#1E88E5", "#00ACC1", "#43A047", "#424242", "#9E9E9E")

@Composable
fun EquipmentScreen() {
    val c = LocalAppContainer.current
    val profile by c.equipment.profile.collectAsStateWithLifecycle()
    val bands by c.equipment.bands.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editingBand by remember { mutableStateOf<Band?>(null) }
    var deletingBand by remember { mutableStateOf<Band?>(null) }

    fun update(f: (EquipmentProfile) -> EquipmentProfile) = c.equipment.updateProfile(f)

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("MON MATÉRIEL", style = MaterialTheme.typography.headlineMedium, color = Lime)
            Text(
                "Ton équipement et ton parc : le créateur de séance ne propose que ce que tu as.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------- Élastiques
        item {
            Section("Élastiques de traction", "Du plus léger au plus fort. Élastique → résistance → exercices compatibles.") {
                bands.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(parseHex(b.colorHex), CircleShape).border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${b.name} · ${b.resistanceLabel}", fontWeight = FontWeight.Bold)
                            Text(
                                b.usages.joinToString(", ") { Catalog.usageLabel(it) }.ifBlank { "Aucun usage défini" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editingBand = b }) { Icon(Icons.Filled.Edit, "Modifier") }
                        IconButton(onClick = { deletingBand = b }) { Icon(Icons.Filled.Delete, "Supprimer") }
                    }
                }
                OutlinedButton(
                    onClick = { editingBand = Band(name = "", colorHex = bandPalette.first(), minKg = 10, maxKg = 20, usages = setOf("TRACTION")) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Ajouter un élastique") }
            }
        }

        // ---------------------------------------------------------- Gilet
        item {
            Section("Gilet lesté", "Tractions, dips, pompes lestées, squats, exercices au poids du corps.") {
                SwitchRow("Je possède un gilet lesté", profile.vestOwned, { v -> update { it.copy(vestOwned = v) } })
                if (profile.vestOwned) {
                    Stepper("Charge minimale", "${profile.vestMinKg} kg", { update { it.copy(vestMinKg = (it.vestMinKg - 1).coerceAtLeast(1)) } }, { update { it.copy(vestMinKg = (it.vestMinKg + 1).coerceAtMost(it.vestMaxKg)) } })
                    Stepper("Charge maximale", "${profile.vestMaxKg} kg", { update { it.copy(vestMaxKg = (it.vestMaxKg - 1).coerceAtLeast(it.vestMinKg)) } }, { update { it.copy(vestMaxKg = (it.vestMaxKg + 1).coerceAtMost(60)) } })
                    Stepper("Incrément", "${profile.vestStepKg} kg", { update { it.copy(vestStepKg = (it.vestStepKg - 1).coerceAtLeast(1)) } }, { update { it.copy(vestStepKg = (it.vestStepKg + 1).coerceAtMost(5)) } })
                }
            }
        }

        // ---------------------------------------------------------- Poids
        item {
            Section("Poids street workout", "Utilisables à la ceinture lestée et pour des exercices spécifiques.") {
                profile.plates.forEachIndexed { i, p ->
                    PlateEditor(
                        p,
                        onChange = { np -> update { it.copy(plates = it.plates.toMutableList().also { l -> l[i] = np }) } },
                        onDelete = { update { it.copy(plates = it.plates.toMutableList().also { l -> l.removeAt(i) }) } },
                    )
                }
                OutlinedButton(onClick = {
                    update { it.copy(plates = it.plates + PlateSet("Autres poids", 10.0, 1, "#9E9E9E")) }
                }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Ajouter une catégorie") }
            }
        }

        // ---------------------------------------------------------- Ceinture
        item {
            Section("Ceinture lestée", "Charge libre, associée aux tractions et dips lestés.") {
                SwitchRow("Je possède une ceinture lestée", profile.beltOwned, { v -> update { it.copy(beltOwned = v) } })
                if (profile.beltOwned) {
                    Stepper("Charge maximale", "${profile.beltMaxKg} kg", { update { it.copy(beltMaxKg = (it.beltMaxKg - 5).coerceAtLeast(5)) } }, { update { it.copy(beltMaxKg = (it.beltMaxKg + 5).coerceAtMost(150)) } })
                    val presets = profile.beltPresets()
                    if (presets.isNotEmpty()) {
                        Text(
                            "Charges possibles avec tes poids : " + presets.joinToString(" · ") { kgLabel(it) },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- Barres
        item {
            Section("Barres horizontales", "Coche les hauteurs présentes dans ton parc.") {
                Catalog.bars.forEach { bar ->
                    val on = bar.key in profile.barsEnabled
                    SwitchRow(
                        bar.label, on,
                        { v -> update { p -> p.copy(barsEnabled = if (v) (p.barsEnabled + bar.key).distinct() else p.barsEnabled - bar.key) } },
                        sub = Catalog.barSuggestions[bar.key]?.joinToString(", "),
                    )
                }
            }
        }

        item {
            Section("Barres parallèles (dips)", "Trois écartements, différence faible mais configurable.") {
                profile.parallels.forEachIndexed { i, ps ->
                    SwitchRow("Parallèles ${ps.label.lowercase()}", ps.enabled, { v ->
                        update { p -> p.copy(parallels = p.parallels.toMutableList().also { l -> l[i] = ps.copy(enabled = v) }) }
                    })
                    if (ps.enabled) {
                        OutlinedTextField(
                            value = ps.note,
                            onValueChange = { t -> update { p -> p.copy(parallels = p.parallels.toMutableList().also { l -> l[i] = ps.copy(note = t) }) } },
                            label = { Text("Écartement / repère (ex : 48 cm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            Section("Barre droite basse", "Dips barre droite, Russian dips, transitions de muscle-up.") {
                SwitchRow("Disponible", profile.straightBarOwned, { v -> update { it.copy(straightBarOwned = v) } })
            }
        }

        item {
            Section("Sol & plateformes", "Pompes, gainage, abdos au sol. Plateforme : pieds surélevés, step, sauts.") {
                SwitchRow("Sol propre (exercices au sol)", profile.floorOk, { v -> update { it.copy(floorOk = v) } })
                SwitchRow("Plateformes", profile.platformOwned, { v -> update { it.copy(platformOwned = v) } })
                if (profile.platformOwned) {
                    Stepper("Hauteur", "${profile.platformHeightCm} cm", { update { it.copy(platformHeightCm = (it.platformHeightCm - 5).coerceAtLeast(5)) } }, { update { it.copy(platformHeightCm = (it.platformHeightCm + 5).coerceAtMost(120)) } })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editingBand?.let { b ->
        BandDialog(
            initial = b,
            onDismiss = { editingBand = null },
            onSave = { nb -> scope.launch { c.equipment.saveBand(nb) }; editingBand = null },
        )
    }
    deletingBand?.let { b ->
        AlertDialog(
            onDismissRequest = { deletingBand = null },
            title = { Text("Supprimer l'élastique ${b.name} ?") },
            text = { Text("Les blocs qui l'utilisent devront choisir un autre élastique.") },
            confirmButton = { TextButton(onClick = { scope.launch { c.equipment.deleteBand(b.id) }; deletingBand = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deletingBand = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Lime)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun PlateEditor(p: PlateSet, onChange: (PlateSet) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(parseHex(p.colorHex), CircleShape).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape))
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = p.name, onValueChange = { onChange(p.copy(name = it)) },
                singleLine = true, modifier = Modifier.weight(1f), label = { Text("Nom") },
            )
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") }
        }
        Stepper("Poids unitaire", kgLabel(p.kg), { onChange(p.copy(kg = (p.kg - 0.5).coerceAtLeast(0.5))) }, { onChange(p.copy(kg = p.kg + 0.5)) })
        Stepper("Quantité", "${p.quantity}", { onChange(p.copy(quantity = (p.quantity - 1).coerceAtLeast(0))) }, { onChange(p.copy(quantity = (p.quantity + 1).coerceAtMost(10))) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandDialog(initial: Band, onDismiss: () -> Unit, onSave: (Band) -> Unit) {
    var b by remember(initial) { mutableStateOf(initial) }
    var minText by remember(initial) { mutableStateOf(initial.minKg.toString()) }
    var maxText by remember(initial) { mutableStateOf(initial.maxKg.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nouvel élastique" else "Modifier l'élastique") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = b.name, onValueChange = { b = b.copy(name = it) }, label = { Text("Nom / couleur") }, singleLine = true)
                Text("Couleur", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bandPalette.forEach { hex ->
                        Box(
                            Modifier.size(34.dp).background(parseHex(hex), CircleShape)
                                .border(if (b.colorHex == hex) 3.dp else 1.dp, if (b.colorHex == hex) Lime else Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { b = b.copy(colorHex = hex) }
                        )
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Résistance min (kg)") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it.filter(Char::isDigit).take(3) },
                        label = { Text("max (kg)") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Text("Exercices compatibles", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                MultiChipGroup(Catalog.bandUsages.map { ChipItem(it.first, it.second) }, b.usages) { key ->
                    b = b.copy(usages = if (key in b.usages) b.usages - key else b.usages + key)
                }
                OutlinedTextField(value = b.note, onValueChange = { b = b.copy(note = it) }, label = { Text("Référence / note") }, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(b.copy(minKg = minText.toIntOrNull() ?: 0, maxKg = maxText.toIntOrNull() ?: 0))
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
