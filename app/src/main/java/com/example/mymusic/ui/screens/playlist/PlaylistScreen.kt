package com.example.mymusic.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/**
 * Playlists tab — shows a single "Local" playlist card.
 * Tapping it opens the full playlist detail screen showing all device songs.
 *
 * Signature matches the call-site in MusicLibraryScreen:
 *   PlaylistScreen(songs, currentSong, favoriteIds, onSongClick, onToggleFavorite, viewModel)
 */
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
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        LocalPlaylistDetail(
            songs            = songs,
            currentSong      = currentSong,
            favoriteIds      = favoriteIds,
            onBack           = { showDetail = false },
            onSongClick      = onSongClick,
            onToggleFavorite = onToggleFavorite,
            onShufflePlay    = { viewModel.playAllShuffled(songs) }
        )
        return
    }

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

        // ── "Local" playlist card — cannot be deleted ──
        item {
            PlaylistCard(
                name        = "Local",
                songCount   = songs.size,
                isProtected = true,
                onClick     = { showDetail = true }
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
    onClick     : () -> Unit
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
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF158A3E)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint     = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text       = name,
                color      = SpotifyWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp
            )
            Text(
                text     = "$songCount song${if (songCount != 1) "s" else ""} · Device",
                color    = SpotifyGray,
                fontSize = 12.sp
            )
        }
        if (isProtected) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Cannot delete",
                tint     = SpotifyGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
}

// ─── Local Playlist Detail ────────────────────────────────────────────
@Composable
fun LocalPlaylistDetail(
    songs            : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    onBack           : () -> Unit,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onShufflePlay    : () -> Unit
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
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1DB954), Color(0xFF0D5C2E), SpotifyBlack)
                        )
                    )
                    //.statusBarsPadding()
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack, "Back",
                        tint = SpotifyWhite, modifier = Modifier.size(24.dp)
                    )
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
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1DB954), Color(0xFF158A3E))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LibraryMusic, null,
                            tint     = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Local",
                        color      = SpotifyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 28.sp
                    )
                    Text(
                        "${songs.size} song${if (songs.size != 1) "s" else ""} · All songs on this device",
                        color    = SpotifyGray,
                        fontSize = 13.sp
                    )
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
                    Icon(
                        Icons.Filled.Lock, null,
                        tint     = SpotifyGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Device playlist · cannot be deleted", color = SpotifyGray, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SpotifyGreen)
                        .clickable { onShufflePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Shuffle, "Shuffle Play",
                        tint     = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        itemsIndexed(songs) { index, song ->
            LocalPlaylistSongItem(
                index            = index,
                song             = song,
                isCurrentSong    = song.id == currentSong?.id,
                isFavorite       = favoriteIds.contains(song.id),
                onClick          = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Individual song row inside Local playlist ────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPlaylistSongItem(
    index            : Int,
    song             : Song,
    isCurrentSong    : Boolean,
    isFavorite       : Boolean,
    onClick          : () -> Unit,
    onToggleFavorite : () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            containerColor   = SpotifySurface2,
            title = {
                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(song.artist, color = SpotifyGray, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = { showTooltip = false }) {
                    Text("Close", color = SpotifyGreen)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { showTooltip = true }
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
            Icon(
                Icons.Filled.MusicNote, null,
                tint     = if (isCurrentSong) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text       = song.title,
                color      = if (isCurrentSong) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = song.artist,
                color    = SpotifyGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick  = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector        = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Unlike" else "Like",
                tint               = if (isFavorite) SpotifyGreen else SpotifyGray,
                modifier           = Modifier.size(18.dp)
            )
        }
        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}
