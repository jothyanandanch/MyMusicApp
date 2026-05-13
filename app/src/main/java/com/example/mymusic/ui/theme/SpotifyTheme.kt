package com.example.mymusic.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Spotify exact colors
val SpotifyGreen   = Color(0xFF1DB954)
val SpotifyBlack   = Color(0xFF121212)
val SpotifyDark    = Color(0xFF181818)
val SpotifyGray    = Color(0xFF282828)
val SpotifyLightGray = Color(0xFFB3B3B3)
val SpotifyWhite   = Color(0xFFFFFFFF)

private val SpotifyColorScheme = darkColorScheme(
    primary          = SpotifyGreen,
    onPrimary        = SpotifyBlack,
    background       = SpotifyBlack,
    onBackground     = SpotifyWhite,
    surface          = SpotifyDark,
    onSurface        = SpotifyWhite,
    surfaceVariant   = SpotifyGray,
    onSurfaceVariant = SpotifyLightGray,
    secondary        = SpotifyLightGray,
    onSecondary      = SpotifyBlack,
)

@Composable
fun SpotifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyColorScheme,
        typography  = Typography(),
        content     = content
    )
}