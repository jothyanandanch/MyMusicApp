package com.nandu.mymusic.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Apple Music–style palette. Single source of truth for the app's colors.
 * Light and dark instances are selected in [AppleMusicTheme] and provided to
 * the UI via [LocalAppleColors] (read with `appleColors()` inside composables).
 */
data class AppleColors(
    val background: Color,      // page background (systemGroupedBackground)
    val card: Color,            // grouped cards / list rows
    val cardElevated: Color,    // sheets, dialogs, chips, menus
    val fill: Color,            // iOS systemFill (search fields, segmented track)
    val separator: Color,       // hairline dividers
    val label: Color,           // primary text / icons
    val secondaryLabel: Color,  // secondary text
    val tertiaryLabel: Color,   // placeholder / disabled text
    val accent: Color,          // Apple Music red
    val systemGreen: Color,     // switches (iOS green)
    val destructive: Color,     // destructive actions (iOS red)
    val bar: Color,             // translucent bar material (nav, mini player)
    val barSeparator: Color,    // hairline on translucent bars
)

val LightAppleColors = AppleColors(
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFFFFFFF),
    fill = Color(0x29787880),
    separator = Color(0x4A3C3C43),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8E8E93),
    tertiaryLabel = Color(0x4D3C3C43),
    accent = Color(0xFFFA233B),
    systemGreen = Color(0xFF34C759),
    destructive = Color(0xFFFF3B30),
    bar = Color(0xF2FFFFFF),
    barSeparator = Color(0x4D3C3C43),
)

val DarkAppleColors = AppleColors(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardElevated = Color(0xFF2C2C2E),
    fill = Color(0x29787880),
    separator = Color(0xA6545458),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF8D8D93),
    tertiaryLabel = Color(0x4DEBEBF5),
    accent = Color(0xFFFC3C44),
    systemGreen = Color(0xFF30D158),
    destructive = Color(0xFFFF453A),
    bar = Color(0xF21C1C1E),
    barSeparator = Color(0xA6545458),
)

val LocalAppleColors = staticCompositionLocalOf { DarkAppleColors }
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Read the current Apple Music palette inside a composable. */
@androidx.compose.runtime.Composable
fun appleColors(): AppleColors = LocalAppleColors.current

/** Gradient used for the pinned "Liked Songs" artwork tile. */
val LikedSongsGradient = listOf(Color(0xFFFA233B), Color(0xFFFB5C74))

/** Fixed gradient pairs for Browse category tiles. */
val CategoryGradients = listOf(
    listOf(Color(0xFFFA233B), Color(0xFFFB5C74)), // red
    listOf(Color(0xFF1D77EF), Color(0xFF64D2FF)), // blue
    listOf(Color(0xFF30D158), Color(0xFF66D4CF)), // green
    listOf(Color(0xFFFF9F0A), Color(0xFFFFD60A)), // amber
    listOf(Color(0xFFBF5AF2), Color(0xFFDA9CFF)), // purple
    listOf(Color(0xFFFF375F), Color(0xFFFF6482)), // pink
    listOf(Color(0xFF5E5CE6), Color(0xFF7D7AFF)), // indigo
    listOf(Color(0xFF64D2FF), Color(0xFF0A84FF)), // teal-blue
)

/** Static fallback gradient for the Now Playing backdrop (replaced by art-derived colors when loaded). */
object NpFallbackGradient {
    val Top = Color(0xFF3A2E33)
    val Mid = Color(0xFF1C1518)
}
