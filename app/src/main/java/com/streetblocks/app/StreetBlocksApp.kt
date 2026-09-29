package com.streetblocks.app

import android.app.Application
import com.streetblocks.app.audio.AudioCues
import com.streetblocks.app.data.EquipmentRepository
import com.streetblocks.app.data.ProgramRepository
import com.streetblocks.app.data.SessionRepository
import com.streetblocks.app.data.SettingsRepository
import com.streetblocks.app.data.WorkoutRepository
import com.streetblocks.app.data.db.AppDatabase
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.WorkoutTemplate
import com.streetblocks.app.engine.StepBuilder
import com.streetblocks.app.engine.StepKind
import com.streetblocks.app.engine.WorkoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreetBlocksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Injection de dépendances manuelle (simple et sans magie). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.get(app)

    val settings = SettingsRepository(app)
    val equipment = EquipmentRepository(app, db.bandDao())
    val workouts = WorkoutRepository(db.workoutDao())
    val sessions = SessionRepository(db.sessionDao())
    val programs = ProgramRepository(db, workouts) { equipment.bandsNow() }
    val audio = AudioCues(app)
    val engine = WorkoutEngine(app, audio, workouts, sessions, programs)

    init {
        audio.configure(settings.settings.value)
        appScope.launch { equipment.seedIfNeeded() }
    }

    /** Prépare et lance une séance. Retourne false si la séance est vide. */
    suspend fun launchSession(workoutId: Long): Boolean {
        val w = workouts.get(workoutId) ?: return false
        val bands = equipment.bandsNow()
        val s = settings.settings.value
        val steps = StepBuilder.build(w.blocks, bands, equipment.profile.value, s)
        if (steps.none { it.kind != StepKind.END }) return false
        kotlinx.coroutines.withContext(Dispatchers.Main) { engine.start(w, steps, s, bands) }
        return true
    }

    /** Lance une séance planifiée d'un programme (avec sa propre configuration). */
    suspend fun launchPlanned(plannedId: Long): Boolean {
        val p = programs.getPlanned(plannedId) ?: return false
        val w = com.streetblocks.app.data.model.Workout(
            id = p.sourceWorkoutId ?: 0L, name = p.name, description = "", blocks = p.blocks,
            createdAt = 0, updatedAt = 0, lastPerformedAt = null,
        )
        val bands = equipment.bandsNow()
        val s = settings.settings.value
        val steps = StepBuilder.build(w.blocks, bands, equipment.profile.value, s)
        if (steps.none { it.kind != StepKind.END }) return false
        kotlinx.coroutines.withContext(Dispatchers.Main) { engine.start(w, steps, s, bands, plannedId = p.id) }
        return true
    }

    /** Crée une séance à partir d'un modèle, en choisissant un élastique adapté si besoin. */
    suspend fun createFromTemplate(t: WorkoutTemplate): Long {
        val bands = equipment.bandsNow()
        val blocks = com.streetblocks.app.data.model.Catalog.withBands(t.blocks(), bands)
        return workouts.create(t.name, blocks, t.description)
    }
}
