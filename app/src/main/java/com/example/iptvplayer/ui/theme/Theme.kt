package com.example.iptvplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    secondary = Color(0xFF00B0FF),
    background = Color(0xFF000000),
    surface = Color(0xFF101418),
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1)
)

@Composable
fun IPTVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else DarkColors,
        content = content
    )
}
