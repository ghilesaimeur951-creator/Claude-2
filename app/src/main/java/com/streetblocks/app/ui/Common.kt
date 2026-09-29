package com.streetblocks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streetblocks.app.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer non fourni") }

@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalAppContainer.current
    return viewModel(key = key) { create(c) }
}

/**
 * Forme « pièce de puzzle » : encoche en haut, tenon en bas,
 * pour que les blocs s'emboîtent visuellement les uns dans les autres.
 */
class PuzzleShape(
    private val tab: Dp = PuzzleTab,
    private val tabStart: Dp = 22.dp,
    private val tabWidth: Dp = 40.dp,
    private val radius: Dp = 10.dp,
    private val topNotch: Boolean = true,
    private val bottomTab: Boolean = true,
    private val roundTop: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val t = tab.toPx()
            val s = tabStart.toPx()
            val w = tabWidth.toPx()
            val r = radius.toPx()
            val topR = if (roundTop) r * 2.2f else r
            val bottom = if (bottomTab) size.height - t else size.height
            val p = Path().apply {
                moveTo(0f, topR)
                cubicTo(0f, topR / 3, topR / 3, 0f, topR, 0f)
                if (topNotch) {
                    lineTo(s, 0f); lineTo(s + t, t); lineTo(s + w - t, t); lineTo(s + w, 0f)
                }
                lineTo(size.width - r, 0f)
                cubicTo(size.width - r / 3, 0f, size.width, r / 3, size.width, r)
                lineTo(size.width, bottom - r)
                cubicTo(size.width, bottom - r / 3, size.width - r / 3, bottom, size.width - r, bottom)
                if (bottomTab) {
                    lineTo(s + w, bottom); lineTo(s + w - t, bottom + t); lineTo(s + t, bottom + t); lineTo(s, bottom)
                }
                lineTo(r, bottom)
                cubicTo(r / 3, bottom, 0f, bottom - r / 3, 0f, bottom - r)
                close()
            }
            return Outline.Generic(p)
        }
    }
}

val PuzzleTab = 8.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Moins") }
        Text(
            value,
            modifier = Modifier.width(92.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Plus") }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

data class ChipItem(val key: String, val label: String, val dot: Color? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(items: List<ChipItem>, selected: String?, onSelect: (String) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item.key == selected,
                onClick = { onSelect(item.key) },
                label = { Text(item.label) },
                leadingIcon = item.dot?.let { c ->
                    @Composable {
                        Box(
                            Modifier.size(14.dp).background(c, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = if (accent.luminanceSafe() > 0.5f) Color.Black else Color.White,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChipGroup(items: List<ChipItem>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item.key in selected, onClick = { onToggle(item.key) }, label = { Text(item.label) })
        }
    }
}

fun Color.luminanceSafe(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Text(
        text,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}
