package com.streetblocks.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.streetblocks.app.data.model.BlockType

val Lime = Color(0xFFC6FF00)
val Orange = Color(0xFFFF6D00)
val Bg = Color(0xFF0E0F13)
val Surface1 = Color(0xFF181A20)
val Surface2 = Color(0xFF232630)
val Muted = Color(0xFFA3A8B8)
val Danger = Color(0xFFFF5252)

private val scheme = darkColorScheme(
    primary = Lime,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A4A00),
    onPrimaryContainer = Lime,
    secondary = Orange,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Color(0xFF2C303B),
    surfaceContainerLow = Color(0xFF14161B),
    outline = Color(0xFF3A3E4A),
    error = Danger,
)

private val baseTypo = Typography()
private val typo = baseTypo.copy(
    headlineLarge = baseTypo.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = baseTypo.headlineMedium.copy(fontWeight = FontWeight.Black),
    headlineSmall = baseTypo.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = baseTypo.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = baseTypo.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp),
)

@Composable
fun StreetBlocksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}

/** Couleurs des blocs, inspirées des langages de programmation par blocs. */
fun blockColor(t: BlockType): Color = when (t) {
    BlockType.WARMUP -> Color(0xFFFFAB19)
    BlockType.PULLUP -> Color(0xFF4C97FF)
    BlockType.DIPS -> Color(0xFF9966FF)
    BlockType.PUSHUP -> Color(0xFFFF6680)
    BlockType.STATIC -> Color(0xFF0FBD8C)
    BlockType.FREE -> Color(0xFFFF8C1A)
    BlockType.REST -> Color(0xFF5CB1D6)
    BlockType.END -> Color(0xFFFF4D4D)
}

fun blockIcon(t: BlockType): ImageVector = when (t) {
    BlockType.WARMUP -> Icons.Filled.LocalFireDepartment
    BlockType.PULLUP -> Icons.Filled.SportsGymnastics
    BlockType.DIPS -> Icons.Filled.AccessibilityNew
    BlockType.PUSHUP -> Icons.Filled.SportsMartialArts
    BlockType.STATIC -> Icons.Filled.SelfImprovement
    BlockType.FREE -> Icons.AutoMirrored.Filled.DirectionsRun
    BlockType.REST -> Icons.Filled.HourglassBottom
    BlockType.END -> Icons.Filled.Flag
}

fun parseHex(hex: String, fallback: Color = Color.Gray): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
