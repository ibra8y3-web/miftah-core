package com.miftah.core.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B2BD9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DEFF), onPrimaryContainer = Color(0xFF1F0A5C),
    secondary = Color(0xFF00796B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFE8), onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A5100), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBA), onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE7E0EE), onSurfaceVariant = Color(0xFF47454F),
    outline = Color(0xFF787580), outlineVariant = Color(0xFFC9C4D0),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBDFF), onPrimary = Color(0xFF2F1585),
    primaryContainer = Color(0xFF4526B8), onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFF7DD6C8), onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048), onSecondaryContainer = Color(0xFFCDEFE8),
    tertiary = Color(0xFFFFB86B), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3C00), onTertiaryContainer = Color(0xFFFFDCBA),
    background = Color(0xFF131218), onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131218), onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47454F), onSurfaceVariant = Color(0xFFC9C4D0),
    outline = Color(0xFF928F9A), outlineVariant = Color(0xFF47454F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

/** Letter-spacing must be zero for Arabic, otherwise letters stop joining. */
private fun TextStyle.flat() = copy(letterSpacing = 0.sp)

private val BaseTypography = Typography()
private val Type = Typography(
    displayLarge = BaseTypography.displayLarge.flat(), displayMedium = BaseTypography.displayMedium.flat(),
    displaySmall = BaseTypography.displaySmall.flat(), headlineLarge = BaseTypography.headlineLarge.flat(),
    headlineMedium = BaseTypography.headlineMedium.flat(), headlineSmall = BaseTypography.headlineSmall.flat(),
    titleLarge = BaseTypography.titleLarge.flat(), titleMedium = BaseTypography.titleMedium.flat(),
    titleSmall = BaseTypography.titleSmall.flat(), bodyLarge = BaseTypography.bodyLarge.flat(),
    bodyMedium = BaseTypography.bodyMedium.flat(), bodySmall = BaseTypography.bodySmall.flat(),
    labelLarge = BaseTypography.labelLarge.flat(), labelMedium = BaseTypography.labelMedium.flat(),
    labelSmall = BaseTypography.labelSmall.flat(),
)

@Composable
fun MiftahTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Type, shapes = Shapes, content = content)
}

enum class Tone { OK, WARN, ERROR, NEUTRAL }

@Composable
internal fun toneColors(tone: Tone): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return when (tone) {
        Tone.OK -> if (dark) Color(0xFF0F3D2A) to Color(0xFF8DE0B5) else Color(0xFFD6F5E4) to Color(0xFF0B5A37)
        Tone.WARN -> cs.tertiaryContainer to cs.onTertiaryContainer
        Tone.ERROR -> cs.errorContainer to cs.onErrorContainer
        Tone.NEUTRAL -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}
