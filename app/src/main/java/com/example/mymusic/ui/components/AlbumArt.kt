package com.example.mymusic.ui.components

import android.content.Context
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


private val ArtSurface = Color(0xFF3E3E3E)
private val ArtGreen   = Color(0xFF1DB954)
private val ArtGray    = Color(0xFFB3B3B3)

@Composable
fun AlbumArt(
    audioUri: Uri,
    isActive: Boolean = false,
    size: Dp? = 48.dp,
    cornerRadius: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var artByteArray by remember(audioUri) { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember(audioUri) { mutableStateOf(true) }

    LaunchedEffect(audioUri) {
        withContext(Dispatchers.IO) {
            artByteArray = extractAlbumArt(context, audioUri)
            isLoading = false
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



private fun extractAlbumArt(context: Context, uri: Uri): ByteArray? {
    // ── Method 1: Android 10+ Native Thumbnail API (Fastest) ─────────────────
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        } catch (ignore: Exception) {}
    }

    // ── Method 2: MediaMetadataRetriever via FileDescriptor ────────────────
    val retriever = android.media.MediaMetadataRetriever()
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            retriever.setDataSource(pfd.fileDescriptor)
            val art = retriever.embeddedPicture
            if (art != null) return art
        }
    } catch (ignore: Exception) {
    } finally {
        try { retriever.release() } catch (ignore: Exception) {}
    }

    // ── Method 3: JAudioTagger Fallback (Fix for ID3v2.4 & Scoped Storage) ─
    // If Android's native tools fail, we copy the file to a temporary cache
    // where JAudioTagger can safely read it without Scoped Storage blocking it.
    var tempFile: java.io.File? = null
    try {
        tempFile = java.io.File.createTempFile("temp_audio", ".mp3", context.cacheDir)

        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
        val artwork = audioFile?.tag?.firstArtwork
        if (artwork != null) {
            return artwork.binaryData
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        tempFile?.delete() // Always clean up to prevent storage leaks
    }

    return null
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