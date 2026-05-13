package com.example.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mymusic.model.Song
import com.example.mymusic.ui.screens.library.*

/**
 * ✅ FIXED: SongRow now displays album art and correct favorite icon status
 */
@Composable
fun SongRow(
    song: Song,
    currentSong: Song?,
    isFavorite: Boolean = false,  // ✅ NEW: Pass favorite state
    onSongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick() }
            .background(
                if (song.id == currentSong?.id) SpotifySurface
                else SpotifyBlack
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ✅ Album art with fallback to music note
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifySurface2),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art for ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (song.id == currentSong?.id) SpotifyGreen else SpotifyGray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Song title and artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (song.id == currentSong?.id) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = SpotifyGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ✅ FAVORITE HEART BUTTON — Shows correct state
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite
                else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(18.dp)
            )
        }

        // More options
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = null,
            tint = SpotifyGray,
            modifier = Modifier.size(20.dp)
        )
    }

    HorizontalDivider(
        color = SpotifySurface,
        thickness = 0.5.dp
    )
}

/**
 * Convert duration in milliseconds to MM:SS format
 */
fun convertTimestampToDuration(position: Long): String {
    val seconds = (position / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (position < 0) "--:--" else "%d:%02d".format(minutes, remainingSeconds)
}