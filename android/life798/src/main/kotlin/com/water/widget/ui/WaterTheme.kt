package com.water.widget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.water.widget.AppThemeMode

/** Theme-aware colors for water status, feedback, and layered surfaces. */
data class WaterSemanticColors(
    val waterStrong: Color,
    val danger: Color,
    val onDanger: Color,
    val surfaceRaised: Color,
    val outlineSoft: Color,
    val scrim: Color
)

private val LightSemanticColors = WaterSemanticColors(
    waterStrong = Color(0xFF0F6E5C),
    danger = Color(0xFFB3261E),
    onDanger = Color.White,
    surfaceRaised = Color(0xFFFFFFFF),
    outlineSoft = Color(0xFFC7D4CE),
    scrim = Color(0x990A1713)
)

private val DarkSemanticColors = WaterSemanticColors(
    waterStrong = Color(0xFF4DD0B1),
    danger = Color(0xFFFFB4AB),
    onDanger = Color(0xFF690005),
    surfaceRaised = Color(0xFF121614),
    outlineSoft = Color(0xFF303934),
    scrim = Color(0xB300090C)
)

private val LocalWaterSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** Shared semantic tokens. Read [colors] inside a [WaterTheme]. */
object WaterThemeTokens {
    val colors: WaterSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWaterSemanticColors.current
}

/** Consistent rounded geometry for cards, controls, sheets, and feature artwork. */
object WaterShapeTokens {
    val compact = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val prominent = RoundedCornerShape(28.dp)

    internal val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = compact,
        medium = card,
        large = prominent,
        extraLarge = RoundedCornerShape(32.dp)
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4ED),
    onPrimaryContainer = Color(0xFF0B4E42),
    secondary = Color(0xFF497C70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F2EF),
    onSecondaryContainer = Color(0xFF0B4E42),
    tertiary = Color(0xFF497C70),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF4ED),
    onTertiaryContainer = Color(0xFF0B4E42),
    error = LightSemanticColors.danger,
    onError = LightSemanticColors.onDanger,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F7F5),
    onBackground = Color(0xFF1C2522),
    surface = Color(0xFFFFFFFF),
    surfaceContainerHigh = LightSemanticColors.surfaceRaised,
    onSurface = Color(0xFF1C2522),
    surfaceVariant = Color(0xFFE9EFEC),
    onSurfaceVariant = Color(0xFF5B6863),
    outline = Color(0xFF68736E),
    outlineVariant = LightSemanticColors.outlineSoft,
    scrim = LightSemanticColors.scrim
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0B1),
    onPrimary = Color(0xFF06231D),
    primaryContainer = Color(0xFF123D34),
    onPrimaryContainer = Color(0xFFD6FFF4),
    secondary = Color(0xFF0F6E5C),
    onSecondary = Color(0xFFE8FFF8),
    secondaryContainer = Color(0xFF17352F),
    onSecondaryContainer = Color(0xFFD1EEE6),
    tertiary = Color(0xFF84B8E8),
    onTertiary = Color(0xFF08263D),
    tertiaryContainer = Color(0xFF1A3348),
    onTertiaryContainer = Color(0xFFD5E9FA),
    error = DarkSemanticColors.danger,
    onError = DarkSemanticColors.onDanger,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFF0F3F2),
    surface = Color(0xFF0A0C0B),
    surfaceContainerHigh = DarkSemanticColors.surfaceRaised,
    onSurface = Color(0xFFF0F3F2),
    surfaceVariant = Color(0xFF151A18),
    onSurfaceVariant = Color(0xFFC2CAC6),
    outline = Color(0xFF68736E),
    outlineVariant = DarkSemanticColors.outlineSoft,
    scrim = DarkSemanticColors.scrim
)

@Composable
fun WaterTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalWaterSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = WaterShapeTokens.material,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                content = content,
            )
        }
    }
}
