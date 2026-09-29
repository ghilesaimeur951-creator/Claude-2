package com.streetblocks.app.ui.builder

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.data.model.Band
import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockText
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.Catalog
import com.streetblocks.app.data.model.EquipmentProfile
import com.streetblocks.app.data.model.LoadMode
import com.streetblocks.app.data.model.timeLabel
import com.streetblocks.app.ui.PuzzleShape
import com.streetblocks.app.ui.PuzzleTab
import com.streetblocks.app.ui.containerViewModel
import com.streetblocks.app.ui.theme.Lime
import com.streetblocks.app.ui.theme.blockColor
import com.streetblocks.app.ui.theme.blockIcon
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(workoutId: Long, onBack: () -> Unit, onOpenSession: () -> Unit, planned: Boolean = false) {
    val vm = containerViewModel(key = "builder-$planned-$workoutId") { BuilderViewModel(it, workoutId, planned) }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val bands by vm.bands.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val estimated by vm.estimatedSec.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var showLibrary by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val plannedInfo by vm.plannedInfo.collectAsStateWithLifecycle()
    var askScope by remember { mutableStateOf(false) }

    // Séance planifiée modifiée + séances suivantes : demander la portée de la modification
    fun leave() {
        val info = plannedInfo
        if (planned && info != null && info.followingCount > 0 && vm.plannedChanged()) askScope = true
        else { vm.saveNow(); onBack() }
    }
    androidx.activity.compose.BackHandler(onBack = ::leave)

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        vm.moveByKey(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (planned) "Séance planifiée" else "Créateur de séance") },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = {
                        val w = workout ?: return@IconButton
                        vm.saveNow()
                        scope.launch {
                            kotlinx.coroutines.delay(150)
                            val ok = if (planned) vm.c.launchPlanned(w.id) else vm.c.launchSession(w.id)
                            if (ok) onOpenSession()
                            else Toast.makeText(ctx, "Ajoute au moins un exercice", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Filled.PlayArrow, "Démarrer", tint = Lime) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Ajouter un repos entre chaque exercice") }, onClick = { menu = false; vm.addRestsBetweenExercises() })
                            DropdownMenuItem(text = { Text("Ajouter le bloc « Fin de séance »") }, onClick = { menu = false; vm.add(BlockType.END) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showLibrary = true },
                containerColor = Lime,
                contentColor = Color.Black,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Ajouter un bloc", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        val w = workout
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(pad))
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            plannedInfo?.let { info ->
                Text(
                    if (info.done) "${com.streetblocks.app.data.model.Planning.dateLong(info.date)} · séance réalisée (lecture seule, historique préservé)"
                    else "${com.streetblocks.app.data.model.Planning.dateLong(info.date)} · configuration propre à cette date : la séance d'origine n'est pas modifiée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(Lime, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp),
                )
            }
            OutlinedTextField(
                value = w.name,
                onValueChange = vm::rename,
                label = { Text("Nom de la séance") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "${w.blocks.count { it.isExercise }} exercices · ${w.blocks.size} blocs · durée estimée ~${(estimated + 59) / 60} min",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Text(
                "Maintiens ⠿ et fais glisser pour réorganiser. Touche un bloc pour le configurer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            ) {
                item(key = "__start") { StartHat() }
                items(w.blocks, key = { it.id }) { block ->
                    ReorderableItem(reorderState, key = block.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "drag")
                        BlockCard(
                            block = block,
                            bands = bands,
                            profile = profile,
                            elevation = elevation,
                            handle = Modifier.draggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            ),
                            onClick = { editingId = block.id },
                            onDuplicate = { vm.duplicate(block.id) },
                            onDelete = { vm.delete(block.id) },
                            onUp = { vm.moveBy(block.id, -1) },
                            onDown = { vm.moveBy(block.id, 1) },
                        )
                    }
                }
                if (w.blocks.none { it.type == BlockType.END }) {
                    item(key = "__endhint") {
                        Text(
                            "Astuce : sans bloc « Fin », la séance se termine après le dernier bloc.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }

    if (askScope) {
        com.streetblocks.app.ui.programs.ScopeDialog(
            title = "Appliquer les modifications",
            followingCount = plannedInfo?.followingCount ?: 0,
            onPick = { scopeChoice ->
                askScope = false
                vm.finish(propagate = scopeChoice == com.streetblocks.app.data.model.EditScope.FOLLOWING) { onBack() }
            },
            onDismiss = { askScope = false },
        )
    }

    if (showLibrary) {
        ModalBottomSheet(onDismissRequest = { showLibrary = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BlockLibrary { type ->
                val id = vm.add(type)
                showLibrary = false
                if (type != BlockType.END) editingId = id
            }
        }
    }

    val editing = workout?.blocks?.firstOrNull { it.id == editingId }
    if (editing != null) {
        ModalBottomSheet(
            onDismissRequest = { editingId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BlockEditor(
                block = editing,
                bands = bands,
                profile = profile,
                onChange = vm::update,
                onDuplicate = { editingId = vm.duplicate(editing.id) },
                onDelete = { vm.delete(editing.id); editingId = null },
                onDone = { editingId = null },
            )
        }
    }
}

/** Bloc « chapeau » de départ, comme dans les langages par blocs. */
@Composable
private fun StartHat() {
    Row(
        Modifier
            .overlapBelow()
            .fillMaxWidth()
            .background(Lime, PuzzleShape(topNotch = false, roundTop = true))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp + PuzzleTab),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text("QUAND LA SÉANCE DÉMARRE", color = Color.Black, fontWeight = FontWeight.Black)
    }
}

/** Réduit la hauteur déclarée pour que le bloc suivant s'emboîte sur le tenon. */
@Composable
private fun Modifier.overlapBelow(): Modifier {
    val tabPx = with(LocalDensity.current) { PuzzleTab.roundToPx() }
    return this.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, (p.height - tabPx).coerceAtLeast(0)) { p.place(0, 0) }
    }
}

@Composable
private fun BlockCard(
    block: Block,
    bands: List<Band>,
    profile: EquipmentProfile,
    elevation: androidx.compose.ui.unit.Dp,
    handle: Modifier,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val color = blockColor(block.type)
    val isEnd = block.type == BlockType.END
    val shape = PuzzleShape(bottomTab = !isEnd)
    val compact = block.type == BlockType.REST || isEnd
    var menu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .then(if (!isEnd) Modifier.overlapBelow() else Modifier)
            .fillMaxWidth()
            .padding(start = if (block.type == BlockType.REST) 18.dp else 0.dp)
            .shadow(elevation, shape)
            .background(color, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 0.dp, top = PuzzleTab + (if (compact) 4.dp else 8.dp), bottom = (if (isEnd) 0.dp else PuzzleTab) + (if (compact) 6.dp else 10.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(blockIcon(block.type), null, tint = Color.White, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val title = when (block.type) {
                    BlockType.REST -> "REPOS  ${timeLabel(block.workSec)}"
                    BlockType.END -> "FIN DE SÉANCE"
                    else -> BlockText.exerciseLabel(block).uppercase()
                }
                Text(title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Text(
                        BlockText.summary(block, bands, profile),
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (block.loadMode == LoadMode.BAND && block.bandId == null) {
                        Text("⚠ Choisis un élastique", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Monter") }, leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) }, onClick = { menu = false; onUp() })
                    DropdownMenuItem(text = { Text("Descendre") }, leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) }, onClick = { menu = false; onDown() })
                    DropdownMenuItem(text = { Text("Dupliquer") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Glisser pour déplacer",
                tint = Color.White,
                modifier = handle.padding(end = 10.dp, start = 2.dp).size(30.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockLibrary(onPick: (BlockType) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text("Bibliothèque de blocs", style = MaterialTheme.typography.titleLarge)
        Text("Touche un bloc pour l'ajouter à ta séance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LibrarySection("Exercices", listOf(BlockType.PULLUP, BlockType.DIPS, BlockType.PUSHUP, BlockType.STATIC, BlockType.FREE), onPick)
        LibrarySection("Contrôle", listOf(BlockType.WARMUP, BlockType.REST, BlockType.END), onPick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySection(title: String, types: List<BlockType>, onPick: (BlockType) -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Lime, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
        types.forEach { t ->
            Column(
                Modifier
                    .weight(1f)
                    .height(86.dp)
                    .background(blockColor(t), PuzzleShape(bottomTab = t != BlockType.END, tabWidth = 30.dp, tabStart = 16.dp))
                    .clickable { onPick(t) }
                    .padding(start = 12.dp, end = 8.dp, top = PuzzleTab + 4.dp, bottom = PuzzleTab + 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(blockIcon(t), null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Catalog.typeLabel(t).uppercase(), color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                }
                Text(Catalog.typeDescription(t), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (types.size % 2 == 1) Spacer(Modifier.weight(1f))
    }
}
