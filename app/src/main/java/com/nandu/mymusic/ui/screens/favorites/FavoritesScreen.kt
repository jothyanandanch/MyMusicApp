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
import com.nandu.mymusic.ui.theme.LikedSongsGradient
import com.nandu.mymusic.ui.theme.appleColors
import com.nandu.mymusic.ui.screens.playlist.AddSongsScreen
import com.nandu.mymusic.ui.screens.playlist.SortMenu
import com.nandu.mymusic.ui.screens.playlist.SortType

@Composable
fun LikedSongsView(
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
    val C = appleColors()
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
            SortType.TITLE_AZ -> favorites.sortedBy { it.title?.lowercase() ?: "" }
            SortType.TITLE_ZA -> favorites.sortedByDescending { it.title?.lowercase() ?: "" }
            SortType.ARTIST -> favorites.sortedBy { it.artist?.lowercase() ?: "" }
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
            .background(C.background)
    ) {
        // ── Apple Music "Liked Songs" header ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.background)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.linearGradient(LikedSongsGradient),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Liked Songs", color = C.label, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(
                        "${favorites.size} song${if (favorites.size != 1) "s" else ""}",
                        color = C.secondaryLabel, fontSize = 13.sp
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
                            Text("Cancel", color = C.label)
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
                                color = if (selectedSongsToRemove.isNotEmpty()) C.destructive else C.secondaryLabel,
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
                                Icon(Icons.Filled.MoreVert, null, tint = C.secondaryLabel)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(C.cardElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add Songs", color = C.label) },
                                    onClick = { showMenu = false; showAddSongsOverlay = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove Songs", color = C.label) },
                                    onClick = { showMenu = false; isEditMode = true }
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (favorites.isNotEmpty()) onSongClick(favorites.first())
                        }) {
                            Icon(Icons.Filled.PlayArrow, "Play Liked Songs",
                                tint = C.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        TextButton(onClick = {
                            if (favorites.isNotEmpty()) onShufflePlay()
                        }) {
                            Icon(Icons.Filled.Shuffle, "Shuffle Liked Songs",
                                tint = C.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                        tint = C.secondaryLabel, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Songs you like will appear here",
                        color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Save songs by tapping the heart icon.",
                        color = C.secondaryLabel, fontSize = 13.sp)
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
                            if (isEditMode && isSelected) C.cardElevated
                            else if (song.id == currentSong?.id && !isEditMode) C.card
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = "Select",
                            tint = if (isSelected) C.accent else C.secondaryLabel,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        // Track number
                        Text(
                            text     = "${index + 1}",
                            color    = if (song.id == currentSong?.id) C.accent else C.secondaryLabel,
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
                            text       = song.title ?: "Unknown Title",
                            color      = if (song.id == currentSong?.id && !isEditMode) C.accent else C.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 12.sp,
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
                                tint               = C.accent,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOnline) Icon(Icons.Filled.Cloud, contentDescription = "Online", tint = C.accent, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                            Text(com.nandu.mymusic.ui.screens.library.formatDuration(song.duration), color = C.secondaryLabel, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(
                                onClick = { showSongMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.MoreVert, "More Options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp))
                            }

                            DropdownMenu(
                                expanded = showSongMenu,
                                onDismissRequest = { showSongMenu = false },
                                modifier = Modifier.background(C.cardElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Album", color = C.label) },
                                    onClick = { showSongMenu = false; onViewAlbum(song.album ?: "Unknown Album") }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Artist", color = C.label) },
                                    onClick = { showSongMenu = false; onViewArtist(song.artist ?: "Unknown Artist") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Play Next", color = C.label) },
                                    onClick = { showSongMenu = false; onPlayNext(song) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue", color = C.label) },
                                    onClick = { showSongMenu = false; onAddToQueue(song) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = C.label) },
                                    onClick = { showSongMenu = false; onAddToPlaylist(song) }
                                )

                                // Download/Edit logic based on offline vs online
                                if (isOnline) {
                                    DropdownMenuItem(
                                        text = { Text("Download Song", color = C.label) },
                                        onClick = { showSongMenu = false; onDownload(song) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Edit Song Info", color = C.label) },
                                        onClick = { showSongMenu = false; onEdit(song) }
                                    )
                                }

                                // Specific to Liked Songs
                                DropdownMenuItem(
                                    text = { Text("Remove from Liked Songs", color = C.destructive) },
                                    onClick = { showSongMenu = false; onToggleFavorite(song) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = C.separator, thickness = 0.5.dp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}