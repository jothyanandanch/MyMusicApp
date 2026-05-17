package com.example.mymusic.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArtSurface  = Color(0xFF3E3E3E)
private val ArtGreen    = Color(0xFF1DB954)
private val ArtGray     = Color(0xFFB3B3B3)

@Composable
fun AlbumArt(
    audioUri: Uri, // 🔥 Now taking the actual audio file URI
    isActive: Boolean = false,
    size: Dp? = 48.dp, // Nullable so it can stretch in the NowPlayingScreen
    cornerRadius: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var artByteArray by remember(audioUri) { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember(audioUri) { mutableStateOf(true) }

    // Extract the embedded ID3 cover art in the background
    LaunchedEffect(audioUri) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, audioUri)
                artByteArray = retriever.embeddedPicture
                retriever.release()
            } catch (e: Exception) {
                artByteArray = null
            } finally {
                isLoading = false
            }
        }
    }

    val boxModifier = if (size != null) modifier.size(size) else modifier.fillMaxSize()

    Box(
        modifier = boxModifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(ArtSurface),
        contentAlignment = Alignment.Center
    ) {
        if (artByteArray != null) {
            // Coil natively supports rendering byte arrays!
            SubcomposeAsyncImage(
                model = artByteArray,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { FallbackIcon(isActive) },
                loading = { FallbackIcon(isActive) }
            )
        } else if (!isLoading) {
            FallbackIcon(isActive)
        }
    }
}

@Composable
private fun FallbackIcon(isActive: Boolean) {
    Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        tint = if (isActive) ArtGreen else ArtGray,
        modifier = Modifier.fillMaxSize(0.5f)
    )
}