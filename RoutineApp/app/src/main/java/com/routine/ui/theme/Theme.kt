package com.routine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

val GreenPrimary = Color(0xFF2E7D32)
val GreenSecondary = Color(0xFF558B2F)
val AmberAccent = Color(0xFFF57F17)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7EFC0),
    onPrimaryContainer = Color(0xFF08290D),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF875200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFEAF0E4),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF0C3911),
    primaryContainer = Color(0xFF255328),
    onPrimaryContainer = Color(0xFFC0EFC1),
    secondary = Color(0xFFBACCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3A4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFFFB868),
    onTertiary = Color(0xFF482900),
    tertiaryContainer = Color(0xFF663D00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F120E),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF161A15),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF20251F),
    onSurfaceVariant = Color(0xFFC0C9BC),
    outline = Color(0xFF8A9385)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
