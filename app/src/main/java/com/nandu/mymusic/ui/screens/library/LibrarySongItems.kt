package com.nandu.mymusic.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.theme.appleColors

/**
 * The song rows used in library/search/collection lists: a full
 * [SongItem] (with the overflow menu) and a compact [LibraryGroupItem]
 * (browse/search grouping with optional add-to-playlist/queue).
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song             : Song,
    currentSong      : Song?,
    isFavorite       : Boolean,
    onSongClick      : () -> Unit,
    onToggleFavorite : () -> Unit,
    onPlayNext       : () -> Unit = {},
    onAddToQueue     : () -> Unit = {},
    onAddToPlaylist  : () -> Unit = {},
    onDelete         : () -> Unit = {},
    onEdit           : () -> Unit = {},
    onDownload       : () -> Unit = {},
    onViewAlbum      : () -> Unit = {},
    onViewArtist     : () -> Unit = {},
    showTrackNumber  : Boolean = false,
    trackNumber      : Int = 0
) {
    val C = appleColors()
    var showMenu by remember { mutableStateOf(false) }
    val isOnline = song.uri.toString().startsWith("http")
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onSongClick)
            .background(if (song.id == currentSong?.id) C.card else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showTrackNumber) {
            Text(
                text = "$trackNumber",
                color = if (song.id == currentSong?.id) C.accent else C.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.requiredWidth(28.dp)
            )
        }
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = song.id == currentSong?.id, size = 48.dp, cornerRadius = 6.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title ?: "Unknown Title", color = if (song.id == currentSong?.id) C.accent else C.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (isFavorite) C.accent else C.secondaryLabel, modifier = Modifier.size(18.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOnline) Icon(Icons.Filled.Cloud, contentDescription = "Online", tint = C.accent, modifier = Modifier.size(14.dp).padding(end = 4.dp))
            Text(formatDuration(song.duration), color = C.secondaryLabel, fontSize = 12.sp)
        }

        Spacer(Modifier.width(4.dp))

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp)) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                DropdownMenuItem(text = { Text("View Album", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onViewAlbum() })
                DropdownMenuItem(text = { Text("View Artist", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onViewArtist() })
                DropdownMenuItem(text = { Text("Play Next", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onPlayNext() })
                DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onAddToQueue() })
                DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onAddToPlaylist() })

                if (isOnline) {
                    DropdownMenuItem(text = { Text("Download Song", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onDownload() })
                } else {
                    DropdownMenuItem(text = { Text("Edit Song Info", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onEdit() })
                    DropdownMenuItem(text = { Text("Delete from Device", color = C.destructive) }, onClick = { showMenu = false; focusManager.clearFocus(); onDelete() })
                }
            }
        }
    }
    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
}

@Composable
fun LibraryGroupItem(
    title: String, subtitle: String, audioUri: Uri?, song: Song? = null, onClick: () -> Unit, isCircular: Boolean = false,
    songs: List<Song> = emptyList(),
    onAddToPlaylist: ((List<Song>) -> Unit)? = null,
    onAddToQueue: ((List<Song>) -> Unit)? = null,
    onAddShuffledToQueue: ((List<Song>) -> Unit)? = null
) {
    val C = appleColors()
    var showMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isCircular) { AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 28.dp)
        } else { AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 8.dp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = C.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = C.secondaryLabel, fontSize = 13.sp)
        }
        if (onAddToPlaylist != null || onAddToQueue != null || onAddShuffledToQueue != null) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = { showMenu = false; onAddToPlaylist(songs) })
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = { showMenu = false; if (songs.isNotEmpty()) onAddToQueue(songs) })
                    }
                    if (onAddShuffledToQueue != null) {
                        DropdownMenuItem(text = { Text("Add to Queue (Shuffled)", color = C.label) }, onClick = { showMenu = false; if (songs.isNotEmpty()) onAddShuffledToQueue(songs) })
                    }
                }
            }
        }
    }
}
