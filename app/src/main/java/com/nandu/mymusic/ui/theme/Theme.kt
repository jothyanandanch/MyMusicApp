package com.nandu.mymusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpotifyColorScheme = darkColorScheme(
    primary             = AppColors.Green,
    onPrimary           = AppColors.Black,
    primaryContainer    = AppColors.Green,
    onPrimaryContainer  = AppColors.Black,
    secondary           = AppColors.Gray,
    onSecondary         = AppColors.Black,
    secondaryContainer  = AppColors.Surface2,
    onSecondaryContainer = AppColors.White,
    background          = AppColors.Black,
    onBackground        = AppColors.White,
    surface             = AppColors.Surface,
    onSurface           = AppColors.White,
    surfaceVariant      = AppColors.Surface2,
    onSurfaceVariant    = AppColors.Gray,
    error               = Color(0xFFFF5555),
    onError             = AppColors.White,
    outline             = AppColors.LightGray,
    outlineVariant      = AppColors.LightGray,
)

@Composable
fun SpotifyUIAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyColorScheme,
        typography  = Typography,
        content     = content
    )
}
