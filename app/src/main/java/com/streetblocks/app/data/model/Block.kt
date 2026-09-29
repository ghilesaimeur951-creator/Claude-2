package com.streetblocks.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

@Serializable
enum class BlockType { WARMUP, PULLUP, DIPS, PUSHUP, STATIC, FREE, REST, END }

@Serializable
enum class LoadMode { NONE, WEIGHTED, BAND }

/**
 * Un bloc de la séance. Un seul modèle générique pour tous les types :
 * chaque type n'utilise que les champs qui le concernent.
 */
@Serializable
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    /** Clé d'exercice du [Catalog] (ex: TRACTION, DIPS, GAINAGE…) */
    val exercise: String = "",
    /** Barre / support / position (ex: BAR_HAUTE, PAR_MOYENNES, PLATEFORME…) */
    val support: String = "",
    /** Prise (tractions) */
    val grip: String = "",
    val loadMode: LoadMode = LoadMode.NONE,
    val vestKg: Int = 0,
    val beltKg: Double = 0.0,
    val bandId: Long? = null,
    val sets: Int = 3,
    val reps: Int = 8,
    /** true = série au temps (durée [workSec]) ; false = répétitions */
    val timed: Boolean = false,
    /** Durée d'une série au temps, d'un repos ou d'un échauffement (secondes) */
    val workSec: Int = 30,
    val restBetweenSetsSec: Int = 90,
    /** Durée estimée d'une répétition (0 = réglage global) */
    val secPerRep: Int = 0,
    /** Tempo / temps sous tension (texte libre, ex « 3-1-1 ») */
    val tempo: String = "",
    val customName: String = "",
    val note: String = "",
) {
    val isExercise: Boolean get() = type in EXERCISE_TYPES

    val addedKg: Double get() = if (loadMode == LoadMode.WEIGHTED) vestKg + beltKg else 0.0

    fun copyNew(): Block = copy(id = UUID.randomUUID().toString())

    companion object {
        val EXERCISE_TYPES = setOf(BlockType.PULLUP, BlockType.DIPS, BlockType.PUSHUP, BlockType.STATIC, BlockType.FREE)

        fun default(type: BlockType): Block = when (type) {
            BlockType.WARMUP -> Block(type = type, exercise = "COMPLET", sets = 1, timed = true, workSec = 480)
            BlockType.PULLUP -> Block(type = type, exercise = "TRACTION", support = Catalog.BAR_HAUTE, grip = "PRONATION", sets = 4, reps = 8, restBetweenSetsSec = 120)
            BlockType.DIPS -> Block(type = type, exercise = "DIPS", support = Catalog.PAR_MOYENNES, sets = 4, reps = 10, restBetweenSetsSec = 90)
            BlockType.PUSHUP -> Block(type = type, exercise = "POMPES", support = Catalog.POS_SOL, sets = 4, reps = 15, restBetweenSetsSec = 60)
            BlockType.STATIC -> Block(type = type, exercise = "GAINAGE", sets = 3, timed = true, workSec = 45, restBetweenSetsSec = 45)
            BlockType.FREE -> Block(type = type, exercise = "SQUATS", sets = 4, reps = 15, restBetweenSetsSec = 60)
            BlockType.REST -> Block(type = type, sets = 1, workSec = 120)
            BlockType.END -> Block(type = type, sets = 1)
        }
    }
}

data class Workout(
    val id: Long,
    val name: String,
    val description: String,
    val blocks: List<Block>,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long?,
)

data class Band(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val minKg: Int,
    val maxKg: Int,
    val usages: Set<String>,
    val note: String = "",
) {
    val resistanceLabel: String get() = if (minKg == maxKg) "$maxKg kg" else "$minKg–$maxKg kg"
}

/** Résultat d'un exercice dans une séance réalisée. */
@Serializable
data class ExerciseLog(
    val blockId: String,
    val key: String,
    val name: String,
    val type: BlockType,
    val setsPlanned: Int,
    val setsDone: Int,
    val reps: Int,
    val repsDone: Int,
    val timed: Boolean,
    val workSec: Int,
    val addedKg: Double,
    val loadMode: LoadMode = LoadMode.NONE,
    val bandId: Long? = null,
    val bandName: String? = null,
)

data class SessionRecord(
    val id: Long,
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val durationSec: Int,
    val completed: Boolean,
    val logs: List<ExerciseLog>,
)

fun kgLabel(d: Double): String =
    if (d % 1.0 == 0.0) "${d.toInt()} kg" else String.format(java.util.Locale.FRANCE, "%.1f kg", d)

fun timeLabel(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun durationWords(totalSec: Int): String {
    val m = totalSec / 60
    val r = totalSec % 60
    return when {
        m == 0 -> "$r secondes"
        r == 0 -> if (m == 1) "1 minute" else "$m minutes"
        else -> "$m minute${if (m > 1) "s" else ""} $r"
    }
}
