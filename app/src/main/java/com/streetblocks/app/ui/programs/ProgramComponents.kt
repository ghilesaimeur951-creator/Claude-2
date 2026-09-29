package com.streetblocks.app.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.data.model.EditScope
import com.streetblocks.app.data.model.SessionSource
import com.streetblocks.app.data.model.Templates
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.theme.Lime
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

/**
 * Choix explicite de la portée d'une modification.
 * Sans séance suivante, rien n'est demandé : la modification ne concerne que cette séance.
 */
@Composable
fun ScopeDialog(
    title: String,
    followingCount: Int,
    onPick: (EditScope) -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (detail != null) Text(detail)
                Button(
                    onClick = { onPick(EditScope.THIS) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
                ) { Text("Uniquement cette séance", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { onPick(EditScope.FOLLOWING) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cette séance et les $followingCount suivantes du même créneau")
                }
                Text(
                    "Les séances déjà réalisées et leur historique ne sont jamais modifiés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, title: String, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    ) {
        DatePicker(state = state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initialMinutes: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val init = initialMinutes ?: (18 * 60)
    val state = rememberTimePickerState(initialHour = init / 60, initialMinute = init % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horaire (facultatif)") },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("Sans horaire") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/** Choix du contenu d'une séance : nouvelle (éditeur de blocs), sauvegardée ou modèle. */
@Composable
fun SourcePickerDialog(title: String, onDismiss: () -> Unit, onPick: (SessionSource, String) -> Unit) {
    val c = LocalAppContainer.current
    val workouts by c.workouts.workouts.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    PickRow("➕ Nouvelle séance (à construire avec les blocs)") { onPick(SessionSource.Empty, "Nouvelle séance") }
                }
                if (workouts.isNotEmpty()) {
                    item { Header("Mes séances sauvegardées") }
                    items(workouts, key = { "w${it.id}" }) { w ->
                        PickRow("${w.name}  ·  ${w.blocks.count { it.isExercise }} exercices") { onPick(SessionSource.Saved(w.id), w.name) }
                    }
                }
                item { Header("Modèles") }
                items(Templates.all, key = { "t${it.key}" }) { t ->
                    PickRow(t.name) { onPick(SessionSource.Template(t.key), t.name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun Header(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Lime, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun PickRow(text: String, onClick: () -> Unit) {
    Column {
        Text(text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
        HorizontalDivider()
    }
}
