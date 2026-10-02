package com.nandu.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.theme.AppleText
import com.nandu.mymusic.ui.theme.appleColors

/**
 * Apple Music style shelf cards: a horizontal single-song card and a
 * playlist card whose cover is a 2×2 mosaic of the first four tracks.
 */

// ─── Apple Music style song card for horizontal shelves ───────────────
@Composable
fun SongShelfCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val C = appleColors()
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = false, size = null, cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(song.title ?: "Unknown Title", color = C.label, style = AppleText.Footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, style = AppleText.Caption1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Apple Music style playlist artwork: a 2×2 mosaic of the first four tracks'
 * covers. Falls back to a gradient tile (e.g. Liked Songs' red heart) or a
 * plain fill when the playlist has no tracks.
 */
@Composable
fun PlaylistCover(
    songs: List<Song>,
    cornerRadius: Dp,
    gradient: List<Color>? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val C = appleColors()
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(C.fill)) {
        when {
            songs.isEmpty() && gradient != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon ?: Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Icon(icon ?: Icons.Filled.MusicNote, null, tint = C.secondaryLabel, modifier = Modifier.size(36.dp)) }
            }
            songs.size == 1 -> {
                AlbumArt(
                    song = songs[0], audioUri = songs[0].uri, title = songs[0].title, artist = songs[0].artist,
                    isActive = false, size = null, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // 2×2 mosaic with thin gaps, like Apple Music playlist covers
                Column(modifier = Modifier.fillMaxSize()) {
                    val rows = songs.take(4).chunked(2)
                    rows.forEach { rowSongs ->
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            rowSongs.forEach { s ->
                                AlbumArt(
                                    song = s, audioUri = s.uri, title = s.title, artist = s.artist,
                                    isActive = false, size = null, cornerRadius = 0.dp,
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                                )
                            }
                            if (rowSongs.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistShelfCard(
    name: String,
    songCount: Int,
    coverSongs: List<Song> = emptyList(),
    gradient: List<Color>? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = appleColors()
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        PlaylistCover(
            songs = coverSongs,
            cornerRadius = 10.dp,
            gradient = gradient,
            icon = icon,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
        Spacer(Modifier.height(6.dp))
        Text(name, color = C.label, style = AppleText.Footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$songCount song${if (songCount != 1) "s" else ""}", color = C.secondaryLabel, style = AppleText.Caption1, maxLines = 1)
    }
}
