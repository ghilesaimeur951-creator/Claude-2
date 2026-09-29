package com.streetblocks.app

import com.streetblocks.app.data.model.AppJson
import com.streetblocks.app.data.model.AppSettings
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.ExerciseLog
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.Progression
import com.streetblocks.app.data.model.Templates
import com.streetblocks.app.engine.StepBuilder
import com.streetblocks.app.engine.StepKind
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val profile = EquipmentProfile()
    private val settings = AppSettings(prepSec = 10, secPerRep = 3)
    private val bands = listOf(
        Band(id = 1, name = "Rouge", colorHex = "#E53935", minKg = 10, maxKg = 20, usages = setOf("TRACTION")),
        Band(id = 2, name = "Noir", colorHex = "#424242", minKg = 15, maxKg = 25, usages = setOf("TRACTION")),
    )

    @Test
    fun weightedPullupsProduceSetsAndRests() {
        val blocks = listOf(
            Block(type = BlockType.PULLUP, exercise = "TRACTION", sets = 5, reps = 10, restBetweenSetsSec = 120, loadMode = LoadMode.WEIGHTED, vestKg = 15),
            Block(type = BlockType.REST, workSec = 180),
            Block(type = BlockType.DIPS, exercise = "DIPS", sets = 3, reps = 8, restBetweenSetsSec = 90),
            Block.default(BlockType.END),
            Block(type = BlockType.PUSHUP, exercise = "POMPES", sets = 3), // après la fin : ignoré
        )
        val steps = StepBuilder.build(blocks, bands, profile, settings)
        val kinds = steps.map { it.kind }
        // prep + 5 séries + 4 repos + repos bloc + 3 séries + 2 repos + fin
        assertEquals(1 + 5 + 4 + 1 + 3 + 2 + 1, steps.size)
        assertEquals(StepKind.PREP, kinds.first())
        assertEquals(StepKind.END, kinds.last())
        val work = steps.filter { it.kind == StepKind.WORK }
        assertEquals(8, work.size)
        assertEquals("SÉRIE 2/5", work[1].headline)
        assertEquals("+15 kg", work[0].load)
        assertEquals(30, work[0].durationSec) // 10 réps × 3 s
        // Pas de préparation après un repos
        assertTrue(steps.none { it.kind == StepKind.PREP && it.title == "DIPS" })
        // Aucune pompe (après le bloc Fin)
        assertTrue(steps.none { it.title == "POMPES" })
        // Le repos annonce l'exercice suivant
        assertTrue(steps.first { it.isBlockRest }.speech.contains("dips"))
    }

    @Test
    fun templatesAllBuild() {
        Templates.all.forEach { t ->
            val steps = StepBuilder.build(t.blocks(), bands, profile, settings)
            assertTrue(t.name, steps.count { it.kind == StepKind.WORK } > 5)
            assertTrue(t.name, StepBuilder.totalSec(steps) in 10 * 60..120 * 60)
        }
    }

    @Test
    fun warmupIsSplitIntoMoves() {
        val steps = StepBuilder.build(listOf(Block.default(BlockType.WARMUP)), bands, profile, settings)
        val warm = steps.filter { it.kind == StepKind.WARMUP }
        assertTrue(warm.size >= 5)
        assertEquals(480, warm.sumOf { it.durationSec })
    }

    @Test
    fun blocksSurviveJsonRoundTrip() {
        val blocks = Templates.all.flatMap { it.blocks() }
        val json = AppJson.encodeToString(blocks)
        assertEquals(blocks, AppJson.decodeFromString<List<Block>>(json))
        val p = AppJson.decodeFromString<EquipmentProfile>(AppJson.encodeToString(profile))
        assertEquals(profile, p)
    }

    @Test
    fun beltPresetsUseStreetWorkoutWeights() {
        assertEquals(listOf(21.0, 24.0, 45.0), profile.beltPresets())
    }

    @Test
    fun progressionSuggestsRepOrLoad() {
        val log = ExerciseLog(
            blockId = "x", key = "k", name = "Tractions", type = BlockType.PULLUP, setsPlanned = 5, setsDone = 5,
            reps = 5, repsDone = 5, timed = false, workSec = 0, addedKg = 10.0, loadMode = LoadMode.WEIGHTED,
        )
        val advice = Progression.advise(log, settings, profile, bands)
        assertEquals("5×5 +10 kg", advice.today)
        assertEquals("5×6 ou 5×5 +12 kg", advice.next)
        val block = Block(type = BlockType.PULLUP, reps = 5, sets = 5, loadMode = LoadMode.WEIGHTED, vestKg = 10)
        assertEquals(6, advice.options[0].apply(block).reps)
        assertEquals(12, advice.options[1].apply(block).vestKg)

        val failed = log.copy(repsDone = 4)
        assertTrue(Progression.advise(failed, settings, profile, bands).options.isEmpty())

        val banded = log.copy(loadMode = LoadMode.BAND, bandId = 2, bandName = "Noir", addedKg = 0.0)
        val bAdvice = Progression.advise(banded, settings, profile, bands)
        assertEquals(1L, bAdvice.options[1].apply(block.copy(loadMode = LoadMode.BAND, bandId = 2)).bandId)
    }
}
