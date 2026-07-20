package com.nandu.mymusic.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MyMusic — Material You inspired warm/dynamic theme.
 * Single source of truth for the app's color palette. These values flow into
 * every screen via the Spotify* aliases declared in MusicLibraryScreen.kt —
 * names are kept for compatibility, but the values below now express the
 * app's new warm-amber "expressive" identity instead of Spotify green.
 */
object AppColors {
    val Black      = Color(0xFF17120E)   // base background
    val Surface    = Color(0xFF221A14)   // rows / sheets
    val Surface2   = Color(0xFF2E241C)   // elevated surface (dialogs, chips, selected rows)
    val Green      = Color(0xFFFF8A3D)   // app accent — warm amber-orange
    val White      = Color(0xFFF7EDE3)   // primary text / icons (warm off-white)
    val Gray       = Color(0xFFBBAA9C)   // secondary / muted text
    val LightGray  = Color(0xFF5C4F44)   // inactive tracks, borders, dividers

    // Additional Material-You tokens used by the Now Playing screen & hero headers
    val OnAccent    = Color(0xFF2B1400)  // dark text/icon for use on top of Green
    val GradientTop = Color(0xFF3A2415)  // top of the "now playing" dynamic gradient
    val GradientMid = Color(0xFF241A12)  // gradient midpoint
    val HeroLight   = Color(0xFFFFB870)  // light end of hero-header gradients
    val HeroDeep    = Color(0xFF8A3B1F)  // deep end of hero-header gradients
    val Error       = Color(0xFFFF6B55)  // destructive actions, warm-shifted red
    val SwipeDelete = Color(0xFFE2735F)  // swipe-to-delete background
}
