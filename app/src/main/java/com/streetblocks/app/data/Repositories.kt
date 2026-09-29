package com.streetblocks.app.data

import android.content.Context
import com.streetblocks.app.data.db.BandDao
import com.streetblocks.app.data.db.BandEntity
import com.streetblocks.app.data.db.SessionDao
import com.streetblocks.app.data.db.SessionEntity
import com.streetblocks.app.data.db.WorkoutDao
import com.streetblocks.app.data.db.WorkoutEntity
import com.streetblocks.app.data.model.AppJson
import com.streetblocks.app.data.model.AppSettings
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.ExerciseLog
import com.streetblocks.app.data.model.SessionRecord
import com.streetblocks.app.data.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class WorkoutRepository(private val dao: WorkoutDao) {

    val workouts: Flow<List<Workout>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Workout? = dao.get(id)?.toDomain()

    suspend fun create(name: String, blocks: List<Block>, description: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            WorkoutEntity(
                name = name, description = description, blocksJson = encode(blocks),
                createdAt = now, updatedAt = now,
            )
        )
    }

    suspend fun save(w: Workout) {
        dao.update(
            WorkoutEntity(
                id = w.id, name = w.name, description = w.description, blocksJson = encode(w.blocks),
                createdAt = w.createdAt, updatedAt = System.currentTimeMillis(), lastPerformedAt = w.lastPerformedAt,
            )
        )
    }

    suspend fun duplicate(id: Long): Long? {
        val w = get(id) ?: return null
        return create("${w.name} (copie)", w.blocks.map { it.copyNew() }, w.description)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markPerformed(id: Long) = dao.markPerformed(id, System.currentTimeMillis())

    suspend fun updateBlock(workoutId: Long, blockId: String, transform: (Block) -> Block): Boolean {
        val w = get(workoutId) ?: return false
        if (w.blocks.none { it.id == blockId }) return false
        save(w.copy(blocks = w.blocks.map { if (it.id == blockId) transform(it) else it }))
        return true
    }

    private fun encode(blocks: List<Block>) = AppJson.encodeToString(blocks)

    private fun WorkoutEntity.toDomain() = Workout(
        id = id, name = name, description = description,
        blocks = runCatching { AppJson.decodeFromString<List<Block>>(blocksJson) }.getOrDefault(emptyList()),
        createdAt = createdAt, updatedAt = updatedAt, lastPerformedAt = lastPerformedAt,
    )
}

class SessionRepository(private val dao: SessionDao) {

    val sessions: Flow<List<SessionRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun insert(r: SessionRecord): Long = dao.insert(
        SessionEntity(
            workoutId = r.workoutId, workoutName = r.workoutName, startedAt = r.startedAt,
            durationSec = r.durationSec, completed = r.completed, logsJson = AppJson.encodeToString(r.logs),
            plannedId = r.plannedId,
        )
    )

    suspend fun updateLogs(id: Long, logs: List<ExerciseLog>) = dao.updateLogs(id, AppJson.encodeToString(logs))

    suspend fun delete(id: Long) = dao.delete(id)

    private fun SessionEntity.toDomain() = SessionRecord(
        id = id, workoutId = workoutId, workoutName = workoutName, startedAt = startedAt,
        durationSec = durationSec, completed = completed,
        logs = runCatching { AppJson.decodeFromString<List<ExerciseLog>>(logsJson) }.getOrDefault(emptyList()),
        plannedId = plannedId,
    )
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings =
        prefs.getString("json", null)?.let { runCatching { AppJson.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: AppSettings()

    fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(_settings.value)
        _settings.value = n
        prefs.edit().putString("json", AppJson.encodeToString(n)).apply()
    }
}

class EquipmentRepository(context: Context, private val bandDao: BandDao) {
    private val prefs = context.getSharedPreferences("equipment", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<EquipmentProfile> = _profile.asStateFlow()

    val bands: Flow<List<Band>> = bandDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun bandsNow(): List<Band> = bandDao.getAll().map { it.toDomain() }

    private fun loadProfile(): EquipmentProfile =
        prefs.getString("profile", null)?.let { runCatching { AppJson.decodeFromString<EquipmentProfile>(it) }.getOrNull() }
            ?: EquipmentProfile()

    fun updateProfile(transform: (EquipmentProfile) -> EquipmentProfile) {
        val n = transform(_profile.value)
        _profile.value = n
        prefs.edit().putString("profile", AppJson.encodeToString(n)).apply()
    }

    suspend fun saveBand(b: Band) {
        val e = BandEntity(
            id = b.id, name = b.name.ifBlank { "Élastique" }, colorHex = b.colorHex,
            minKg = minOf(b.minKg, b.maxKg), maxKg = maxOf(b.minKg, b.maxKg),
            usages = b.usages.joinToString(","), note = b.note,
        )
        if (b.id == 0L) bandDao.insert(e) else bandDao.update(e)
    }

    suspend fun deleteBand(id: Long) = bandDao.delete(id)

    /** Base d'élastiques type Decathlon, modifiable ensuite dans « Mon matériel ». */
    suspend fun seedIfNeeded() {
        if (prefs.getBoolean("bands_seeded", false)) return
        if (bandDao.count() == 0) {
            val note = "Référence type Decathlon – ajuste la résistance à ton modèle"
            listOf(
                Band(name = "Jaune", colorHex = "#FDD835", minKg = 5, maxKg = 10, usages = setOf("MOBILITE", "POMPES"), note = note),
                Band(name = "Rouge", colorHex = "#E53935", minKg = 10, maxKg = 20, usages = setOf("MOBILITE", "POMPES", "DIPS"), note = note),
                Band(name = "Noir", colorHex = "#424242", minKg = 15, maxKg = 25, usages = setOf("TRACTION", "DIPS", "MUSCLE_UP", "POMPES"), note = note),
                Band(name = "Violet", colorHex = "#8E24AA", minKg = 25, maxKg = 35, usages = setOf("TRACTION", "DIPS", "MUSCLE_UP"), note = note),
                Band(name = "Vert", colorHex = "#43A047", minKg = 35, maxKg = 55, usages = setOf("TRACTION", "MUSCLE_UP"), note = note),
                Band(name = "Bleu", colorHex = "#1E88E5", minKg = 45, maxKg = 75, usages = setOf("TRACTION", "MUSCLE_UP"), note = note),
            ).forEach { saveBand(it) }
        }
        prefs.edit().putBoolean("bands_seeded", true).apply()
    }

    private fun BandEntity.toDomain() = Band(
        id = id, name = name, colorHex = colorHex, minKg = minKg, maxKg = maxKg,
        usages = usages.split(",").filter { it.isNotBlank() }.toSet(), note = note,
    )
}
