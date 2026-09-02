package com.nandu.mymusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = LightAppleColors.accent,
    onPrimary = Color.White,
    secondary = LightAppleColors.systemGreen,
    onSecondary = Color.White,
    background = LightAppleColors.background,
    onBackground = LightAppleColors.label,
    surface = LightAppleColors.card,
    onSurface = LightAppleColors.label,
    surfaceVariant = LightAppleColors.cardElevated,
    onSurfaceVariant = LightAppleColors.secondaryLabel,
    surfaceContainer = LightAppleColors.card,
    surfaceContainerHigh = LightAppleColors.cardElevated,
    error = LightAppleColors.destructive,
    onError = Color.White,
    outline = LightAppleColors.separator,
    outlineVariant = LightAppleColors.separator,
)

private val DarkScheme = darkColorScheme(
    primary = DarkAppleColors.accent,
    onPrimary = Color.White,
    secondary = DarkAppleColors.systemGreen,
    onSecondary = Color.White,
    background = DarkAppleColors.background,
    onBackground = DarkAppleColors.label,
    surface = DarkAppleColors.card,
    onSurface = DarkAppleColors.label,
    surfaceVariant = DarkAppleColors.cardElevated,
    onSurfaceVariant = DarkAppleColors.secondaryLabel,
    surfaceContainer = DarkAppleColors.card,
    surfaceContainerHigh = DarkAppleColors.cardElevated,
    error = DarkAppleColors.destructive,
    onError = Color.White,
    outline = DarkAppleColors.separator,
    outlineVariant = DarkAppleColors.separator,
)

/** iOS-style corner radii. */
val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AppleMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkAppleColors else LightAppleColors
    val scheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalAppleColors provides palette,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = AppleShapes,
            content = content,
        )
    }
}
