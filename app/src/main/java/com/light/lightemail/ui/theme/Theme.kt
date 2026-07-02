package com.light.lightemail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    outline = DarkGray
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
    outline = LightGray
)

private val ThunderbirdLightColorScheme = lightColorScheme(
    primary = ThunderbirdBlue,
    onPrimary = White,
    background = ThunderbirdSurface,
    onBackground = ThunderbirdOnSurface,
    surface = White,
    onSurface = ThunderbirdOnSurface,
    primaryContainer = ThunderbirdLightBlue,
    onPrimaryContainer = ThunderbirdDarkBlue,
    secondary = ThunderbirdBlue,
    onSecondary = White,
    outline = LightGray
)

private val ThunderbirdDarkColorScheme = darkColorScheme(
    primary = ThunderbirdBlue,
    onPrimary = White,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    primaryContainer = ThunderbirdDarkBlue,
    onPrimaryContainer = ThunderbirdLightBlue,
    secondary = ThunderbirdBlue,
    onSecondary = White,
    outline = DarkGray
)

@Composable
fun LightEmailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useColorMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useColorMode && darkTheme -> ThunderbirdDarkColorScheme
        useColorMode -> ThunderbirdLightColorScheme
        darkTheme -> BlackDarkColorScheme
        else -> BlackLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
