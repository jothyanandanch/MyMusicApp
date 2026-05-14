package com.example.mymusic.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymusic.model.Song
import com.example.mymusic.ui.screens.library.SpotifyBlack
import com.example.mymusic.ui.screens.library.SpotifyGray
import com.example.mymusic.ui.screens.library.SpotifyGreen
import com.example.mymusic.ui.screens.library.SpotifySurface
import com.example.mymusic.ui.screens.library.SpotifySurface2
import com.example.mymusic.ui.screens.library.SpotifyWhite
import com.example.mymusic.ui.screens.library.formatDuration
import com.example.mymusic.viewmodel.MusicViewModel

@Composable
fun PlaylistScreen(
    songs            : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    modifier         : Modifier = Modifier,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    viewModel        : MusicViewModel
) {
    // Observe the map of custom playlists
    val customPlaylists by viewModel.customPlaylists.observeAsState(initial = emptyMap())

    var showLocalDetail by remember { mutableStateOf(false) }
    var selectedCustomPlaylist by remember { mutableStateOf<String?>(null) }

    // ✅ NEW: State to track which playlist is pending deletion
    var playlistToDelete by remember { mutableStateOf<String?>(null) }

    // ── ✅ NEW: The "Confirm Delete" Dialog ──
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = SpotifySurface2,
            title = {
                Text(
                    "Delete Playlist?",
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete '$playlistToDelete'? This action cannot be undone.",
                    color = SpotifyGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlistToDelete!!)
                    // If we are currently viewing the playlist we just deleted, close the detail view
                    if (selectedCustomPlaylist == playlistToDelete) {
                        selectedCustomPlaylist = null
                    }
                    playlistToDelete = null
                }) {
                    Text(
                        "Delete",
                        color = Color(0xFFFF5555),
                        fontWeight = FontWeight.Bold
                    ) // Red for danger
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(
                        "Cancel",
                        color = SpotifyGray
                    )
                }
            }
        )
    }

    // ── 1. Detail View for the "Local" Protected Playlist ──
    if (showLocalDetail) {
        PlaylistDetailView(
            playlistName     = "Local",
            songs            = songs,
            currentSong      = currentSong,
            favoriteIds      = favoriteIds,
            isProtected      = true,
            onBack           = { showLocalDetail = false },
            onSongClick      = onSongClick,
            onToggleFavorite = onToggleFavorite,
            onShufflePlay    = { viewModel.playAllShuffled(songs) },
            onRemoveSong     = { /* Protected */ },
            onDeletePlaylist = { /* Protected */ }
        )
        return
    }

    // ── 2. Detail View for a selected Custom Playlist ──
    if (selectedCustomPlaylist != null) {
        val pName = selectedCustomPlaylist!!
        val pIds = customPlaylists[pName] ?: emptyList()
        // Map saved IDs back to Song objects
        val pSongs = songs.filter { pIds.contains(it.id) }

        PlaylistDetailView(
            playlistName     = pName,
            songs            = pSongs,
            currentSong      = currentSong,
            favoriteIds      = favoriteIds,
            isProtected      = false,
            onBack           = { selectedCustomPlaylist = null },
            onSongClick      = { song -> viewModel.playSong(song, pSongs) }, // Play within context of playlist
            onToggleFavorite = onToggleFavorite,
            onShufflePlay    = { viewModel.playShuffled(pSongs) },
            onRemoveSong     = { song -> viewModel.removeSongFromPlaylist(pName, song) },
            onDeletePlaylist = { playlistToDelete = pName } // ✅ TRiggers dialog instead of direct delete
        )

        return
    }

    // ── 3. Main Playlists List ──
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        item {
            Text(
                "Your Playlists",
                color      = SpotifyWhite,
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        // Local Playlist Card
        item {
            PlaylistCard(
                name        = "Local",
                songCount   = songs.size,
                isProtected = true,
                onClick     = { showLocalDetail = true },
                onDelete    = { /* Cannot delete */ }
            )
        }

        // User Custom Playlist Cards
        items(customPlaylists.entries.toList(), key = { it.key }) { (name, ids) ->
            PlaylistCard(
                name        = name,
                songCount   = ids.size,
                isProtected = false,
                onClick     = { selectedCustomPlaylist = name },
                onDelete    = { playlistToDelete = name }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Playlist Card ────────────────────────────────────────────────────
@Composable
fun PlaylistCard(
    name        : String,
    songCount   : Int,
    isProtected : Boolean,
    onClick     : () -> Unit,
    onDelete    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF158A3E)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(name, color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("$songCount song${if (songCount != 1) "s" else ""} ${if (isProtected) "· Device" else "· Custom"}", color = SpotifyGray, fontSize = 12.sp)
        }

        if (isProtected) {
            Icon(Icons.Filled.Lock, "Cannot delete", tint = SpotifyGray, modifier = Modifier.size(18.dp))
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete Playlist", tint = SpotifyGray, modifier = Modifier.size(20.dp))
            }
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
}

// ─── General Playlist Detail View ─────────────────────────────────────
@Composable
fun PlaylistDetailView(
    playlistName     : String,
    songs            : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    isProtected      : Boolean,
    onBack           : () -> Unit,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onShufflePlay    : () -> Unit,
    onRemoveSong     : (Song) -> Unit,
    onDeletePlaylist : () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1DB954), Color(0xFF0D5C2E), SpotifyBlack)))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                    }
                    if (!isProtected) {
                        IconButton(onClick = onDeletePlaylist) {
                            Icon(Icons.Filled.Delete, "Delete", tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF158A3E)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(playlistName, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("${songs.size} song${if (songs.size != 1) "s" else ""}", color = SpotifyGray, fontSize = 13.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProtected) {
                        Icon(Icons.Filled.Lock, null, tint = SpotifyGray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Device playlist · cannot be deleted", color = SpotifyGray, fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SpotifyGreen)
                        .clickable { onShufflePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shuffle, "Shuffle Play", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (songs.isEmpty()) {
            item {
                Text(
                    "This playlist is empty. Go add some songs!",
                    color = SpotifyGray,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            itemsIndexed(songs) { index, song ->
                PlaylistSongItem(
                    index            = index,
                    song             = song,
                    isCurrentSong    = song.id == currentSong?.id,
                    isFavorite       = favoriteIds.contains(song.id),
                    isProtected      = isProtected,
                    onClick          = { onSongClick(song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onRemoveSong     = { onRemoveSong(song) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Individual Song Row ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongItem(
    index            : Int,
    song             : Song,
    isCurrentSong    : Boolean,
    isFavorite       : Boolean,
    isProtected      : Boolean,
    onClick          : () -> Unit,
    onToggleFavorite : () -> Unit,
    onRemoveSong     : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { showMenu = true }
            )
            .background(if (isCurrentSong) SpotifySurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = "${index + 1}",
            color    = if (isCurrentSong) SpotifyGreen else SpotifyGray,
            fontSize = 13.sp,
            modifier = Modifier.width(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifySurface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = if (isCurrentSong) SpotifyGreen else SpotifyGray, modifier = Modifier.size(24.dp))
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(song.title, color = if (isCurrentSong) SpotifyGreen else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = SpotifyGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isFavorite) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, "More Options", tint = SpotifyGray, modifier = Modifier.size(20.dp))
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SpotifySurface2)
            ) {
                DropdownMenuItem(
                    text = { Text("Song Info", color = SpotifyWhite) },
                    onClick = { showMenu = false }
                )
                if (!isProtected) {
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist", color = Color(0xFFFF5555)) },
                        onClick = {
                            showMenu = false
                            onRemoveSong()
                        }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}