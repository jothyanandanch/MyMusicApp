package com.nandu.mymusic.ui.screens.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
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
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.screens.library.SpotifyBlack
import com.nandu.mymusic.ui.screens.library.SpotifyGray
import com.nandu.mymusic.ui.screens.library.SpotifyGreen
import com.nandu.mymusic.ui.screens.library.SpotifySurface
import com.nandu.mymusic.ui.screens.library.SpotifySurface2
import com.nandu.mymusic.ui.screens.library.SpotifyWhite
import com.nandu.mymusic.ui.screens.playlist.AddSongsScreen
import com.nandu.mymusic.ui.screens.playlist.SortMenu
import com.nandu.mymusic.ui.screens.playlist.SortType

@Composable
fun FavoritesScreen(
    allSongs         : List<Song>, // Needed to see what songs can be added
    favorites        : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    sortType         : SortType,
    onSortChange     : (SortType) -> Unit,
    modifier         : Modifier = Modifier,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onAddSongs       : (List<Song>) -> Unit, // Bulk Add Callback
    onRemoveSongs    : (List<Song>) -> Unit,  // Bulk Remove Callback
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit,
    onDownload       : (Song) -> Unit,
    onShufflePlay    : () -> Unit = {},
    onEdit           : (Song) -> Unit = {}
) {
    // ── State for Menu & Bulk Actions ──
    var showMenu by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedSongsToRemove by remember { mutableStateOf(setOf<Song>()) }
    var showAddSongsOverlay by remember { mutableStateOf(false) }

    // ── Back Button Handling ──
    BackHandler(enabled = isEditMode || showAddSongsOverlay) {
        if (showAddSongsOverlay) {
            showAddSongsOverlay = false
        } else if (isEditMode) {
            isEditMode = false
            selectedSongsToRemove = emptySet()
        }
    }

    // ── Sorting Logic ──
    val displayFavorites = remember(favorites, sortType) {
        when (sortType) {
            SortType.DEFAULT -> favorites
            SortType.TITLE_AZ -> favorites.sortedBy { it.title.lowercase() }
            SortType.TITLE_ZA -> favorites.sortedByDescending { it.title.lowercase() }
            SortType.ARTIST -> favorites.sortedBy { it.artist.lowercase() }
        }
    }

    // ── 1. The "Add Songs" Overlay ──
    if (showAddSongsOverlay) {
        // Filter out songs that are already in Liked Songs
        val availableSongs = allSongs.filter { !favoriteIds.contains(it.id) }

        AddSongsScreen(
            playlistName = "Liked Songs",
            availableSongs = availableSongs,
            onDismiss = { showAddSongsOverlay = false },
            onAddSongs = { selectedSongs ->
                onAddSongs(selectedSongs)
                showAddSongsOverlay = false
            }
        )
        return // Prevents drawing the main screen underneath
    }

    // ── 2. The Main Favorites Screen ──
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // ── Spotify "Liked Songs" gradient header ──
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF4B2D8C), Color(0xFF1A1A2E), SpotifyBlack)
                        )
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF7B5EA7), Color(0xFF4B2D8C))),
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint     = SpotifyWhite,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Liked Songs", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text(
                        "${favorites.size} song${if (favorites.size != 1) "s" else ""}",
                        color = SpotifyGray, fontSize = 13.sp
                    )
                }
            }
        }

        // ── Action row: Shuffle play button & Edit options ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditMode) {
                    // Show Bulk Delete Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isEditMode = false; selectedSongsToRemove = emptySet() }) {
                            Text("Cancel", color = SpotifyWhite)
                        }
                        TextButton(
                            onClick = {
                                onRemoveSongs(selectedSongsToRemove.toList())
                                isEditMode = false
                                selectedSongsToRemove = emptySet()
                            },
                            enabled = selectedSongsToRemove.isNotEmpty()
                        ) {
                            Text(
                                text = "Remove (${selectedSongsToRemove.size})",
                                color = if (selectedSongsToRemove.isNotEmpty()) Color(0xFFFF5555) else SpotifyGray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Show Standard Actions
                    SortMenu(sortType = sortType, onSortChange = onSortChange)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SpotifySurface2)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add Songs", color = SpotifyWhite) },
                                    onClick = { showMenu = false; showAddSongsOverlay = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove Songs", color = SpotifyWhite) },
                                    onClick = { showMenu = false; isEditMode = true }
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SpotifyGreen)
                                .clickable {
                                    if (favorites.isNotEmpty()) onSongClick(favorites.first())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Play Liked Songs",
                                tint = SpotifyBlack, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SpotifyGreen)
                                .clickable {
                                    if (favorites.isNotEmpty()) onShufflePlay()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Shuffle, "Shuffle Liked Songs",
                                tint = SpotifyBlack, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        if (displayFavorites.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null,
                        tint = SpotifyGray, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Songs you like will appear here",
                        color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Save songs by tapping the heart icon.",
                        color = SpotifyGray, fontSize = 13.sp)
                }
            }
        } else {
            itemsIndexed(displayFavorites) { index, song ->
                var showSongMenu by remember { mutableStateOf(false) }
                val isSelected = selectedSongsToRemove.contains(song)
                val isOnline = song.uri.toString().startsWith("http")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isEditMode) {
                                selectedSongsToRemove = if (isSelected) selectedSongsToRemove - song else selectedSongsToRemove + song
                            } else {
                                onSongClick(song)
                            }
                        }
                        .background(
                            if (isEditMode && isSelected) SpotifySurface2
                            else if (song.id == currentSong?.id && !isEditMode) SpotifySurface
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = "Select",
                            tint = if (isSelected) SpotifyGreen else SpotifyGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        // Track number
                        Text(
                            text     = "${index + 1}",
                            color    = if (song.id == currentSong?.id) SpotifyGreen else SpotifyGray,
                            fontSize = 13.sp,
                            modifier = Modifier.requiredWidth(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // ✅ Actual artwork loader rendering correctly now
                    AlbumArt(
                        song = song,
                        audioUri = song.uri,
                        isActive = song.id == currentSong?.id && !isEditMode,
                        size = 48.dp,
                        cornerRadius = 4.dp
                    )

                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text       = song.title,
                            color      = if (song.id == currentSong?.id && !isEditMode) SpotifyGreen else SpotifyWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(song.artist, color = SpotifyGray, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    if (!isEditMode) {
                        IconButton(
                            onClick  = { onToggleFavorite(song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Favorite,
                                contentDescription = "Remove from Liked Songs",
                                tint               = SpotifyGreen,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOnline) Icon(Icons.Filled.Cloud, contentDescription = "Online", tint = SpotifyGreen, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                            Text(com.nandu.mymusic.ui.screens.library.formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(
                                onClick = { showSongMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.MoreVert, "More Options", tint = SpotifyGray, modifier = Modifier.size(20.dp))
                            }

                            DropdownMenu(
                                expanded = showSongMenu,
                                onDismissRequest = { showSongMenu = false },
                                modifier = Modifier.background(SpotifySurface2)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Album", color = SpotifyWhite) },
                                    onClick = { showSongMenu = false; onViewAlbum(song.album ?: "Unknown Album") }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Artist", color = SpotifyWhite) },
                                    onClick = { showSongMenu = false; onViewArtist(song.artist) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Play Next", color = SpotifyWhite) },
                                    onClick = { showSongMenu = false; onPlayNext(song) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue", color = SpotifyWhite) },
                                    onClick = { showSongMenu = false; onAddToQueue(song) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = SpotifyWhite) },
                                    onClick = { showSongMenu = false; onAddToPlaylist(song) }
                                )

                                // Download/Edit logic based on offline vs online
                                if (isOnline) {
                                    DropdownMenuItem(
                                        text = { Text("Download Song", color = SpotifyWhite) },
                                        onClick = { showSongMenu = false; onDownload(song) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Edit Song Info", color = SpotifyWhite) },
                                        onClick = { showSongMenu = false; onEdit(song) }
                                    )
                                }

                                // Specific to Liked Songs
                                DropdownMenuItem(
                                    text = { Text("Remove from Liked Songs", color = Color(0xFFFF5555)) },
                                    onClick = { showSongMenu = false; onToggleFavorite(song) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}