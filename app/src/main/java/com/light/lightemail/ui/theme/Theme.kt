package com.light.lightemail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackDarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    secondary = DarkGray,
    onSecondary = White,
    outline = DarkGray,
)

private val BlackLightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    secondary = LightGray,
    onSecondary = Black,
    outline = LightGray,
)

private val ThunderbirdLightColorScheme = lightColorScheme(
    primary = ThunderbirdBlue,
    onPrimary = White,
    primaryContainer = ThunderbirdLightBlue,
    onPrimaryContainer = ThunderbirdOnLightBlue,
    secondary = ThunderbirdCyan,
    onSecondary = White,
    secondaryContainer = ThunderbirdLightCyan,
    onSecondaryContainer = Color(0xFF001F29),
    tertiary = ThunderbirdAmber,
    onTertiary = White,
    tertiaryContainer = ThunderbirdLightAmber,
    onTertiaryContainer = Color(0xFF3B1800),
    background = ThunderbirdLightBackground,
    onBackground = ThunderbirdLightOnBackground,
    surface = ThunderbirdLightSurface,
    onSurface = ThunderbirdLightOnSurface,
    surfaceVariant = ThunderbirdLightSurfaceVariant,
    onSurfaceVariant = ThunderbirdLightOnSurfaceVariant,
    outline = ThunderbirdLightOutline,
    outlineVariant = ThunderbirdLightOutlineVariant,
    inverseSurface = Color(0xFF2E3440),
    inverseOnSurface = Color(0xFFF0F4F9),
    inversePrimary = ThunderbirdDarkPrimary
)

private val ThunderbirdDarkColorScheme = darkColorScheme(
    primary = ThunderbirdDarkPrimary,
    onPrimary = ThunderbirdDarkOnPrimary,
    primaryContainer = ThunderbirdDarkPrimaryContainer,
    onPrimaryContainer = ThunderbirdDarkOnPrimaryContainer,
    secondary = ThunderbirdBrightCyan,
    onSecondary = Color(0xFF003545),
    secondaryContainer = ThunderbirdDarkCyan,
    onSecondaryContainer = Color(0xFFC8F2FF),
    tertiary = ThunderbirdBrightAmber,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = ThunderbirdDarkAmber,
    onTertiaryContainer = Color(0xFFFFDBC7),
    background = ThunderbirdDarkBackground,
    onBackground = ThunderbirdDarkOnBackground,
    surface = ThunderbirdDarkSurface,
    onSurface = ThunderbirdDarkOnSurface,
    surfaceVariant = ThunderbirdDarkSurfaceVariant,
    onSurfaceVariant = ThunderbirdDarkOnSurfaceVariant,
    outline = ThunderbirdDarkOutline,
    outlineVariant = ThunderbirdDarkOutlineVariant,
    inverseSurface = ThunderbirdLightSurface,
    inverseOnSurface = ThunderbirdLightOnSurface,
    inversePrimary = ThunderbirdBlue
)

private val PosteoLightColorScheme = lightColorScheme(
    primary = PosteoGreen,
    onPrimary = White,
    primaryContainer = PosteoLightGreen,
    onPrimaryContainer = PosteoOnLightGreen,
    secondary = PosteoTeal,
    onSecondary = White,
    secondaryContainer = PosteoLightTeal,
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = PosteoAmber,
    onTertiary = Black,
    tertiaryContainer = PosteoLightAmber,
    onTertiaryContainer = Color(0xFF261A00),
    background = PosteoLightBackground,
    onBackground = PosteoLightOnBackground,
    surface = PosteoLightSurface,
    onSurface = PosteoLightOnSurface,
    surfaceVariant = PosteoLightSurfaceVariant,
    onSurfaceVariant = PosteoLightOnSurfaceVariant,
    outline = PosteoLightOutline,
    outlineVariant = PosteoLightOutlineVariant,
    inverseSurface = Color(0xFF2C342C),
    inverseOnSurface = Color(0xFFEFF5ED),
    inversePrimary = PosteoDarkPrimary
)

private val PosteoDarkColorScheme = darkColorScheme(
    primary = PosteoDarkPrimary,
    onPrimary = PosteoDarkOnPrimary,
    primaryContainer = PosteoDarkPrimaryContainer,
    onPrimaryContainer = PosteoDarkOnPrimaryContainer,
    secondary = PosteoBrightTeal,
    onSecondary = Color(0xFF003734),
    secondaryContainer = PosteoDarkTeal,
    onSecondaryContainer = Color(0xFFB2ECEF),
    tertiary = PosteoBrightAmber,
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = PosteoDarkAmber,
    onTertiaryContainer = Color(0xFFFFE08B),
    background = PosteoDarkBackground,
    onBackground = PosteoDarkOnBackground,
    surface = PosteoDarkSurface,
    onSurface = PosteoDarkOnSurface,
    surfaceVariant = PosteoDarkSurfaceVariant,
    onSurfaceVariant = PosteoDarkOnSurfaceVariant,
    outline = PosteoDarkOutline,
    outlineVariant = PosteoDarkOutlineVariant,
    inverseSurface = PosteoLightSurface,
    inverseOnSurface = PosteoLightOnSurface,
    inversePrimary = PosteoGreen
)

@Composable
fun LightEmailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: String = "black",
    useColorMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val mode = if (themeMode.isNotEmpty() && themeMode != "black") {
        themeMode
    } else if (useColorMode) {
        "color"
    } else {
        "black"
    }

    val colorScheme = when (mode) {
        "color" -> if (darkTheme) ThunderbirdDarkColorScheme else ThunderbirdLightColorScheme
        "posteo" -> if (darkTheme) PosteoDarkColorScheme else PosteoLightColorScheme
        else -> if (darkTheme) BlackDarkColorScheme else BlackLightColorScheme
    }

    val customTextSelectionColors = TextSelectionColors(
        handleColor = colorScheme.primary,
        backgroundColor = colorScheme.primary.copy(alpha = 0.35f)
    )

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides customTextSelectionColors,
            content = content
        )
    }
}
