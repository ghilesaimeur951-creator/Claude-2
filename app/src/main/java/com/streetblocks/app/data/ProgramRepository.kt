package com.streetblocks.app.data

import androidx.room.withTransaction
import com.streetblocks.app.data.db.AppDatabase
import com.streetblocks.app.data.db.PlannedSessionEntity
import com.streetblocks.app.data.db.ProgramEntity
import com.streetblocks.app.data.model.AppJson
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.Catalog
import com.streetblocks.app.data.model.EditScope
import com.streetblocks.app.data.model.PlanChange
import com.streetblocks.app.data.model.PlanStatus
import com.streetblocks.app.data.model.PlannedSession
import com.streetblocks.app.data.model.Program
import com.streetblocks.app.data.model.ProgramOps
import com.streetblocks.app.data.model.ProgramSpec
import com.streetblocks.app.data.model.SessionContent
import com.streetblocks.app.data.model.SessionSource
import com.streetblocks.app.data.model.Templates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.DayOfWeek
import java.time.LocalDate

class ProgramRepository(
    private val db: AppDatabase,
    private val workouts: WorkoutRepository,
    private val bands: suspend () -> List<Band>,
) {
    private val dao = db.programDao()

    val programs: Flow<List<Program>> = dao.observePrograms().map { l -> l.map { it.toDomain() } }
    val allPlanned: Flow<List<PlannedSession>> = dao.observeAllPlanned().map { l -> l.map { it.toDomain() } }

    fun program(id: Long): Flow<Program?> = dao.observeProgram(id).map { it?.toDomain() }
    fun planned(programId: Long): Flow<List<PlannedSession>> = dao.observePlanned(programId).map { l -> l.map { it.toDomain() } }

    suspend fun getProgram(id: Long): Program? = dao.getProgram(id)?.toDomain()
    suspend fun getPlanned(id: Long): PlannedSession? = dao.getPlanned(id)?.toDomain()
    private suspend fun plannedOf(programId: Long) = dao.plannedOf(programId).map { it.toDomain() }

    /** Contenu d'une séance à partir d'une séance sauvegardée, d'un modèle, ou vide. */
    suspend fun resolve(source: SessionSource): SessionContent = when (source) {
        is SessionSource.Saved -> workouts.get(source.workoutId)
            ?.let { w -> SessionContent(w.name, w.blocks.map { it.copyNew() }, w.id) }
            ?: emptyContent()
        is SessionSource.Template -> Templates.all.firstOrNull { it.key == source.key }
            ?.let { t -> SessionContent(t.name, Catalog.withBands(t.blocks(), bands()), null) }
            ?: emptyContent()
        SessionSource.Empty -> emptyContent()
    }

    private fun emptyContent() = SessionContent("Nouvelle séance", listOf(Block.default(BlockType.WARMUP), Block.default(BlockType.END)), null)

    suspend fun create(spec: ProgramSpec): Long {
        val contents = spec.slots.associate { it.day to resolve(it.source) }
        return db.withTransaction {
            val id = dao.insertProgram(
                ProgramEntity(
                    name = spec.name.ifBlank { "Mon programme" }, goal = spec.goal,
                    startEpochDay = spec.start.toEpochDay(), endEpochDay = spec.end.toEpochDay(),
                    trainingDays = spec.slots.map { it.day.value }.sorted().joinToString(","),
                    createdAt = System.currentTimeMillis(),
                )
            )
            dao.insertPlanned(ProgramOps.generate(id, spec) { contents.getValue(it.day) }.map { it.toEntity() })
            id
        }
    }

    suspend fun updateInfo(id: Long, name: String, goal: String, end: LocalDate? = null) {
        val p = dao.getProgram(id) ?: return
        dao.updateProgram(p.copy(name = name, goal = goal, endEpochDay = end?.toEpochDay() ?: p.endEpochDay))
    }

    /** Supprime le programme. L'historique des séances réalisées est conservé. */
    suspend fun deleteProgram(id: Long) = db.withTransaction {
        dao.deletePlannedOfProgram(id)
        dao.deleteProgram(id)
    }

    private suspend fun apply(change: PlanChange): Int {
        if (change.count == 0) return 0
        db.withTransaction {
            if (change.delete.isNotEmpty()) dao.deletePlanned(change.delete)
            if (change.update.isNotEmpty()) dao.updatePlanned(change.update.map { it.toEntity() })
            if (change.insert.isNotEmpty()) dao.insertPlanned(change.insert.map { it.toEntity() })
        }
        return change.count
    }

    private suspend fun withTarget(id: Long, op: (List<PlannedSession>, PlannedSession) -> PlanChange): Int {
        val target = getPlanned(id) ?: return 0
        return apply(op(plannedOf(target.programId), target))
    }

    suspend fun followingCount(id: Long): Int {
        val t = getPlanned(id) ?: return 0
        return com.streetblocks.app.data.model.Planning.following(plannedOf(t.programId), t).size
    }

    /** Sauvegarde du contenu d'UNE occurrence (éditeur de blocs). */
    suspend fun saveContent(id: Long, name: String, blocks: List<Block>) {
        val p = getPlanned(id) ?: return
        if (!p.editable) return
        dao.updatePlanned(listOf(p.copy(name = name, blocks = blocks).toEntity()))
    }

    suspend fun propagateContent(id: Long): Int = withTarget(id) { all, t -> ProgramOps.propagate(all, t) }

    suspend fun edit(id: Long, scope: EditScope, transform: (PlannedSession) -> PlannedSession): Int =
        withTarget(id) { all, t -> ProgramOps.edit(all, t, scope, transform) }

    suspend fun replaceWith(id: Long, scope: EditScope, source: SessionSource): Int {
        val c = resolve(source)
        return edit(id, scope) { it.copy(name = c.name, blocks = c.blocks, sourceWorkoutId = c.sourceWorkoutId) }
    }

    suspend fun move(id: Long, date: LocalDate, scope: EditScope): Int = withTarget(id) { all, t -> ProgramOps.move(all, t, date, scope) }

    suspend fun duplicateTo(id: Long, date: LocalDate): Int = withTarget(id) { _, t -> ProgramOps.duplicateTo(t, date) }

    suspend fun duplicateWeek(programId: Long, week: LocalDate, target: LocalDate): Int =
        apply(ProgramOps.duplicateWeek(plannedOf(programId), week, target))

    suspend fun repeatWeek(programId: Long, week: LocalDate): Int {
        val p = getProgram(programId) ?: return 0
        return apply(ProgramOps.repeatWeek(plannedOf(programId), week, p.end))
    }

    suspend fun delete(id: Long, scope: EditScope): Int = withTarget(id) { all, t -> ProgramOps.delete(all, t, scope) }

    suspend fun add(programId: Long, date: LocalDate, source: SessionSource, time: Int?): Long {
        val c = resolve(source)
        val e = ProgramOps.duplicateTo(
            PlannedSession(0, programId, "", date, time, c.name, c.blocks, c.sourceWorkoutId, PlanStatus.PLANNED, null, null, ""),
            date,
        ).insert.first()
        return dao.insertPlanned(listOf(e.toEntity())).first()
    }

    suspend fun setStatus(id: Long, status: PlanStatus) {
        val p = getPlanned(id) ?: return
        if (p.status == PlanStatus.DONE && status != PlanStatus.DONE && p.sessionId != null) {
            // Replanifier une séance réalisée : l'historique est conservé, seule la planification change
            dao.updatePlanned(listOf(p.copy(status = status, sessionId = null, completedAt = null).toEntity()))
        } else {
            dao.updatePlanned(listOf(p.copy(status = status).toEntity()))
        }
    }

    suspend fun markDone(id: Long, sessionId: Long) {
        val p = getPlanned(id) ?: return
        dao.updatePlanned(listOf(p.copy(status = PlanStatus.DONE, sessionId = sessionId, completedAt = System.currentTimeMillis()).toEntity()))
    }

    suspend fun applyProgression(plannedId: Long, key: String, transform: (Block) -> Block): Int =
        withTarget(plannedId) { all, t -> ProgramOps.applyProgression(all, t, key, transform) }

    // ------------------------------------------------------------ mapping

    private fun ProgramEntity.toDomain() = Program(
        id = id, name = name, goal = goal,
        start = LocalDate.ofEpochDay(startEpochDay), end = LocalDate.ofEpochDay(endEpochDay),
        trainingDays = trainingDays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet(),
        createdAt = createdAt,
    )

    private fun PlannedSessionEntity.toDomain() = PlannedSession(
        id = id, programId = programId, seriesId = seriesId, date = LocalDate.ofEpochDay(dateEpochDay),
        timeMinutes = timeMinutes, name = name,
        blocks = runCatching { AppJson.decodeFromString<List<Block>>(blocksJson) }.getOrDefault(emptyList()),
        sourceWorkoutId = sourceWorkoutId,
        status = runCatching { PlanStatus.valueOf(status) }.getOrDefault(PlanStatus.PLANNED),
        sessionId = sessionId, completedAt = completedAt, note = note,
    )

    private fun PlannedSession.toEntity() = PlannedSessionEntity(
        id = id, programId = programId, seriesId = seriesId, dateEpochDay = date.toEpochDay(), timeMinutes = timeMinutes,
        name = name, blocksJson = AppJson.encodeToString(blocks), sourceWorkoutId = sourceWorkoutId,
        status = status.name, sessionId = sessionId, completedAt = completedAt, note = note,
    )
}
