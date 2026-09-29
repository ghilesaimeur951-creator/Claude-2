package com.streetblocks.app.engine

import com.streetblocks.app.data.model.AppSettings
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockText
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.Catalog
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.durationWords
import com.streetblocks.app.data.model.kgLabel
import kotlin.math.max

enum class StepKind { PREP, WORK, REST, WARMUP, END }

/** Une étape minutée de la séance, générée à partir des blocs. */
data class Step(
    val kind: StepKind,
    val blockType: BlockType?,
    val blockId: String?,
    val title: String,
    val headline: String,
    val detail: String,
    val load: String?,
    val loadDetail: String?,
    val equipment: String?,
    val setIndex: Int = 0,
    val setCount: Int = 0,
    val durationSec: Int,
    /** true : effort au temps. false : répétitions (durée estimée, fin possible en avance) */
    val timed: Boolean = true,
    val reps: Int = 0,
    val speech: String = "",
    val isBlockRest: Boolean = false,
)

object StepBuilder {

    fun build(blocks: List<Block>, bands: List<Band>, profile: EquipmentProfile, s: AppSettings): List<Step> {
        val out = mutableListOf<Step>()
        loop@ for (b in blocks) {
            when (b.type) {
                BlockType.END -> break@loop
                BlockType.REST -> if (b.workSec > 0) out += Step(
                    kind = StepKind.REST, blockType = b.type, blockId = b.id,
                    title = "REPOS", headline = "REPOS", detail = "Récupère", load = null, loadDetail = null,
                    equipment = null, durationSec = b.workSec, isBlockRest = true,
                )
                BlockType.WARMUP -> out += warmupSteps(b, bands)
                else -> out += exerciseSteps(b, bands, profile, s, prevIsRest = out.lastOrNull()?.kind == StepKind.REST)
            }
        }
        out += Step(
            kind = StepKind.END, blockType = BlockType.END, blockId = null, title = "SÉANCE TERMINÉE",
            headline = "BRAVO", detail = "", load = null, loadDetail = null, equipment = null, durationSec = 0,
            speech = "Séance terminée. Bravo !",
        )

        // Annonces vocales des repos : durée + exercice suivant
        return out.mapIndexed { i, st ->
            if (st.kind != StepKind.REST) st else {
                val next = out.drop(i + 1).firstOrNull { it.kind != StepKind.REST }
                val nextText = when {
                    next == null || next.kind == StepKind.END -> "Dernière récupération."
                    next.kind == StepKind.WORK && next.setIndex > 1 -> "Ensuite, série ${next.setIndex} sur ${next.setCount}."
                    else -> "Prochain exercice : ${next.title.lowercase()}."
                }
                st.copy(speech = "Repos, ${durationWords(st.durationSec)}. $nextText")
            }
        }
    }

    private fun warmupSteps(b: Block, bands: List<Band>): List<Step> {
        val moves = Catalog.warmupMoves(b.exercise)
        val total = max(30, b.workSec)
        val count = moves.size.coerceAtMost(max(1, total / 20))
        val per = total / count
        val band = if (b.exercise == "ELASTIQUE") BlockText.band(b, bands) else null
        return (0 until count).map { i ->
            val dur = if (i == count - 1) total - per * (count - 1) else per
            Step(
                kind = StepKind.WARMUP, blockType = BlockType.WARMUP, blockId = b.id,
                title = moves[i].uppercase(), headline = "ÉCHAUFFEMENT ${i + 1}/$count",
                detail = BlockText.exerciseLabel(b), load = band?.let { "Élastique ${it.name}" }, loadDetail = null,
                equipment = null, setIndex = i + 1, setCount = count, durationSec = dur,
                speech = if (i == 0) "Échauffement. ${moves[i]}." else "${moves[i]}.",
            )
        }
    }

    private fun exerciseSteps(b: Block, bands: List<Band>, profile: EquipmentProfile, s: AppSettings, prevIsRest: Boolean): List<Step> {
        val out = mutableListOf<Step>()
        val name = BlockText.exerciseLabel(b)
        val title = name.uppercase()
        val load = BlockText.loadLabel(b, bands)
        val loadDetail = BlockText.loadDetail(b, bands)
        val equip = BlockText.equipmentLabel(b, profile)
        val volume = if (b.timed) "${b.sets} séries de ${b.workSec} secondes" else "${b.sets} séries de ${b.reps}"
        val loadSpeech = when (b.loadMode) {
            LoadMode.WEIGHTED -> if (b.addedKg > 0) ", plus ${kgLabel(b.addedKg).replace(" kg", " kilos").replace('.', ',')}" else ""
            LoadMode.BAND -> BlockText.band(b, bands)?.let { ", élastique ${it.name}" } ?: ", avec élastique"
            LoadMode.NONE -> ""
        }
        val spr = if (b.secPerRep > 0) b.secPerRep else (Catalog.option(b)?.secPerRep ?: s.secPerRep)

        if (s.prepSec > 0 && !prevIsRest) {
            out += Step(
                kind = StepKind.PREP, blockType = b.type, blockId = b.id, title = title, headline = "PRÉPARE-TOI",
                detail = if (b.timed) "${b.sets} × ${b.workSec} s" else "${b.sets} × ${b.reps} réps",
                load = load, loadDetail = loadDetail, equipment = equip, setIndex = 1, setCount = b.sets,
                durationSec = s.prepSec, speech = "Prochain exercice : $name, $volume$loadSpeech. Prépare-toi.",
            )
        }

        for (set in 1..b.sets) {
            val dur = if (b.timed) b.workSec else max(5, b.reps * spr)
            val speech = buildString {
                if (set == 1) append("$name. ")
                append("Série $set sur ${b.sets}. ")
                append(if (b.timed) "Tiens ${durationWords(b.workSec)}. " else "${b.reps} répétitions. ")
                if (set == 1 && loadSpeech.isNotEmpty()) append(loadSpeech.removePrefix(", ").replaceFirstChar { it.uppercase() } + ". ")
                append("C'est parti !")
            }
            out += Step(
                kind = StepKind.WORK, blockType = b.type, blockId = b.id, title = title,
                headline = "SÉRIE $set/${b.sets}",
                detail = if (b.timed) "TIENS ${b.workSec} s" else "${b.reps} RÉPÉTITIONS",
                load = load, loadDetail = loadDetail, equipment = equip, setIndex = set, setCount = b.sets,
                durationSec = dur, timed = b.timed, reps = b.reps, speech = speech,
            )
            if (set < b.sets && b.restBetweenSetsSec > 0) {
                out += Step(
                    kind = StepKind.REST, blockType = b.type, blockId = b.id, title = "REPOS", headline = "REPOS",
                    detail = "Série ${set + 1}/${b.sets} ensuite", load = null, loadDetail = null, equipment = null,
                    setIndex = set, setCount = b.sets, durationSec = b.restBetweenSetsSec,
                )
            }
        }
        return out
    }

    fun totalSec(steps: List<Step>) = steps.sumOf { it.durationSec }
}
