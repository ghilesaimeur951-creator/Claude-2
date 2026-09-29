package com.streetblocks.app.data.model

data class ExOption(
    val key: String,
    val label: String,
    val secPerRep: Int? = null,
    val timed: Boolean = false,
    val defaultSupport: String? = null,
    /** Équipement implicite, affiché pendant la séance */
    val implied: String? = null,
)

data class SupportOption(val key: String, val label: String, val short: String)

/** Bibliothèque d'exercices, supports et usages d'élastiques. */
object Catalog {
    // Barres horizontales
    const val BAR_NOMBRIL = "BAR_NOMBRIL"
    const val BAR_HANCHES = "BAR_HANCHES"
    const val BAR_SOUS_POIGNETS = "BAR_SOUS_POIGNETS"
    const val BAR_POIGNETS = "BAR_POIGNETS"
    const val BAR_HAUTE = "BAR_HAUTE"

    // Dips
    const val PAR_SERREES = "PAR_SERREES"
    const val PAR_MOYENNES = "PAR_MOYENNES"
    const val PAR_LARGES = "PAR_LARGES"
    const val BARRE_DROITE = "BARRE_DROITE"

    // Positions pompes
    const val POS_SOL = "SOL"
    const val POS_PLATEFORME = "PLATEFORME"
    const val POS_MAINS_PLATEFORME = "MAINS_PLATEFORME"
    const val POS_BARRE_NOMBRIL = "MAINS_BARRE_NOMBRIL"
    const val POS_BARRE_HANCHES = "MAINS_BARRE_HANCHES"

    // Implicites
    const val IMPLIED_FLOOR = "Au sol"
    const val IMPLIED_PARALLELS = "Barres parallèles"
    const val IMPLIED_HIGH_BAR = "Barre haute"
    const val IMPLIED_PLATFORM = "Plateforme"

    val bars = listOf(
        SupportOption(BAR_NOMBRIL, "Barre hauteur nombril", "Barre nombril"),
        SupportOption(BAR_HANCHES, "Barre hauteur hanches", "Barre hanches"),
        SupportOption(BAR_SOUS_POIGNETS, "Barre légèrement sous les poignets (bras tendus)", "Barre sous poignets"),
        SupportOption(BAR_POIGNETS, "Barre au niveau des poignets (bras tendus)", "Barre poignets"),
        SupportOption(BAR_HAUTE, "Barre haute (bout des doigts)", "Barre haute"),
    )

    /** Exercices conseillés pour chaque hauteur de barre. */
    val barSuggestions: Map<String, List<String>> = mapOf(
        BAR_NOMBRIL to listOf("Tractions australiennes (difficiles)", "Pompes inclinées mains sur barre", "Dips barre droite", "Scapular push-ups"),
        BAR_HANCHES to listOf("Tractions australiennes", "Pompes inclinées", "Sauts / franchissements", "Étirements ischios"),
        BAR_SOUS_POIGNETS to listOf("Tractions genoux fléchis", "Tractions sautées", "Négatives de tractions", "Muscle-up assisté pieds au sol"),
        BAR_POIGNETS to listOf("Tractions sautées", "Négatives de tractions", "Tractions assistées élastique (pied)", "Transitions de muscle-up"),
        BAR_HAUTE to listOf("Tractions complètes", "Tractions lestées", "Muscle-ups", "Suspension / dead hang", "Relevés de jambes suspendu", "Front lever"),
    )

    val dipsSupports = listOf(
        SupportOption(PAR_SERREES, "Parallèles serrées", "Parallèles serrées"),
        SupportOption(PAR_MOYENNES, "Parallèles moyennes", "Parallèles moyennes"),
        SupportOption(PAR_LARGES, "Parallèles légèrement larges", "Parallèles larges"),
        SupportOption(BARRE_DROITE, "Barre droite basse", "Barre droite"),
    )

    val pushupPositions = listOf(
        SupportOption(POS_SOL, "Au sol", "Au sol"),
        SupportOption(POS_PLATEFORME, "Pieds surélevés (plateforme)", "Pieds sur plateforme"),
        SupportOption(POS_MAINS_PLATEFORME, "Mains sur plateforme (plus facile)", "Mains sur plateforme"),
        SupportOption(POS_BARRE_HANCHES, "Inclinées, mains sur barre hanches", "Mains barre hanches"),
        SupportOption(POS_BARRE_NOMBRIL, "Inclinées, mains sur barre nombril", "Mains barre nombril"),
    )

    val grips = listOf(
        SupportOption("PRONATION", "Pronation", "Pronation"),
        SupportOption("SUPINATION", "Supination", "Supination"),
        SupportOption("NEUTRE", "Neutre", "Neutre"),
        SupportOption("LARGE", "Large", "Prise large"),
        SupportOption("SERREE", "Serrée", "Prise serrée"),
        SupportOption("MIXTE", "Mixte", "Prise mixte"),
    )

    val pullupExercises = listOf(
        ExOption("TRACTION", "Tractions", defaultSupport = BAR_HAUTE),
        ExOption("AUSTRALIENNE", "Tractions australiennes", defaultSupport = BAR_HANCHES),
        ExOption("NEGATIVE", "Tractions négatives", secPerRep = 6, defaultSupport = BAR_POIGNETS),
        ExOption("SAUTEE", "Tractions sautées", defaultSupport = BAR_POIGNETS),
        ExOption("EXPLOSIVE", "Tractions explosives", defaultSupport = BAR_HAUTE),
        ExOption("ARCHER", "Tractions archer", secPerRep = 4, defaultSupport = BAR_HAUTE),
        ExOption("L_PULLUP", "Tractions L-sit", secPerRep = 4, defaultSupport = BAR_HAUTE),
        ExOption("MUSCLE_UP", "Muscle-ups", secPerRep = 5, defaultSupport = BAR_HAUTE),
        ExOption("SCAP", "Scapular pull-ups", defaultSupport = BAR_HAUTE),
    )

    val dipsExercises = listOf(
        ExOption("DIPS", "Dips"),
        ExOption("DIPS_PECS", "Dips penchés (pectoraux)"),
        ExOption("DIPS_TRICEPS", "Dips droits (triceps)"),
        ExOption("NEGATIFS", "Dips négatifs", secPerRep = 6),
        ExOption("DIPS_BARRE", "Dips barre droite", defaultSupport = BARRE_DROITE),
        ExOption("RUSSIAN", "Russian dips", secPerRep = 4, defaultSupport = BARRE_DROITE),
        ExOption("EXPLOSIFS", "Dips explosifs"),
    )

    val pushupExercises = listOf(
        ExOption("POMPES", "Pompes"),
        ExOption("LARGES", "Pompes larges"),
        ExOption("DIAMANT", "Pompes diamant"),
        ExOption("ARCHER", "Pompes archer", secPerRep = 4),
        ExOption("EXPLOSIVES", "Pompes explosives"),
        ExOption("PIKE", "Pike push-ups"),
        ExOption("PSEUDO", "Pseudo-planche push-ups", secPerRep = 4),
    )

    val staticExercises = listOf(
        ExOption("GAINAGE", "Gainage planche", timed = true, implied = IMPLIED_FLOOR),
        ExOption("GAINAGE_LAT", "Gainage latéral", timed = true, implied = IMPLIED_FLOOR),
        ExOption("HOLLOW", "Hollow body", timed = true, implied = IMPLIED_FLOOR),
        ExOption("L_SIT", "L-sit", timed = true, implied = IMPLIED_PARALLELS),
        ExOption("SUPPORT", "Support bras tendus (dips)", timed = true, implied = IMPLIED_PARALLELS),
        ExOption("DEAD_HANG", "Suspension (dead hang)", timed = true, implied = IMPLIED_HIGH_BAR),
        ExOption("CHIN_HOLD", "Menton au-dessus de la barre", timed = true, implied = IMPLIED_HIGH_BAR),
        ExOption("TUCK_FL", "Tuck front lever", timed = true, implied = IMPLIED_HIGH_BAR),
        ExOption("TUCK_PL", "Tuck planche", timed = true, implied = IMPLIED_PARALLELS),
        ExOption("SUPERMAN", "Superman", timed = true, implied = IMPLIED_FLOOR),
    )

    val freeExercises = listOf(
        ExOption("SQUATS", "Squats", implied = IMPLIED_FLOOR),
        ExOption("JUMP_SQUAT", "Squats sautés", implied = IMPLIED_FLOOR),
        ExOption("FENTES", "Fentes", implied = IMPLIED_FLOOR),
        ExOption("PISTOL", "Pistol squats", secPerRep = 4, implied = IMPLIED_FLOOR),
        ExOption("STEP_UP", "Step-ups", implied = IMPLIED_PLATFORM),
        ExOption("BOX_JUMP", "Sauts sur plateforme", implied = IMPLIED_PLATFORM),
        ExOption("KNEE_RAISE", "Relevés de genoux suspendu", implied = IMPLIED_HIGH_BAR),
        ExOption("LEG_RAISE", "Relevés de jambes suspendu", implied = IMPLIED_HIGH_BAR),
        ExOption("CRUNCH", "Crunchs", secPerRep = 2, implied = IMPLIED_FLOOR),
        ExOption("SITUP", "Sit-ups", implied = IMPLIED_FLOOR),
        ExOption("BURPEES", "Burpees", secPerRep = 4, implied = IMPLIED_FLOOR),
        ExOption("MOUNTAIN", "Mountain climbers", secPerRep = 1, implied = IMPLIED_FLOOR),
        ExOption("CUSTOM", "Personnalisé…"),
    )

    val warmupExercises = listOf(
        ExOption("COMPLET", "Échauffement complet", timed = true),
        ExOption("MOBILITE", "Mobilité articulaire", timed = true),
        ExOption("ACTIVATION", "Activation musculaire", timed = true),
        ExOption("ELASTIQUE", "Activation à l'élastique", timed = true),
        ExOption("CARDIO", "Cardio léger", timed = true),
    )

    private val mobility = listOf(
        "Rotations de la nuque", "Cercles des épaules", "Rotations des poignets", "Grands cercles des bras",
        "Rotations du bassin", "Rotations des genoux", "Rotations des chevilles", "Chat-vache",
    )
    private val activation = listOf(
        "Scapular pull-ups à la barre", "Scapular push-ups", "Pompes lentes", "Suspension active",
        "Tractions australiennes faciles", "Support aux parallèles",
    )
    private val band = listOf(
        "Dislocations d'épaules à l'élastique", "Écartés élastique (pull-aparts)", "Rotations externes élastique",
        "Face pulls élastique", "Tirage élastique bras tendus",
    )
    private val cardio = listOf(
        "Jumping jacks", "Montées de genoux", "Talons-fesses", "Mountain climbers", "Squats au poids du corps", "Step-ups sur plateforme",
    )

    fun warmupMoves(key: String): List<String> = when (key) {
        "MOBILITE" -> mobility
        "ACTIVATION" -> activation
        "ELASTIQUE" -> band
        "CARDIO" -> cardio
        else -> listOf("Jumping jacks", "Montées de genoux") + mobility.take(5) + activation.take(4)
    }

    val bandUsages = listOf(
        "TRACTION" to "Assistance traction",
        "MUSCLE_UP" to "Assistance muscle-up",
        "DIPS" to "Assistance dips",
        "POMPES" to "Pompes assistées",
        "MOBILITE" to "Mobilité / échauffement",
    )

    fun usageLabel(key: String) = bandUsages.firstOrNull { it.first == key }?.second ?: key

    fun exercises(type: BlockType): List<ExOption> = when (type) {
        BlockType.PULLUP -> pullupExercises
        BlockType.DIPS -> dipsExercises
        BlockType.PUSHUP -> pushupExercises
        BlockType.STATIC -> staticExercises
        BlockType.FREE -> freeExercises
        BlockType.WARMUP -> warmupExercises
        else -> emptyList()
    }

    fun option(b: Block): ExOption? = exercises(b.type).firstOrNull { it.key == b.exercise }

    /** Usage d'élastique correspondant à un bloc. */
    fun bandUsageFor(b: Block): String? = when (b.type) {
        BlockType.PULLUP -> if (b.exercise == "MUSCLE_UP") "MUSCLE_UP" else "TRACTION"
        BlockType.DIPS -> "DIPS"
        BlockType.PUSHUP -> "POMPES"
        BlockType.WARMUP -> "MOBILITE"
        else -> null
    }

    /** Élastique par défaut : le milieu de gamme parmi les compatibles. */
    fun pickBand(b: Block, bands: List<Band>): Long? {
        val usage = bandUsageFor(b)
        val compatible = bands.filter { usage == null || usage in it.usages }.ifEmpty { bands }
        return compatible.getOrNull(compatible.size / 2)?.id
    }

    /** Complète les blocs « élastique » sans élastique choisi. */
    fun withBands(blocks: List<Block>, bands: List<Band>): List<Block> =
        blocks.map { b -> if (b.loadMode == LoadMode.BAND && b.bandId == null) b.copy(bandId = pickBand(b, bands)) else b }

    fun supportsBand(type: BlockType) = type == BlockType.PULLUP || type == BlockType.DIPS || type == BlockType.PUSHUP

    fun typeLabel(t: BlockType): String = when (t) {
        BlockType.WARMUP -> "Échauffement"
        BlockType.PULLUP -> "Tractions"
        BlockType.DIPS -> "Dips"
        BlockType.PUSHUP -> "Pompes"
        BlockType.STATIC -> "Statique"
        BlockType.FREE -> "Exercice libre"
        BlockType.REST -> "Repos"
        BlockType.END -> "Fin de séance"
    }

    fun typeDescription(t: BlockType): String = when (t) {
        BlockType.WARMUP -> "Mobilité, activation, cardio guidés"
        BlockType.PULLUP -> "Classiques, lestées, élastique, muscle-up"
        BlockType.DIPS -> "Parallèles serrées/moyennes/larges, barre droite"
        BlockType.PUSHUP -> "Sol, pieds surélevés, lestées, élastique"
        BlockType.STATIC -> "Gainage, L-sit, holds, suspension"
        BlockType.FREE -> "Squats, abdos, plateforme, burpees…"
        BlockType.REST -> "Compte à rebours de récupération"
        BlockType.END -> "Clôture automatique de la séance"
    }
}

/** Textes d'affichage d'un bloc. */
object BlockText {

    fun exerciseLabel(b: Block): String = when {
        b.type == BlockType.FREE && b.exercise == "CUSTOM" -> b.customName.ifBlank { "Exercice libre" }
        b.type == BlockType.REST -> "Repos"
        b.type == BlockType.END -> "Fin de séance"
        else -> Catalog.option(b)?.label ?: Catalog.typeLabel(b.type)
    }

    fun gripLabel(b: Block): String? =
        if (b.type == BlockType.PULLUP) Catalog.grips.firstOrNull { it.key == b.grip }?.short else null

    fun supportLabel(b: Block, profile: EquipmentProfile): String? = when (b.type) {
        BlockType.PULLUP -> Catalog.bars.firstOrNull { it.key == b.support }?.short
        BlockType.DIPS -> Catalog.dipsSupports.firstOrNull { it.key == b.support }?.short
        BlockType.PUSHUP -> when (b.support) {
            Catalog.POS_PLATEFORME -> "Pieds sur plateforme ${profile.platformHeightCm} cm"
            Catalog.POS_MAINS_PLATEFORME -> "Mains sur plateforme ${profile.platformHeightCm} cm"
            else -> Catalog.pushupPositions.firstOrNull { it.key == b.support }?.short
        }
        BlockType.STATIC, BlockType.FREE -> Catalog.option(b)?.implied?.let {
            if (it == Catalog.IMPLIED_PLATFORM) "Plateforme ${profile.platformHeightCm} cm" else it
        }
        else -> null
    }

    /** Équipement lisible (support + prise). */
    fun equipmentLabel(b: Block, profile: EquipmentProfile): String? =
        listOfNotNull(supportLabel(b, profile), gripLabel(b)).joinToString(" · ").ifBlank { null }

    fun band(b: Block, bands: List<Band>): Band? = bands.firstOrNull { it.id == b.bandId }

    /** Charge courte : « +15 kg », « Élastique Noir », « Poids du corps ». */
    fun loadLabel(b: Block, bands: List<Band>): String? = when (b.loadMode) {
        LoadMode.WEIGHTED -> if (b.addedKg > 0) "+${kgLabel(b.addedKg)}" else "Poids du corps"
        LoadMode.BAND -> band(b, bands)?.let { "Élastique ${it.name}" } ?: "Élastique (à choisir)"
        LoadMode.NONE -> if (b.isExercise) "Poids du corps" else null
    }

    /** Détail de la charge : « Gilet 10 kg + ceinture 21 kg » / « Résistance 15–25 kg ». */
    fun loadDetail(b: Block, bands: List<Band>): String? = when (b.loadMode) {
        LoadMode.WEIGHTED -> listOfNotNull(
            if (b.vestKg > 0) "Gilet ${b.vestKg} kg" else null,
            if (b.beltKg > 0) "Ceinture ${kgLabel(b.beltKg)}" else null,
        ).joinToString(" + ").ifBlank { null }
        LoadMode.BAND -> band(b, bands)?.let { "Assistance ${it.resistanceLabel}" }
        LoadMode.NONE -> null
    }

    fun volumeLabel(b: Block): String = when {
        b.type == BlockType.REST -> timeLabel(b.workSec)
        b.type == BlockType.WARMUP -> "${b.workSec / 60} min"
        b.timed -> "${b.sets} × ${b.workSec} s"
        else -> "${b.sets} × ${b.reps} réps"
    }

    fun summary(b: Block, bands: List<Band>, profile: EquipmentProfile): String = when (b.type) {
        BlockType.REST -> "Durée ${timeLabel(b.workSec)}"
        BlockType.END -> "Clôture automatique de la séance"
        BlockType.WARMUP -> listOfNotNull(
            "${b.workSec / 60} min",
            "${Catalog.warmupMoves(b.exercise).size} mouvements guidés",
            if (b.exercise == "ELASTIQUE") band(b, bands)?.let { "Élastique ${it.name}" } else null,
        ).joinToString(" · ")
        else -> listOfNotNull(
            volumeLabel(b),
            loadLabel(b, bands)?.takeIf { b.loadMode != LoadMode.NONE },
            equipmentLabel(b, profile),
            if (b.sets > 1 && b.restBetweenSetsSec > 0) "Repos ${timeLabel(b.restBetweenSetsSec)}" else null,
            b.tempo.takeIf { it.isNotBlank() }?.let { "Tempo $it" },
        ).joinToString(" · ")
    }

    /** Clé de progression : même exercice, même support, même prise. */
    fun progressKey(b: Block): String = "${b.type}|${b.exercise}|${b.support}|${b.grip}|${if (b.type == BlockType.FREE) b.customName else ""}"

    fun fullName(b: Block, profile: EquipmentProfile): String =
        listOfNotNull(exerciseLabel(b), equipmentLabel(b, profile)).joinToString(" — ")
}
