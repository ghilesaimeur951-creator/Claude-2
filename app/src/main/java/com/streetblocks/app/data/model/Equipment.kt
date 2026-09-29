package com.streetblocks.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlateSet(
    val name: String,
    val kg: Double,
    val quantity: Int,
    val colorHex: String,
)

@Serializable
data class ParallelSetting(
    val key: String,
    val label: String,
    val enabled: Boolean = true,
    val note: String = "",
)

/** Configuration du matériel et du parc de street workout de l'utilisateur. */
@Serializable
data class EquipmentProfile(
    val vestOwned: Boolean = true,
    val vestMinKg: Int = 1,
    val vestMaxKg: Int = 20,
    val vestStepKg: Int = 1,
    val beltOwned: Boolean = true,
    val beltMaxKg: Int = 60,
    val plates: List<PlateSet> = listOf(
        PlateSet("Poids blancs", 21.0, 1, "#F5F5F5"),
        PlateSet("Poids noirs", 24.0, 1, "#212121"),
    ),
    val barsEnabled: List<String> = Catalog.bars.map { it.key },
    val barNotes: Map<String, String> = emptyMap(),
    val parallels: List<ParallelSetting> = listOf(
        ParallelSetting(Catalog.PAR_SERREES, "Serrées", note = "Écart le plus étroit"),
        ParallelSetting(Catalog.PAR_MOYENNES, "Moyennes", note = "Écart intermédiaire"),
        ParallelSetting(Catalog.PAR_LARGES, "Légèrement larges", note = "Écart le plus large"),
    ),
    val straightBarOwned: Boolean = true,
    val floorOk: Boolean = true,
    val platformOwned: Boolean = true,
    val platformHeightCm: Int = 30,
) {
    /** Charges possibles à la ceinture avec les poids disponibles (combinaisons). */
    fun beltPresets(): List<Double> {
        val units = plates.flatMap { p -> List(p.quantity.coerceIn(0, 4)) { p.kg } }
        var sums = setOf(0.0)
        for (u in units) sums = sums + sums.map { it + u }
        return sums.filter { it > 0 && it <= beltMaxKg }.sorted()
    }

    fun parallelEnabled(key: String) = parallels.firstOrNull { it.key == key }?.enabled ?: true
}

@Serializable
data class AppSettings(
    val outdoorMode: Boolean = false,
    val sounds: Boolean = true,
    val voice: Boolean = true,
    val vibration: Boolean = true,
    val volumeBoost: Boolean = true,
    val fullScreenAlerts: Boolean = true,
    val countdownSec: Int = 5,
    val prepSec: Int = 10,
    val secPerRep: Int = 3,
    val loadIncrementKg: Double = 2.0,
)
