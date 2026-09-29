package com.streetblocks.app.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetblocks.app.AppContainer
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.Workout
import com.streetblocks.app.engine.StepBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BuilderViewModel(val c: AppContainer, private val initialId: Long) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    val bands: StateFlow<List<Band>> = c.equipment.bands.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profile = c.equipment.profile
    val settings = c.settings.settings

    /** Durée totale estimée en secondes. */
    val estimatedSec: StateFlow<Int> = combine(_workout, bands, profile, settings) { w, b, p, s ->
        if (w == null) 0 else StepBuilder.totalSec(StepBuilder.build(w.blocks, b, p, s))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var saveJob: Job? = null
    private var dirty = false

    init {
        viewModelScope.launch {
            val id = if (initialId == 0L) {
                c.workouts.create("Nouvelle séance", listOf(Block.default(BlockType.WARMUP), Block.default(BlockType.END)))
            } else initialId
            _workout.value = c.workouts.get(id)
        }
    }

    private fun mutate(f: (Workout) -> Workout) {
        val w = _workout.value ?: return
        val n = f(w)
        _workout.value = n
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            c.workouts.save(n)
            dirty = false
        }
    }

    fun rename(name: String) = mutate { it.copy(name = name) }

    private fun blocks(f: (MutableList<Block>) -> Unit) = mutate { w -> w.copy(blocks = w.blocks.toMutableList().also(f)) }

    /** Ajoute un bloc juste avant le bloc « Fin » (ou à la fin). Retourne son id. */
    fun add(type: BlockType): String {
        val b = Block.default(type)
        blocks { list ->
            val endIdx = list.indexOfFirst { it.type == BlockType.END }
            if (type == BlockType.END || endIdx < 0) list.add(b) else list.add(endIdx, b)
        }
        return b.id
    }

    fun update(b: Block) = blocks { list ->
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) list[i] = b
    }

    fun duplicate(id: String): String? {
        var newId: String? = null
        blocks { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) {
                val copy = list[i].copyNew()
                newId = copy.id
                list.add(i + 1, copy)
            }
        }
        return newId
    }

    fun delete(id: String) = blocks { list -> list.removeAll { it.id == id } }

    fun moveByKey(fromKey: Any, toKey: Any) = blocks { list ->
        val from = list.indexOfFirst { it.id == fromKey }
        val to = list.indexOfFirst { it.id == toKey }
        if (from >= 0 && to >= 0 && from != to) list.add(to, list.removeAt(from))
    }

    fun moveBy(id: String, delta: Int) = blocks { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i >= 0 && j in list.indices) list.add(j, list.removeAt(i))
    }

    /** Insère un bloc repos après chaque exercice qui n'en a pas. */
    fun addRestsBetweenExercises(sec: Int = 120) = blocks { list ->
        var i = 0
        while (i < list.size) {
            val b = list[i]
            val next = list.getOrNull(i + 1)
            if (b.isExercise && next != null && next.type != BlockType.REST && next.type != BlockType.END) {
                list.add(i + 1, Block(type = BlockType.REST, sets = 1, workSec = sec))
                i++
            }
            i++
        }
    }

    fun saveNow() {
        val w = _workout.value ?: return
        saveJob?.cancel()
        c.appScope.launch { c.workouts.save(w) }
        dirty = false
    }

    override fun onCleared() {
        if (dirty) {
            val w = _workout.value
            if (w != null) c.appScope.launch { c.workouts.save(w) }
        }
        super.onCleared()
    }
}
