package com.streetblocks.app.ui.session

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.data.model.ExerciseLog
import com.streetblocks.app.data.model.Progression
import com.streetblocks.app.data.model.timeLabel
import com.streetblocks.app.engine.EngineState
import com.streetblocks.app.engine.Step
import com.streetblocks.app.engine.StepKind
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.Stepper
import com.streetblocks.app.ui.luminanceSafe
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(onExit: () -> Unit) {
    val c = LocalAppContainer.current
    val st by c.engine.state.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }

    KeepAwakeFullScreen()

    BackHandler {
        if (st.running) confirmStop = true else { c.engine.reset(); onExit() }
    }

    when {
        st.running -> RunningView(
            st,
            onClose = { confirmStop = true },
            onPause = c.engine::togglePause,
            onNext = c.engine::skip,
            onPrev = c.engine::previous,
            onAdd = { c.engine.addTime(15) },
        )
        st.finished -> FinishedView(st, onDone = { c.engine.reset(); onExit() })
        else -> LaunchedEffect(Unit) { onExit() }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Arrêter la séance ?") },
            text = { Text("Les séries déjà réalisées seront enregistrées dans l'historique.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; c.engine.stop() }) { Text("Arrêter") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continuer") } },
        )
    }
}

/** Écran toujours allumé, plein écran, visible sur l'écran de verrouillage. */
@Composable
private fun KeepAwakeFullScreen() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            activity?.setShowWhenLocked(true)
            activity?.setTurnScreenOn(true)
        }
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 27) {
                activity?.setShowWhenLocked(false)
                activity?.setTurnScreenOn(false)
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun darken(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, 1f)

private fun backgroundFor(step: Step?): Color = when (step?.kind) {
    StepKind.WORK -> darken(step.blockType?.let(::blockColor) ?: Color(0xFFE53935), 0.72f)
    StepKind.REST -> Color(0xFF0D2B45)
    StepKind.PREP -> Color(0xFFFFC400)
    StepKind.WARMUP -> Color(0xFFD35400)
    StepKind.END, null -> Color(0xFF1B5E20)
}

@Composable
private fun RunningView(
    st: EngineState,
    onClose: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onAdd: () -> Unit,
) {
    val step = st.current ?: return
    val bg by animateColorAsState(backgroundFor(step), label = "bg")
    val fg = if (bg.luminanceSafe() > 0.55f) Color.Black else Color.White
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val secLeft = st.remainingSec
    val lastSeconds = secLeft <= settings.countdownSec && step.durationSec > settings.countdownSec
    val pulse by animateFloatAsState(if (lastSeconds && secLeft % 2 == 0) 1.12f else 1f, label = "pulse")
    val stepProgress = if (st.stepTotalMs > 0) (st.remainingMs.toFloat() / st.stepTotalMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            // Barre du haut
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Arrêter", tint = fg) }
                Column(Modifier.weight(1f)) {
                    Text(st.workoutName, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Étape ${st.index + 1}/${st.steps.size - 1} · ${timeLabel(st.elapsedSec)} écoulées", color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
                if (settings.outdoorMode) Text("EXTÉRIEUR", color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
            LinearProgressIndicator(
                progress = { st.overallProgress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                color = fg, trackColor = fg.copy(alpha = 0.2f), strokeCap = StrokeCap.Round,
            )

            Spacer(Modifier.weight(0.6f))

            Text(step.headline, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                step.title,
                color = fg,
                fontSize = if (step.title.length > 22) 32.sp else 42.sp,
                lineHeight = if (step.title.length > 22) 36.sp else 46.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            if (step.kind == StepKind.WORK || step.kind == StepKind.PREP) {
                Text(step.detail, color = fg, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    step.load?.let {
                        Text(
                            it, color = bg, fontSize = 30.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.background(fg, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                    }
                }
                listOfNotNull(step.loadDetail, step.equipment).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, color = fg.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            } else if (step.kind == StepKind.WARMUP) {
                Text(step.detail, color = fg.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.weight(0.4f))

            // Minuteur géant
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { stepProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = if (lastSeconds) Lime else fg,
                        trackColor = fg.copy(alpha = 0.15f),
                        strokeWidth = 14.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (secLeft >= 60) timeLabel(secLeft) else "$secLeft",
                            color = if (lastSeconds && fg == Color.White) Lime else fg,
                            fontSize = if (secLeft >= 60) 72.sp else 104.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.scale(pulse),
                        )
                        if (st.paused) Text("PAUSE", color = fg, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        else if (step.kind == StepKind.WORK && !step.timed) Text("durée estimée", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }

            // À suivre
            val next = st.next
            if ((step.kind == StepKind.REST || step.kind == StepKind.PREP || step.kind == StepKind.WARMUP) && next != null && next.kind != StepKind.END && next !== step) {
                if (step.kind != StepKind.PREP) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 14.dp).background(fg.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(12.dp),
                    ) {
                        Text("À SUIVRE", color = fg.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                        Text(next.title, color = fg, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(if (next.kind == StepKind.WORK) next.headline.lowercase().replaceFirstChar { it.uppercase() } else null, next.detail.lowercase(), next.load).joinToString(" · "),
                            color = fg.copy(alpha = 0.9f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))

            if (step.kind == StepKind.WORK && !step.timed) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(68.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SÉRIE TERMINÉE", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.SkipPrevious, "Précédent", tint = fg, modifier = Modifier.size(40.dp)) }
                FilledIconButton(
                    onClick = onPause, modifier = Modifier.size(86.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = fg, contentColor = bg),
                ) { Icon(if (st.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "Pause", Modifier.size(48.dp)) }
                IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.SkipNext, "Suivant", tint = fg, modifier = Modifier.size(40.dp)) }
                TextButton(onClick = onAdd) { Text("+15 s", color = fg, fontWeight = FontWeight.Black, fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun FinishedView(st: EngineState, onDone: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val profile by c.equipment.profile.collectAsStateWithLifecycle()
    val bands by c.equipment.bands.collectAsStateWithLifecycle(emptyList())
    val logs = remember(st.startedAt) { mutableStateListOf<ExerciseLog>().also { it.addAll(st.logs) } }
    val applied = remember(st.startedAt) { mutableStateMapOf<String, String>() }
    val isPlanned = st.plannedId != 0L

    fun persist() = c.engine.updateLogs(logs.toList())

    val totalSets = logs.sumOf { it.setsDone }
    val volumeKg = logs.sumOf { it.setsDone * (if (it.timed) 0 else it.repsDone) * it.addedKg }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Lime, modifier = Modifier.size(72.dp))
                Text(if (st.completed) "SÉANCE TERMINÉE" else "SÉANCE ARRÊTÉE", style = MaterialTheme.typography.headlineMedium, color = Lime)
                Text(st.workoutName, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(timeLabel(st.elapsedSec), "durée")
                    Stat("$totalSets", "séries")
                    Stat(if (volumeKg > 0) "${volumeKg.toInt()} kg" else "—", "volume lesté")
                }
            }
        }
        item {
            Text(
                "Indique les répétitions réellement faites (moyenne par série), puis applique la progression proposée pour la prochaine séance.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(logs, key = { _, l -> l.blockId }) { i, log ->
            val advice = Progression.advise(log, settings, profile, bands)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(blockColor(log.type), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(log.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${log.setsDone}/${log.setsPlanned} séries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!log.timed) {
                        Stepper(
                            "Réps réalisées", "${log.repsDone}",
                            { logs[i] = log.copy(repsDone = (log.repsDone - 1).coerceAtLeast(0)); persist() },
                            { logs[i] = log.copy(repsDone = log.repsDone + 1); persist() },
                            sub = "Objectif : ${log.reps}",
                        )
                    }
                    Text("Aujourd'hui : ${advice.today}", fontWeight = FontWeight.Bold)
                    Text("Prochaine séance : ${advice.next}", color = Lime)
                    if (advice.options.isNotEmpty() && (isPlanned || st.workoutId != 0L)) {
                        if (log.blockId in applied) {
                            Text(applied.getValue(log.blockId), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        } else {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                advice.options.forEach { opt ->
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            if (isPlanned) {
                                                // Programme : uniquement les prochaines séances de ce créneau, jamais la séance d'origine
                                                val n = c.programs.applyProgression(st.plannedId, log.key, opt.apply)
                                                applied[log.blockId] = if (n > 0) "✓ Appliquée aux $n prochaines séances de ce créneau" else "Aucune séance suivante à ajuster dans le programme"
                                            } else if (c.workouts.updateBlock(st.workoutId, log.blockId, opt.apply)) {
                                                applied[log.blockId] = "✓ Progression appliquée à la séance"
                                            }
                                        }
                                    }, modifier = Modifier.weight(1f)) { Text(opt.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { persist(); onDone() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
            ) { Text("TERMINER", fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
