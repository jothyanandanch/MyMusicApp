package com.nandu.mymusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpotifyColorScheme = darkColorScheme(
    primary        = Color(0xFF1DB954),   // Spotify Green
    onPrimary      = Color(0xFF000000),
    background     = Color(0xFF121212),   // Spotify Black
    onBackground   = Color(0xFFFFFFFF),
    surface        = Color(0xFF1E1E1E),   // Spotify Surface
    onSurface      = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB3B3B3), // Spotify Gray
    secondary      = Color(0xFFB3B3B3),
    onSecondary    = Color(0xFF000000),
)

@Composable
fun SpotifyUIAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyColorScheme,
        typography  = Typography,
        content     = content
    )
}