package com.streetblocks.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class PlanStatus { PLANNED, DONE, SKIPPED }

/** Portée d'une modification dans un programme. */
enum class EditScope { THIS, FOLLOWING }

data class Program(
    val id: Long,
    val name: String,
    val goal: String,
    val start: LocalDate,
    val end: LocalDate,
    val trainingDays: Set<DayOfWeek>,
    val createdAt: Long,
)

/**
 * Une séance planifiée à une date. Elle possède SA PROPRE copie des blocs :
 * modifier la séance d'origine ne la change pas.
 * [seriesId] regroupe les occurrences d'un même créneau hebdomadaire
 * (ex. tous les lundis) pour « cette séance et les suivantes ».
 */
data class PlannedSession(
    val id: Long,
    val programId: Long,
    val seriesId: String,
    val date: LocalDate,
    val timeMinutes: Int?,
    val name: String,
    val blocks: List<Block>,
    val sourceWorkoutId: Long?,
    val status: PlanStatus,
    val sessionId: Long?,
    val completedAt: Long?,
    val note: String,
) {
    val editable: Boolean get() = status != PlanStatus.DONE
}

/** D'où vient le contenu d'une séance planifiée. */
sealed class SessionSource {
    data class Saved(val workoutId: Long) : SessionSource()
    data class Template(val key: String) : SessionSource()
    data object Empty : SessionSource()
}

data class DaySlot(
    val day: DayOfWeek,
    val source: SessionSource,
    val timeMinutes: Int? = null,
)

data class ProgramSpec(
    val name: String,
    val goal: String,
    val start: LocalDate,
    val end: LocalDate,
    val slots: List<DaySlot>,
)

enum class ProgramLength(val label: String) {
    ONE_WEEK("1 semaine"), TWO_WEEKS("2 semaines"), ONE_MONTH("1 mois"), TWO_MONTHS("2 mois"), CUSTOM("Personnalisée");

    fun endFor(start: LocalDate): LocalDate = when (this) {
        ONE_WEEK -> start.plusDays(6)
        TWO_WEEKS -> start.plusDays(13)
        ONE_MONTH -> start.plusMonths(1).minusDays(1)
        TWO_MONTHS -> start.plusMonths(2).minusDays(1)
        CUSTOM -> start.plusWeeks(4).minusDays(1)
    }
}

data class ProgramStats(
    val total: Int,
    val done: Int,
    val skipped: Int,
    val late: Int,
    val next: PlannedSession?,
) {
    val progress: Float get() = if (total == 0) 0f else (done + skipped).toFloat() / total
}

object Planning {
    val goals = listOf("Force", "Hypertrophie", "Endurance", "Muscle-up", "Tractions", "Dips", "Remise en forme")

    val week: List<DayOfWeek> = DayOfWeek.values().toList()

    /** Dates d'entraînement d'une période pour des jours de semaine donnés. */
    fun datesFor(start: LocalDate, end: LocalDate, days: Set<DayOfWeek>): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .filter { it.dayOfWeek in days }
            .toList()
    }

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun stats(list: List<PlannedSession>, today: LocalDate = LocalDate.now()): ProgramStats = ProgramStats(
        total = list.size,
        done = list.count { it.status == PlanStatus.DONE },
        skipped = list.count { it.status == PlanStatus.SKIPPED },
        late = list.count { it.status == PlanStatus.PLANNED && it.date.isBefore(today) },
        next = list.filter { it.status == PlanStatus.PLANNED && !it.date.isBefore(today) }
            .minWithOrNull(compareBy({ it.date }, { it.timeMinutes ?: 0 })),
    )

    /** Occurrences concernées par « cette séance et les suivantes » (jamais les séances réalisées). */
    fun following(all: List<PlannedSession>, p: PlannedSession): List<PlannedSession> =
        all.filter { it.id != p.id && it.seriesId == p.seriesId && !it.date.isBefore(p.date) && it.status == PlanStatus.PLANNED }

    fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.FRANCE).removeSuffix(".").replaceFirstChar { it.uppercase() }
    fun dayLong(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }

    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
    private val dateLongFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

    fun dateLabel(d: LocalDate): String = dateFmt.format(d).replaceFirstChar { it.uppercase() }
    fun dateLong(d: LocalDate): String = dateLongFmt.format(d).replaceFirstChar { it.uppercase() }
    fun shortDate(d: LocalDate): String = shortFmt.format(d)
    fun period(a: LocalDate, b: LocalDate): String = "du ${shortDate(a)} au ${shortDate(b)}${if (a.year != b.year) " ${b.year}" else ""}"
    fun timeLabel(min: Int?): String? = min?.let { "%02d:%02d".format(it / 60, it % 60) }

    fun relative(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d.toEpochDay() - today.toEpochDay()) {
        0L -> "Aujourd'hui"
        1L -> "Demain"
        -1L -> "Hier"
        else -> dateLabel(d)
    }
}
