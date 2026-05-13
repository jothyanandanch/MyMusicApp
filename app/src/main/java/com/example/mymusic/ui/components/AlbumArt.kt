package com.example.mymusic.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

// ─── Shared colour tokens (same as MusicLibraryScreen) ─────────────
private val ArtSurface  = Color(0xFF3E3E3E)
private val ArtGreen    = Color(0xFF1DB954)
private val ArtGray     = Color(0xFFB3B3B3)

/**
 * Loads album art from [artUri] (a content:// URI from MediaStore).
 * Falls back to a green MusicNote icon if the URI is null or fails.
 *
 * @param artUri      The album art URI from [Song.getAlbumArtUri()], may be null.
 * @param isActive    When true the fallback icon is tinted green; otherwise gray.
 * @param size        The width & height of the square artwork box.
 * @param cornerRadius Border-radius of the artwork box. Default 4 dp (Spotify style).
 */
@Composable
fun AlbumArt(
    artUri: Uri?,
    isActive: Boolean = false,
    size: Dp = 48.dp,
    cornerRadius: Dp = 4.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(ArtSurface),
        contentAlignment = Alignment.Center
    ) {
        if (artUri != null) {
            SubcomposeAsyncImage(
                model = artUri,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                // Show fallback icon while loading or on error
                error = {
                    FallbackIcon(isActive = isActive, size = size)
                },
                loading = {
                    FallbackIcon(isActive = isActive, size = size)
                }
            )
        } else {
            FallbackIcon(isActive = isActive, size = size)
        }
    }
}

@Composable
private fun FallbackIcon(isActive: Boolean, size: Dp) {
    Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        tint = if (isActive) ArtGreen else ArtGray,
        modifier = Modifier.size(size * 0.5f)
    )
}
