package com.nandu.mymusic.ui.screens.playlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
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
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.screens.library.SpotifyBlack
import com.nandu.mymusic.ui.screens.library.SpotifyGray
import com.nandu.mymusic.ui.screens.library.SpotifyGreen
import com.nandu.mymusic.ui.screens.library.SpotifySurface
import com.nandu.mymusic.ui.screens.library.SpotifySurface2
import com.nandu.mymusic.ui.screens.library.SpotifyWhite
import com.nandu.mymusic.ui.screens.library.formatDuration
import com.nandu.mymusic.viewmodel.MusicViewModel

// ─── Sort Component ───────────────────────────────────────────────────
enum class SortType { DEFAULT, TITLE_AZ, TITLE_ZA, ARTIST }

@Composable
fun SortMenu(
    sortType: SortType,
    onSortChange: (SortType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = SpotifyGray, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SpotifySurface2)
        ) {
            DropdownMenuItem(
                text = { Text("Default Order", color = if (sortType == SortType.DEFAULT) SpotifyGreen else SpotifyWhite) },
                onClick = { onSortChange(SortType.DEFAULT); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Title (A-Z)", color = if (sortType == SortType.TITLE_AZ) SpotifyGreen else SpotifyWhite) },
                onClick = { onSortChange(SortType.TITLE_AZ); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Title (Z-A)", color = if (sortType == SortType.TITLE_ZA) SpotifyGreen else SpotifyWhite) },
                onClick = { onSortChange(SortType.TITLE_ZA); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Artist", color = if (sortType == SortType.ARTIST) SpotifyGreen else SpotifyWhite) },
                onClick = { onSortChange(SortType.ARTIST); expanded = false }
            )
        }
    }
}

@Composable
fun PlaylistScreen(
    songs            : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    sortType         : SortType,
    onSortChange     : (SortType) -> Unit,
    showSnackbar     : (String) -> Unit,
    modifier         : Modifier = Modifier,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    viewModel        : MusicViewModel
) {
    val customPlaylists by viewModel.customPlaylists.observeAsState(initial = emptyMap())

    var showLocalDetail by remember { mutableStateOf(false) }
    var selectedCustomPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistToDelete by remember { mutableStateOf<String?>(null) }

    // Rename state
    var playlistToRename by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Create state
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameToCreate by remember { mutableStateOf("") }

    // Add Songs state
    var playlistToAddSongsTo by remember { mutableStateOf<String?>(null) }

    // Intercept System Back Button
    BackHandler(enabled = showLocalDetail || selectedCustomPlaylist != null || playlistToAddSongsTo != null) {
        if (playlistToAddSongsTo != null) {
            playlistToAddSongsTo = null
        } else if (showLocalDetail) {
            showLocalDetail = false
        } else if (selectedCustomPlaylist != null) {
            selectedCustomPlaylist = null
        }
    }

    // ── The "Create Playlist" Dialog ──
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SpotifySurface2,
            title = { Text("Create New Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = playlistNameToCreate,
                    onValueChange = { playlistNameToCreate = it },
                    singleLine = true,
                    label = { Text("Playlist name", color = SpotifyGray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = SpotifyWhite)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistNameToCreate.isNotBlank()) {
                        viewModel.createPlaylist(playlistNameToCreate)
                        showSnackbar("Playlist created: $playlistNameToCreate")
                        playlistNameToCreate = ""
                        showCreateDialog = false
                    }
                }) { Text("Create", color = SpotifyGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = SpotifyGray) } }
        )
    }

    // ── The "Confirm Delete" Dialog ──
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = SpotifySurface2,
            title = { Text("Delete Playlist?", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '$playlistToDelete'? This action cannot be undone.", color = SpotifyGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlistToDelete!!)
                    if (selectedCustomPlaylist == playlistToDelete) selectedCustomPlaylist = null
                    showSnackbar("Playlist deleted")
                    playlistToDelete = null
                }) { Text("Delete", color = Color(0xFFFF5555), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel", color = SpotifyGray) } }
        )
    }

    // ── The "Rename Playlist" Dialog ──
    if (playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            containerColor = SpotifySurface2,
            title = { Text("Rename Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = SpotifyWhite)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank() && newPlaylistName != playlistToRename) {
                        viewModel.renamePlaylist(playlistToRename!!, newPlaylistName)
                        if (selectedCustomPlaylist == playlistToRename) selectedCustomPlaylist = newPlaylistName
                        showSnackbar("Playlist renamed to $newPlaylistName")
                    }
                    playlistToRename = null
                    newPlaylistName = ""
                }) { Text("Save", color = SpotifyGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { playlistToRename = null }) { Text("Cancel", color = SpotifyGray) } }
        )
    }

    // ── 0. Overlay: Add Songs Screen ──
    if (playlistToAddSongsTo != null) {
        val pName = playlistToAddSongsTo!!
        val existingIds = customPlaylists[pName] ?: emptyList()
        val availableSongs = songs.filter { !existingIds.contains(it.id) }

        AddSongsScreen(
            playlistName = pName,
            availableSongs = availableSongs,
            onDismiss = { playlistToAddSongsTo = null },
            onAddSongs = { selectedSongs ->
                viewModel.addSongsToPlaylist(pName, selectedSongs)
                showSnackbar("Added ${selectedSongs.size} songs to $pName")
                playlistToAddSongsTo = null
            }
        )
        return
    }

    // ── 1. Detail View for Local ──
    if (showLocalDetail) {
        PlaylistDetailView(
            playlistName     = "Local",
            songs            = songs,
            currentSong      = currentSong,
            favoriteIds      = favoriteIds,
            sortType         = sortType,
            onSortChange     = onSortChange,
            showSnackbar     = showSnackbar,
            isProtected      = true,
            onBack           = { showLocalDetail = false },
            onSongClick      = onSongClick,
            onToggleFavorite = onToggleFavorite,
            onShufflePlay    = { viewModel.playAllShuffled(songs) },
            onPlayAll        = { if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs) },
            onAddSong        = { },
            onAddToQueue     = {
                if (songs.isNotEmpty()) {
                    viewModel.addListToQueue(songs)
                    showSnackbar("Added ${songs.size} songs to queue")
                }
            },
            onRemoveSong     = { },
            onRemoveSongs    = { },
            onDeletePlaylist = { },
            onRenamePlaylist = { }
        )
        return
    }

    // ── 2. Detail View for Custom Playlists ──
    if (selectedCustomPlaylist != null) {
        val pName = selectedCustomPlaylist!!
        val pIds = customPlaylists[pName] ?: emptyList()
        val pSongs = songs.filter { pIds.contains(it.id) }

        PlaylistDetailView(
            playlistName     = pName,
            songs            = pSongs,
            currentSong      = currentSong,
            favoriteIds      = favoriteIds,
            sortType         = sortType,
            onSortChange     = onSortChange,
            showSnackbar     = showSnackbar,
            isProtected      = false,
            onBack           = { selectedCustomPlaylist = null },
            onSongClick      = { song -> viewModel.playSong(song, pSongs) },
            onToggleFavorite = onToggleFavorite,
            onShufflePlay    = { viewModel.playShuffled(pSongs) },
            onPlayAll        = { if (pSongs.isNotEmpty()) viewModel.playSong(pSongs.first(), pSongs) },
            onAddSong        = { playlistToAddSongsTo = pName },
            onAddToQueue     = {
                if (pSongs.isNotEmpty()) {
                    viewModel.addListToQueue(pSongs)
                    showSnackbar("Added ${pSongs.size} songs to queue")
                }
            },
            onRemoveSong     = { song -> viewModel.removeSongFromPlaylist(pName, song); showSnackbar("Removed 1 song") },
            onRemoveSongs    = { list -> list.forEach { viewModel.removeSongFromPlaylist(pName, it) }; showSnackbar("Removed ${list.size} songs") },
            onDeletePlaylist = { playlistToDelete = pName },
            onRenamePlaylist = {
                newPlaylistName = pName
                playlistToRename = pName
            }
        )
        return
    }

    // ── 3. Main Playlists List ──
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Playlists", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, "Create Playlist", tint = SpotifyWhite)
                }
            }
        }

        item { PlaylistCard(name = "Local", songCount = songs.size, isProtected = true, onClick = { showLocalDetail = true }) }

        items(customPlaylists.entries.toList(), key = { it.key }) { (name, ids) ->
            val pSongs = songs.filter { ids.contains(it.id) }
            PlaylistCard(name = name, songCount = ids.size, isProtected = false, onClick = { selectedCustomPlaylist = name })
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
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF158A3E)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(name, color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("$songCount song${if (songCount != 1) "s" else ""} ${if (isProtected) "· Device" else "· Custom"}", color = SpotifyGray, fontSize = 12.sp)
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
    sortType         : SortType,
    onSortChange     : (SortType) -> Unit,
    showSnackbar     : (String) -> Unit,
    isProtected      : Boolean,
    onBack           : () -> Unit,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onShufflePlay    : () -> Unit,
    onPlayAll        : () -> Unit,
    onAddSong        : () -> Unit,
    onAddToQueue     : () -> Unit,
    onRemoveSong     : (Song) -> Unit,
    onRemoveSongs    : (List<Song>) -> Unit,
    onDeletePlaylist : () -> Unit,
    onRenamePlaylist : () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var selectedSongsToRemove by remember { mutableStateOf(setOf<Song>()) }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        selectedSongsToRemove = emptySet()
    }

    val displaySongs = remember(songs, sortType) {
        when (sortType) {
            SortType.DEFAULT -> songs
            SortType.TITLE_AZ -> songs.sortedBy { it.title.lowercase() }
            SortType.TITLE_ZA -> songs.sortedByDescending { it.title.lowercase() }
            SortType.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1DB954), Color(0xFF0D5C2E), SpotifyBlack)))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (isEditMode) {
                            isEditMode = false
                            selectedSongsToRemove = emptySet()
                        } else { onBack() }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }

                    if (isEditMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { isEditMode = false; selectedSongsToRemove = emptySet() }) { Text("Cancel", color = SpotifyWhite) }
                            TextButton(
                                onClick = { onRemoveSongs(selectedSongsToRemove.toList()); isEditMode = false; selectedSongsToRemove = emptySet() },
                                enabled = selectedSongsToRemove.isNotEmpty()
                            ) { Text("Delete (${selectedSongsToRemove.size})", color = if (selectedSongsToRemove.isNotEmpty()) Color(0xFFFF5555) else SpotifyGray, fontWeight = FontWeight.Bold) }
                        }
                    } else if (!isProtected) {
                        Row {
                            IconButton(onClick = onRenamePlaylist) { Icon(Icons.Filled.Edit, "Rename", tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }
                            IconButton(onClick = onDeletePlaylist) { Icon(Icons.Filled.Delete, "Delete", tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Box(
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(4.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF158A3E)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(playlistName, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("${songs.size} song${if (songs.size != 1) "s" else ""}", color = SpotifyGray, fontSize = 13.sp)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProtected) {
                        Icon(Icons.Filled.Lock, null, tint = SpotifyGray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Device playlist", color = SpotifyGray, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isProtected) {
                        IconButton(onClick = onAddSong) { Icon(Icons.Filled.AddCircleOutline, "Add Songs", tint = SpotifyGray) }
                        IconButton(onClick = { isEditMode = !isEditMode; if (!isEditMode) selectedSongsToRemove = emptySet() }) {
                            Icon(Icons.Filled.Checklist, "Select Songs", tint = if (isEditMode) SpotifyGreen else SpotifyGray)
                        }
                    }
                    IconButton(onClick = onAddToQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Queue", tint = SpotifyGray) }
                    SortMenu(sortType = sortType, onSortChange = onSortChange)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(SpotifyGreen).clickable { onShufflePlay() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Shuffle, "Shuffle Play", tint = Color.Black, modifier = Modifier.size(28.dp)) }
                }
            }
        }

        if (displaySongs.isEmpty()) {
            item { Text("This playlist is empty. Go add some songs!", color = SpotifyGray, modifier = Modifier.padding(32.dp)) }
        } else {
            itemsIndexed(displaySongs) { index, song ->
                val isSelected = selectedSongsToRemove.contains(song)
                PlaylistSongItem(
                    index            = index,
                    song             = song,
                    isCurrentSong    = song.id == currentSong?.id,
                    isFavorite       = favoriteIds.contains(song.id),
                    isProtected      = isProtected,
                    isEditMode       = isEditMode,
                    isSelected       = isSelected,
                    onClick          = {
                        if (isEditMode) {
                            selectedSongsToRemove = if (isSelected) selectedSongsToRemove - song else selectedSongsToRemove + song
                        } else { onSongClick(song) }
                    },
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
    isEditMode       : Boolean = false,
    isSelected       : Boolean = false,
    onClick          : () -> Unit,
    onToggleFavorite : () -> Unit,
    onRemoveSong     : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick() }, onLongClick = { if (!isEditMode) showMenu = true })
            .background(if (isCurrentSong && !isEditMode) SpotifySurface else if (isSelected) SpotifySurface2 else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            Icon(imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = "Select", tint = if (isSelected) SpotifyGreen else SpotifyGray, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Text(text = "${index + 1}", color = if (isCurrentSong) SpotifyGreen else SpotifyGray, fontSize = 13.sp, modifier = Modifier.width(24.dp))
            Spacer(Modifier.width(8.dp))
        }

        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(SpotifySurface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = if (isCurrentSong) SpotifyGreen else SpotifyGray, modifier = Modifier.size(24.dp))
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, color = if (isCurrentSong && !isEditMode) SpotifyGreen else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = SpotifyGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (!isEditMode) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) SpotifyGreen else SpotifyGray, modifier = Modifier.size(18.dp))
            }

            Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More Options", tint = SpotifyGray, modifier = Modifier.size(20.dp)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(SpotifySurface2)) {
                    DropdownMenuItem(text = { Text("Song Info", color = SpotifyWhite) }, onClick = { showMenu = false })
                    if (!isProtected) {
                        DropdownMenuItem(text = { Text("Remove from Playlist", color = Color(0xFFFF5555)) }, onClick = { showMenu = false; onRemoveSong() })
                    }
                }
            }
        }
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}

// ─── Add Songs Multi-Select Screen Overlay ────────────────────────────
@Composable
fun AddSongsScreen(
    playlistName: String,
    availableSongs: List<Song>,
    onDismiss: () -> Unit,
    onAddSongs: (List<Song>) -> Unit
) {
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }

    Column(modifier = Modifier.fillMaxSize().background(SpotifyBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel", tint = SpotifyWhite) }
            Text(text = "Add to $playlistName", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(onClick = { onAddSongs(selectedSongs.toList()) }, enabled = selectedSongs.isNotEmpty()) {
                Text(text = if (selectedSongs.isEmpty()) "Add" else "Add (${selectedSongs.size})", color = if (selectedSongs.isNotEmpty()) SpotifyGreen else SpotifyGray, fontWeight = FontWeight.Bold)
            }
        }

        if (availableSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("All your songs are already in this playlist!", color = SpotifyGray) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(availableSongs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song)

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song }
                            .background(if (isSelected) SpotifySurface2 else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(audioUri = song.uri, isActive = isSelected, size = 48.dp, cornerRadius = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = song.artist, color = SpotifyGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) { Icon(Icons.Filled.CheckCircle, "Selected", tint = SpotifyGreen, modifier = Modifier.size(24.dp)) }
                        else { Icon(Icons.Outlined.Circle, "Select", tint = SpotifyGray, modifier = Modifier.size(24.dp)) }
                    }
                    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
                }
            }
        }
    }
}