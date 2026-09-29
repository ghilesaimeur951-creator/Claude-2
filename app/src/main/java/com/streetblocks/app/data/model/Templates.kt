package com.streetblocks.app.data.model

import com.streetblocks.app.data.model.BlockType.*

data class WorkoutTemplate(
    val key: String,
    val name: String,
    val description: String,
    val blocks: () -> List<Block>,
)

/** Modèles de séances prêts à l'emploi (copiés puis modifiables). */
object Templates {

    private fun warm(min: Int, key: String = "COMPLET") =
        Block(type = WARMUP, exercise = key, sets = 1, timed = true, workSec = min * 60)

    private fun rest(sec: Int) = Block(type = REST, sets = 1, workSec = sec)
    private fun end() = Block.default(END)

    private fun pull(ex: String, sets: Int, reps: Int, rest: Int, kg: Int = 0, grip: String = "PRONATION", band: Boolean = false, support: String = Catalog.BAR_HAUTE) =
        Block(
            type = PULLUP, exercise = ex, support = support, grip = grip, sets = sets, reps = reps, restBetweenSetsSec = rest,
            loadMode = when { band -> LoadMode.BAND; kg > 0 -> LoadMode.WEIGHTED; else -> LoadMode.NONE }, vestKg = kg,
        )

    private fun dips(ex: String, sets: Int, reps: Int, rest: Int, kg: Int = 0, support: String = Catalog.PAR_MOYENNES) =
        Block(
            type = DIPS, exercise = ex, support = support, sets = sets, reps = reps, restBetweenSetsSec = rest,
            loadMode = if (kg > 0) LoadMode.WEIGHTED else LoadMode.NONE, vestKg = kg,
        )

    private fun push(ex: String, sets: Int, reps: Int, rest: Int, pos: String = Catalog.POS_SOL, kg: Int = 0) =
        Block(
            type = PUSHUP, exercise = ex, support = pos, sets = sets, reps = reps, restBetweenSetsSec = rest,
            loadMode = if (kg > 0) LoadMode.WEIGHTED else LoadMode.NONE, vestKg = kg,
        )

    private fun hold(ex: String, sets: Int, sec: Int, rest: Int) =
        Block(type = STATIC, exercise = ex, sets = sets, timed = true, workSec = sec, restBetweenSetsSec = rest)

    private fun free(ex: String, sets: Int, reps: Int, rest: Int) =
        Block(type = FREE, exercise = ex, sets = sets, reps = reps, restBetweenSetsSec = rest)

    val all = listOf(
        WorkoutTemplate("force", "Force – Tractions & Dips lestés", "5×5 lourds, longs repos. Pour gagner en force max.") {
            listOf(
                warm(8),
                pull("TRACTION", 5, 5, 180, kg = 10),
                rest(180),
                dips("DIPS", 5, 5, 180, kg = 15),
                rest(120),
                hold("L_SIT", 3, 15, 60),
                end(),
            )
        },
        WorkoutTemplate("hypertrophie", "Hypertrophie – Haut du corps", "4 séries de 10–15, repos courts, volume élevé.") {
            listOf(
                warm(8),
                pull("TRACTION", 4, 10, 90, kg = 5),
                rest(90),
                dips("DIPS_PECS", 4, 12, 90),
                rest(90),
                push("POMPES", 4, 15, 60, pos = Catalog.POS_PLATEFORME),
                rest(60),
                pull("AUSTRALIENNE", 3, 12, 60, support = Catalog.BAR_HANCHES),
                rest(60),
                hold("GAINAGE", 3, 45, 45),
                end(),
            )
        },
        WorkoutTemplate("endurance", "Endurance – Circuit volume", "Beaucoup de répétitions, repos courts.") {
            listOf(
                warm(5, "CARDIO"),
                pull("TRACTION", 5, 8, 45),
                dips("DIPS", 5, 12, 45),
                push("POMPES", 5, 20, 45),
                free("BURPEES", 3, 10, 45),
                hold("GAINAGE", 3, 60, 30),
                end(),
            )
        },
        WorkoutTemplate("muscleup", "Muscle-up – Progression", "Explosivité, muscle-up assisté et transitions.") {
            listOf(
                warm(8, "ACTIVATION"),
                pull("EXPLOSIVE", 5, 4, 120),
                rest(120),
                pull("MUSCLE_UP", 5, 3, 150, band = true),
                rest(120),
                dips("DIPS_BARRE", 4, 8, 90, support = Catalog.BARRE_DROITE),
                rest(90),
                dips("RUSSIAN", 3, 5, 90, support = Catalog.BARRE_DROITE),
                end(),
            )
        },
        WorkoutTemplate("traction", "Spécial tractions", "Toutes les prises, du lourd au volume.") {
            listOf(
                warm(8),
                pull("TRACTION", 5, 6, 120),
                rest(120),
                pull("TRACTION", 4, 8, 90, grip = "SUPINATION"),
                rest(90),
                pull("AUSTRALIENNE", 4, 12, 60, support = Catalog.BAR_HANCHES),
                rest(60),
                hold("DEAD_HANG", 3, 30, 60),
                end(),
            )
        },
        WorkoutTemplate("dips", "Spécial dips", "Parallèles lestées, barre droite, triceps.") {
            listOf(
                warm(8),
                dips("DIPS", 5, 6, 150, kg = 10),
                rest(120),
                dips("DIPS_BARRE", 4, 10, 90, support = Catalog.BARRE_DROITE),
                rest(90),
                push("DIAMANT", 3, 15, 60),
                rest(60),
                hold("SUPPORT", 3, 30, 45),
                end(),
            )
        },
    )
}
