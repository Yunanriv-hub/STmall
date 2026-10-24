package com.fktm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fktm.app.settings.ThemeMode

private val Teal = Color(0xFF4DD0B1)
private val TealDark = Color(0xFF0F6E5C)
private val Danger = Color(0xFFE5534B)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06231D),
    primaryContainer = Color(0xFF123D34),
    onPrimaryContainer = Color(0xFFD6FFF4),
    secondary = TealDark,
    onSecondary = Color(0xFFE8FFF8),
    secondaryContainer = Color(0xFF17352F),
    onSecondaryContainer = Color(0xFFD1EEE6),
    tertiary = Color(0xFF84B8E8),
    onTertiary = Color(0xFF08263D),
    tertiaryContainer = Color(0xFF1A3348),
    onTertiaryContainer = Color(0xFFD5E9FA),
    error = Danger,
    onError = Color.Black,
    errorContainer = Color(0xFF4A1714),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFF0F3F2),
    surface = Color(0xFF0A0C0B),
    onSurface = Color(0xFFF0F3F2),
    surfaceVariant = Color(0xFF151A18),
    onSurfaceVariant = Color(0xFFC2CAC6),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080A09),
    surfaceContainer = Color(0xFF0D100F),
    surfaceContainerHigh = Color(0xFF121614),
    surfaceContainerHighest = Color(0xFF171C1A),
    outline = Color(0xFF68736E),
    outlineVariant = Color(0xFF303934),
    inverseSurface = Color(0xFFE8EFEC),
    inverseOnSurface = Color(0xFF17201C),
    inversePrimary = TealDark,
)

private val LightScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4ED),
    onPrimaryContainer = Color(0xFF0B4E42),
    secondary = Color(0xFF497C70),
    secondaryContainer = Color(0xFFE7F2EF),
    background = Color(0xFFF5F7F5),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C2522),
    onSurface = Color(0xFF1C2522),
    error = Danger,
    onError = Color.White,
)

fun resolveDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FktmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
