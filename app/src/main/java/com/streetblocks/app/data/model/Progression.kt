package com.streetblocks.app.data.model

data class ProgressionOption(
    val label: String,
    val apply: (Block) -> Block,
)

data class ProgressionAdvice(
    val today: String,
    val next: String,
    val options: List<ProgressionOption>,
)

/**
 * Progression « double » : on ajoute des répétitions, ou de la charge,
 * uniquement si la séance a été réussie.
 */
object Progression {

    fun performanceLabel(sets: Int, reps: Int, timed: Boolean, workSec: Int, mode: LoadMode, kg: Double, bandName: String?): String {
        val vol = if (timed) "${sets}×${workSec} s" else "${sets}×$reps"
        val load = when (mode) {
            LoadMode.WEIGHTED -> if (kg > 0) " +${kgLabel(kg)}" else ""
            LoadMode.BAND -> bandName?.let { " élastique $it" } ?: " élastique"
            LoadMode.NONE -> ""
        }
        return vol + load
    }

    fun advise(log: ExerciseLog, settings: AppSettings, profile: EquipmentProfile, bands: List<Band>): ProgressionAdvice {
        val today = performanceLabel(log.setsDone, log.repsDone, log.timed, log.workSec, log.loadMode, log.addedKg, log.bandName)
        val success = log.setsDone >= log.setsPlanned && (log.timed || log.repsDone >= log.reps)

        if (!success) {
            val keep = performanceLabel(log.setsPlanned, log.reps, log.timed, log.workSec, log.loadMode, log.addedKg, log.bandName)
            return ProgressionAdvice(today, "Garde le même objectif : $keep", emptyList())
        }

        if (log.timed) {
            val a = ProgressionOption("+5 s par série") { b -> b.copy(workSec = b.workSec + 5) }
            val b2 = ProgressionOption("+1 série") { b -> b.copy(sets = b.sets + 1) }
            return ProgressionAdvice(
                today,
                "${log.setsPlanned}×${log.workSec + 5} s ou ${log.setsPlanned + 1}×${log.workSec} s",
                listOf(a, b2),
            )
        }

        val plusRep = ProgressionOption("+1 rép. (${log.setsPlanned}×${log.reps + 1})") { b -> b.copy(reps = b.reps + 1) }

        return when (log.loadMode) {
            LoadMode.BAND -> {
                val current = bands.firstOrNull { it.id == log.bandId }
                val lighter = current?.let { c -> bands.filter { it.maxKg < c.maxKg }.maxByOrNull { it.maxKg } }
                val opts = buildList {
                    add(plusRep)
                    if (lighter != null) add(ProgressionOption("Élastique ${lighter.name}") { b -> b.copy(bandId = lighter.id) })
                    else add(ProgressionOption("Sans élastique") { b -> b.copy(loadMode = LoadMode.NONE, bandId = null) })
                }
                ProgressionAdvice(
                    today,
                    "${log.setsPlanned}×${log.reps + 1} ou " + (lighter?.let { "élastique ${it.name}" } ?: "sans élastique"),
                    opts,
                )
            }
            else -> {
                val inc = settings.loadIncrementKg
                val newKg = log.addedKg + inc
                val addKg = ProgressionOption("+${kgLabel(inc)} (+${kgLabel(newKg)})") { b -> addLoad(b, inc, profile) }
                val nextText = "${log.setsPlanned}×${log.reps + 1} ou ${log.setsPlanned}×${log.reps} +${kgLabel(newKg)}"
                ProgressionAdvice(today, nextText, listOf(plusRep, addKg))
            }
        }
    }

    /** Ajoute de la charge : d'abord au gilet (jusqu'à son max), puis à la ceinture. */
    fun addLoad(b: Block, inc: Double, profile: EquipmentProfile): Block {
        val base = if (b.loadMode != LoadMode.WEIGHTED) b.copy(loadMode = LoadMode.WEIGHTED, vestKg = 0, beltKg = 0.0, bandId = null) else b
        val incInt = inc.toInt()
        return if (profile.vestOwned && inc % 1.0 == 0.0 && base.vestKg + incInt <= profile.vestMaxKg && base.beltKg == 0.0) {
            base.copy(vestKg = base.vestKg + incInt)
        } else {
            base.copy(beltKg = base.beltKg + inc)
        }
    }
}
