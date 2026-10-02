package com.nandu.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.theme.appleColors

/**
 * The persistent chrome of the library: the floating Apple Music mini player
 * (transport + now-playing metadata) and the bottom tab bar.
 */

// ─── Floating Apple Music mini player ─────────────────────────────────
@Composable
fun MiniPlayerBar(
    song: Song, isPlaying: Boolean, progress: Long, duration: Long, isFavorite: Boolean,
    onTogglePlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onToggleFavorite: () -> Unit, onClick: () -> Unit
) {
    val C = appleColors()
    val fraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.bar)
            .border(0.5.dp, C.barSeparator, RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        // Thin progress line across the top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .align(Alignment.TopStart)
                .background(C.barSeparator.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.5.dp)
                    .background(C.accent)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 0.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = true, size = 54.dp, cornerRadius = 0.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(text = song.title ?: "Unknown Title", color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = C.label, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = C.label, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun AppleBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val C = appleColors()
    NavigationBar(containerColor = C.bar, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Icons.Filled.Home, "Listen Now", 0),
            Triple(Icons.Filled.GridView, "New", 1),
            Triple(Icons.AutoMirrored.Filled.QueueMusic, "Library", 2),
            Triple(Icons.Filled.Search, "Search", 3)
        )
        items.forEach { (icon, label, index) ->
            NavigationBarItem(
                icon = { Icon(icon, label, modifier = Modifier.size(24.dp)) }, label = { Text(label, fontSize = 10.sp) }, selected = selectedTab == index, onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = C.accent, selectedTextColor = C.accent, unselectedIconColor = C.secondaryLabel, unselectedTextColor = C.secondaryLabel, indicatorColor = Color.Transparent)
            )
        }
    }
}
