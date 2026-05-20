package com.nandu.mymusic.ui.components

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
import com.nandu.mymusic.utils.CoverArtUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArtSurface = Color(0xFF3E3E3E)
private val ArtGreen   = Color(0xFF1DB954)
private val ArtGray    = Color(0xFFB3B3B3)

@Composable
fun AlbumArt(
    audioUri: Uri,
    title: String = "",   // ✅ Added title
    artist: String = "",  // ✅ Added artist
    isActive: Boolean = false,
    size: Dp? = 48.dp,
    cornerRadius: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // This can now hold either a local ByteArray OR a web URL string!
    var artModel by remember(audioUri) { mutableStateOf<Any?>(null) }
    var isLoading by remember(audioUri) { mutableStateOf(true) }

    LaunchedEffect(audioUri) {
        withContext(Dispatchers.IO) {
            val uriString = audioUri.toString()

            // 1. If it's an online HTTP URL, use the iTunes API!
            if (uriString.startsWith("http")) {
                val webUrl = CoverArtUtils.fetchAlbumArtUrl(title, artist)
                artModel = webUrl
            }
            // 2. If it's a local file, extract it like before
            else {
                artModel = extractLocalAlbumArt(context, audioUri)
            }
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
        // Coil handles both ByteArrays and Web URLs automatically!
        if (artModel != null) {
            SubcomposeAsyncImage(
                model = artModel,
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

private fun extractLocalAlbumArt(context: Context, uri: Uri): ByteArray? {
    // Keep your exact same local extraction code here...
    // (Method 1, Method 2, Method 3 using JAudioTagger)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        } catch (ignore: Exception) {}
    }
    // ... [Rest of your existing extractAlbumArt code] ...
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