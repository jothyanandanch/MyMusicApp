package com.nandu.mymusic.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.utils.CoverArtUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumArt(
    audioUri: Uri? = null,
    title: String = "",
    artist: String = "",
    isActive: Boolean = false,
    size: Dp? = null,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
    song: Song? = null // Optional fallback if you pass the full song object
) {
    var imageUrl by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(audioUri, song) {
        // 1. If full Song object is provided and has Cloudinary URL
        if (song != null && song.albumArtUri != null && song.albumArtUri.toString().startsWith("http")) {
            imageUrl = song.albumArtUri.toString()
        }
        // 2. Derive Cloudinary Cover URL from Cloudinary Audio URL dynamically
        else if (audioUri != null && audioUri.toString().contains("cloudinary.com")) {
            val urlString = audioUri.toString()
            // Transforms your audio URL directly into the matching cover URL
            imageUrl = urlString
                .replace("/video/upload/", "/image/upload/")
                .replace("/songs/", "/covers/")
                .replace(".mp3", ".jpg")
        }
        // 3. If online URL but no cover in DB, fallback to iTunes API
        else if (audioUri != null && audioUri.toString().startsWith("http")) {
            withContext(Dispatchers.IO) {
                // ✅ FIXED TYPO: Changed to fetchAlbumArtUrl to match CoverArtUtils.kt
                imageUrl = CoverArtUtils.fetchAlbumArtUrl(title, artist)
            }
        }
        // 4. Otherwise, it's local
        else {
            // Coil automatically handles MediaStore audio URIs to extract embedded offline album art
            imageUrl = song?.albumArtUri ?: audioUri
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    // Apply size if provided, otherwise let it fill available constraints
    val boxModifier = if (size != null) modifier.size(size) else modifier

    Box(
        modifier = boxModifier
            .shadow(if (isActive) 8.dp else 2.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}