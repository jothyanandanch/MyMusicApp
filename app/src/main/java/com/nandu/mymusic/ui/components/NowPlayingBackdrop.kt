package com.nandu.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.theme.NpFallbackGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apple Music style Now Playing backdrop: a vertical gradient tinted by the
 * album art's dominant color, falling into black. Falls back to a static
 * gradient while loading (or when no art is available).
 */
@Composable
fun NowPlayingBackdrop(song: Song?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(song?.id, song?.albumArtUri, song?.uri) {
        if (song == null) return@LaunchedEffect
        dominantColor = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(song.albumArtUri ?: song.uri)
                    .size(64)
                    .allowHardware(false) // Palette requires a software bitmap
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = drawable?.let { d ->
                    if (d is android.graphics.drawable.BitmapDrawable) d.bitmap
                    else d.toBitmap(64, 64)
                }
                bitmap?.let {
                    Palette.from(it).maximumColorCount(24).generate().dominantSwatch?.rgb
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    val top = dominantColor?.let { Color(it) } ?: NpFallbackGradient.Top
    val mid = dominantColor?.let {
        Color(ColorUtils.blendARGB(it, android.graphics.Color.BLACK, 0.55f))
    } ?: NpFallbackGradient.Mid

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, mid, Color.Black), startY = 0f, endY = 1800f))
    )
}
