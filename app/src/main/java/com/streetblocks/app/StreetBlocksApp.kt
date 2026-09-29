package com.streetblocks.app

import android.app.Application
import com.streetblocks.app.audio.AudioCues
import com.streetblocks.app.data.EquipmentRepository
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
    val audio = AudioCues(app)
    val engine = WorkoutEngine(app, audio, workouts, sessions)

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

    /** Crée une séance à partir d'un modèle, en choisissant un élastique adapté si besoin. */
    suspend fun createFromTemplate(t: WorkoutTemplate): Long {
        val bands = equipment.bandsNow()
        val blocks = t.blocks().map { b -> if (b.loadMode == LoadMode.BAND && b.bandId == null) b.copy(bandId = pickBand(b, bands)) else b }
        return workouts.create(t.name, blocks, t.description)
    }

    private fun pickBand(b: Block, bands: List<Band>): Long? {
        val usage = com.streetblocks.app.data.model.Catalog.bandUsageFor(b)
        val compatible = bands.filter { usage == null || usage in it.usages }.ifEmpty { bands }
        return compatible.getOrNull(compatible.size / 2)?.id
    }
}
