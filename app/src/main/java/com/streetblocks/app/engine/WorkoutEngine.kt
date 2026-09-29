package com.streetblocks.app.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.streetblocks.app.audio.AudioCues
import com.streetblocks.app.audio.AudioCues.Cue
import com.streetblocks.app.data.SessionRepository
import com.streetblocks.app.data.WorkoutRepository
import com.streetblocks.app.data.model.AppSettings
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockText
import com.streetblocks.app.data.model.ExerciseLog
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.SessionRecord
import com.streetblocks.app.data.model.Workout
import com.streetblocks.app.service.WorkoutService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

data class EngineState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val paused: Boolean = false,
    val completed: Boolean = false,
    val workoutId: Long = 0,
    val workoutName: String = "",
    val steps: List<Step> = emptyList(),
    val index: Int = 0,
    val remainingMs: Long = 0,
    val stepTotalMs: Long = 0,
    val elapsedSec: Int = 0,
    val startedAt: Long = 0,
    val sessionId: Long = 0,
    val logs: List<ExerciseLog> = emptyList(),
) {
    val current: Step? get() = steps.getOrNull(index)

    /** Prochaine étape « utile » (hors repos). */
    val next: Step? get() = steps.drop(index + 1).firstOrNull { it.kind != StepKind.REST }

    val remainingSec: Int get() = ceil(remainingMs / 1000.0).toInt()

    val totalSec: Int get() = steps.sumOf { it.durationSec }

    val overallProgress: Float
        get() {
            val total = totalSec.coerceAtLeast(1)
            val before = steps.take(index).sumOf { it.durationSec }
            val inStep = ((stepTotalMs - remainingMs) / 1000.0).coerceAtLeast(0.0)
            return ((before + inStep) / total).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Moteur d'exécution automatique : enchaîne les étapes, gère les décomptes,
 * déclenche sons / voix / vibrations et enregistre la séance à la fin.
 * Vit au niveau de l'application pour survivre aux changements d'écran.
 */
class WorkoutEngine(
    private val context: Context,
    private val audio: AudioCues,
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var endAt = 0L
    private var pausedRemaining = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var startedElapsed = 0L
    private var lastBeepSec = -1
    private var announced10 = false
    private var settings = AppSettings()
    private var blocks: List<Block> = emptyList()
    private var bands: List<Band> = emptyList()
    private val doneSets = HashMap<String, Int>()

    fun start(workout: Workout, steps: List<Step>, s: AppSettings, bands: List<Band>) {
        stopLoop()
        settings = s
        blocks = workout.blocks
        this.bands = bands
        doneSets.clear()
        pausedTotal = 0
        startedElapsed = SystemClock.elapsedRealtime()
        audio.configure(s)
        audio.beginSession()
        _state.value = EngineState(
            running = true, workoutId = workout.id, workoutName = workout.name, steps = steps,
            index = 0, startedAt = System.currentTimeMillis(),
        )
        enterStep(0, previous = null)
        loopJob = scope.launch { loop() }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        }
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val st = _state.value
            if (!st.running) break
            if (!st.paused) {
                val now = SystemClock.elapsedRealtime()
                val rem = endAt - now
                if (rem <= 0) {
                    advance()
                } else {
                    val sec = ceil(rem / 1000.0).toInt()
                    val cur = st.current
                    if (cur != null && sec <= settings.countdownSec && sec != lastBeepSec && sec > 0 && cur.durationSec > settings.countdownSec) {
                        lastBeepSec = sec
                        audio.play(if (sec == 1) Cue.FINAL_TICK else Cue.TICK)
                    }
                    if (cur != null && cur.kind == StepKind.REST && !announced10 && sec == 10 && cur.durationSec >= 25) {
                        announced10 = true
                        audio.speak("Dix secondes. Prépare-toi.")
                    }
                    _state.value = st.copy(remainingMs = rem, elapsedSec = elapsed())
                }
            }
            delay(100)
        }
    }

    private fun elapsed(): Int {
        val now = SystemClock.elapsedRealtime()
        val pausedNow = if (_state.value.paused) now - pausedAt else 0
        return ((now - startedElapsed - pausedTotal - pausedNow) / 1000).toInt()
    }

    private fun enterStep(i: Int, previous: Step?) {
        val st = _state.value
        val step = st.steps.getOrNull(i) ?: return
        if (step.kind == StepKind.END) {
            finish(completed = true)
            return
        }
        val ms = step.durationSec * 1000L
        endAt = SystemClock.elapsedRealtime() + ms
        lastBeepSec = -1
        announced10 = false
        _state.value = st.copy(index = i, remainingMs = ms, stepTotalMs = ms, paused = false)

        audio.cancelSpeech()
        val cue = when (step.kind) {
            StepKind.PREP -> Cue.CHANGE
            StepKind.WARMUP -> if (previous?.kind == StepKind.WARMUP) Cue.HALF else Cue.START
            StepKind.REST -> if (previous?.kind == StepKind.WORK) Cue.SET_END else Cue.CHANGE
            StepKind.WORK -> if (previous == null || previous.kind == StepKind.PREP) Cue.START else Cue.GO
            StepKind.END -> Cue.SESSION_END
        }
        audio.play(cue)
        audio.speak(step.speech, audio.cueMs(cue) + 150)
    }

    private fun advance() {
        val st = _state.value
        val cur = st.current
        if (cur?.kind == StepKind.WORK && cur.blockId != null) {
            doneSets[cur.blockId] = (doneSets[cur.blockId] ?: 0) + 1
        }
        val nextIndex = st.index + 1
        if (nextIndex >= st.steps.size) finish(true) else enterStep(nextIndex, cur)
    }

    // ------------------------------------------------------------ commandes

    fun togglePause() {
        val st = _state.value
        if (!st.running) return
        val now = SystemClock.elapsedRealtime()
        if (st.paused) {
            endAt = now + pausedRemaining
            pausedTotal += now - pausedAt
            _state.value = st.copy(paused = false)
            audio.play(Cue.HALF)
        } else {
            pausedRemaining = (endAt - now).coerceAtLeast(0)
            pausedAt = now
            _state.value = st.copy(paused = true, remainingMs = pausedRemaining)
            audio.cancelSpeech()
        }
    }

    /** Passe à l'étape suivante (ex : série terminée plus tôt que prévu). */
    fun skip() {
        val st = _state.value
        if (!st.running) return
        if (st.paused) {
            pausedTotal += SystemClock.elapsedRealtime() - pausedAt
        }
        advance()
    }

    fun previous() {
        val st = _state.value
        if (!st.running || st.index == 0) return
        if (st.paused) pausedTotal += SystemClock.elapsedRealtime() - pausedAt
        val target = st.index - 1
        val targetStep = st.steps[target]
        if (targetStep.kind == StepKind.WORK && targetStep.blockId != null) {
            doneSets[targetStep.blockId] = ((doneSets[targetStep.blockId] ?: 0) - 1).coerceAtLeast(0)
        }
        enterStep(target, st.steps.getOrNull(target - 1))
    }

    fun addTime(sec: Int) {
        val st = _state.value
        if (!st.running) return
        val add = sec * 1000L
        if (st.paused) pausedRemaining += add else endAt += add
        _state.value = st.copy(stepTotalMs = st.stepTotalMs + add, remainingMs = st.remainingMs + add)
    }

    /** Arrêt manuel : la séance est enregistrée si au moins une série a été faite. */
    fun stop() {
        val st = _state.value
        if (!st.running) return
        if (doneSets.values.sum() == 0) {
            stopLoop()
            audio.cancelSpeech()
            audio.endSession()
            _state.value = EngineState()
        } else {
            finish(completed = false)
        }
    }

    /** Après l'écran de fin. */
    fun reset() {
        stopLoop()
        _state.value = EngineState()
    }

    fun updateLogs(logs: List<ExerciseLog>) {
        val st = _state.value
        _state.value = st.copy(logs = logs)
        if (st.sessionId != 0L) scope.launch { withContext(Dispatchers.IO) { sessions.updateLogs(st.sessionId, logs) } }
    }

    private fun finish(completed: Boolean) {
        val st = _state.value
        stopLoop()
        val logs = buildLogs(st)
        val duration = elapsed()
        if (completed) {
            audio.play(Cue.SESSION_END)
            audio.speak("Séance terminée. Bravo !", audio.cueMs(Cue.SESSION_END) + 150)
        } else {
            audio.cancelSpeech()
        }
        _state.value = st.copy(
            running = false, finished = true, completed = completed, paused = false,
            remainingMs = 0, elapsedSec = duration, logs = logs,
        )
        scope.launch {
            // Laisse le temps au dernier son d'être joué avant de restaurer le volume
            delay(4000)
            if (!_state.value.running) audio.endSession()
        }
        scope.launch {
            val id = withContext(NonCancellable + Dispatchers.IO) {
                sessions.insert(
                    SessionRecord(
                        id = 0, workoutId = st.workoutId, workoutName = st.workoutName, startedAt = st.startedAt,
                        durationSec = duration, completed = completed, logs = logs,
                    )
                ).also { workouts.markPerformed(st.workoutId) }
            }
            val now = _state.value
            if (now.finished && now.workoutId == st.workoutId) _state.value = now.copy(sessionId = id)
        }
    }

    private fun buildLogs(st: EngineState): List<ExerciseLog> {
        val ids = st.steps.filter { it.kind == StepKind.WORK }.mapNotNull { it.blockId }.distinct()
        return ids.mapNotNull { id ->
            val b = blocks.firstOrNull { it.id == id } ?: return@mapNotNull null
            val band = if (b.loadMode == LoadMode.BAND) BlockText.band(b, bands) else null
            ExerciseLog(
                blockId = b.id, key = BlockText.progressKey(b), name = BlockText.exerciseLabel(b), type = b.type,
                setsPlanned = b.sets, setsDone = doneSets[id] ?: 0, reps = b.reps, repsDone = b.reps,
                timed = b.timed, workSec = b.workSec, addedKg = b.addedKg, loadMode = b.loadMode,
                bandId = band?.id, bandName = band?.name,
            )
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }
}
