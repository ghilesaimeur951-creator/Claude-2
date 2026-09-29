package com.streetblocks.app.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SessionContent(val name: String, val blocks: List<Block>, val sourceWorkoutId: Long?)

/** Changements à appliquer en base (logique pure, testable sans base de données). */
data class PlanChange(
    val insert: List<PlannedSession> = emptyList(),
    val update: List<PlannedSession> = emptyList(),
    val delete: List<Long> = emptyList(),
) {
    val count: Int get() = insert.size + update.size + delete.size
}

/**
 * Opérations de planification. Règles :
 *  - une séance RÉALISÉE n'est jamais modifiée, déplacée ni supprimée ;
 *  - « cette séance et les suivantes » = même créneau hebdomadaire (seriesId), à partir de cette date ;
 *  - chaque occurrence garde sa propre copie des blocs.
 */
object ProgramOps {

    fun newSeries(): String = "s-" + UUID.randomUUID().toString()

    private fun blank(programId: Long, series: String, date: LocalDate, time: Int?, c: SessionContent) = PlannedSession(
        id = 0, programId = programId, seriesId = series, date = date, timeMinutes = time,
        name = c.name, blocks = c.blocks, sourceWorkoutId = c.sourceWorkoutId,
        status = PlanStatus.PLANNED, sessionId = null, completedAt = null, note = "",
    )

    /** Génère toutes les occurrences : l'organisation hebdomadaire est répétée sur toute la période. */
    fun generate(programId: Long, spec: ProgramSpec, content: (DaySlot) -> SessionContent): List<PlannedSession> {
        val bySlot = spec.slots.associate { slot -> slot.day to Triple(newSeries(), slot, content(slot)) }
        return Planning.datesFor(spec.start, spec.end, bySlot.keys).map { d ->
            val (series, slot, c) = bySlot.getValue(d.dayOfWeek)
            blank(programId, series, d, slot.timeMinutes, c)
        }
    }

    private fun targets(all: List<PlannedSession>, target: PlannedSession, scope: EditScope): List<PlannedSession> =
        (listOf(target).filter { it.editable } + if (scope == EditScope.FOLLOWING) Planning.following(all, target) else emptyList())
            .distinctBy { it.id }

    fun edit(all: List<PlannedSession>, target: PlannedSession, scope: EditScope, transform: (PlannedSession) -> PlannedSession): PlanChange =
        PlanChange(update = targets(all, target, scope).map { transform(it).copy(id = it.id, status = it.status, sessionId = it.sessionId) })

    /** Copie le contenu (nom + blocs) de [source] vers ses occurrences suivantes. */
    fun propagate(all: List<PlannedSession>, source: PlannedSession): PlanChange =
        PlanChange(update = Planning.following(all, source).map { it.copy(name = source.name, blocks = source.blocks) })

    /** Déplace une séance ; avec FOLLOWING, décale toutes les suivantes du même nombre de jours. */
    fun move(all: List<PlannedSession>, target: PlannedSession, newDate: LocalDate, scope: EditScope): PlanChange {
        val delta = ChronoUnit.DAYS.between(target.date, newDate)
        if (delta == 0L) return PlanChange()
        return PlanChange(update = targets(all, target, scope).map { it.copy(date = it.date.plusDays(delta)) })
    }

    /** Copie indépendante d'une séance à une autre date. */
    fun duplicateTo(target: PlannedSession, date: LocalDate): PlanChange =
        PlanChange(insert = listOf(blank(target.programId, newSeries(), date, target.timeMinutes, SessionContent(target.name, target.blocks, target.sourceWorkoutId))))

    /** Copie toutes les séances d'une semaine vers une autre semaine. */
    fun duplicateWeek(all: List<PlannedSession>, weekStart: LocalDate, targetWeekStart: LocalDate): PlanChange {
        val from = Planning.weekStart(weekStart)
        val to = Planning.weekStart(targetWeekStart)
        val delta = ChronoUnit.DAYS.between(from, to)
        if (delta == 0L) return PlanChange()
        val seriesMap = HashMap<String, String>()
        val src = all.filter { !it.date.isBefore(from) && it.date.isBefore(from.plusDays(7)) }
        return PlanChange(insert = src.map { s ->
            blank(s.programId, seriesMap.getOrPut(s.seriesId) { newSeries() }, s.date.plusDays(delta), s.timeMinutes, SessionContent(s.name, s.blocks, s.sourceWorkoutId))
        })
    }

    /**
     * Répète l'organisation d'une semaine sur toutes les semaines suivantes jusqu'à [end] :
     * les séances non réalisées de ces semaines sont remplacées, les séances réalisées sont conservées.
     */
    fun repeatWeek(all: List<PlannedSession>, weekStart: LocalDate, end: LocalDate): PlanChange {
        val from = Planning.weekStart(weekStart)
        val src = all.filter { !it.date.isBefore(from) && it.date.isBefore(from.plusDays(7)) }
        if (src.isEmpty()) return PlanChange()
        val nextWeek = from.plusDays(7)
        val delete = all.filter { !it.date.isBefore(nextWeek) && !it.date.isAfter(end) && it.status != PlanStatus.DONE }.map { it.id }
        val doneDates = all.filter { !it.date.isBefore(nextWeek) && it.status == PlanStatus.DONE }.map { it.date to it.seriesId }.toSet()
        val insert = mutableListOf<PlannedSession>()
        var w = nextWeek
        while (!w.isAfter(end)) {
            for (s in src) {
                val d = s.date.plusDays(ChronoUnit.DAYS.between(from, w))
                if (d.isAfter(end) || (d to s.seriesId) in doneDates) continue
                insert += blank(s.programId, s.seriesId, d, s.timeMinutes, SessionContent(s.name, s.blocks, s.sourceWorkoutId))
            }
            w = w.plusDays(7)
        }
        return PlanChange(insert = insert, delete = delete)
    }

    fun delete(all: List<PlannedSession>, target: PlannedSession, scope: EditScope): PlanChange =
        PlanChange(delete = targets(all, target, scope).map { it.id })

    /** Applique une progression aux prochaines occurrences du créneau (blocs du même exercice). */
    fun applyProgression(all: List<PlannedSession>, done: PlannedSession, key: String, transform: (Block) -> Block): PlanChange =
        PlanChange(update = all.filter { it.seriesId == done.seriesId && it.date.isAfter(done.date) && it.status == PlanStatus.PLANNED }
            .mapNotNull { p ->
                if (p.blocks.none { BlockText.progressKey(it) == key }) null
                else p.copy(blocks = p.blocks.map { b -> if (BlockText.progressKey(b) == key) transform(b) else b })
            })
}
