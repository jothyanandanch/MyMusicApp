package com.nandu.mymusic.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.screens.library.formatDuration
import com.nandu.mymusic.ui.theme.LikedSongsGradient
import com.nandu.mymusic.ui.theme.appleColors
import com.nandu.mymusic.viewmodel.MusicViewModel
import androidx.compose.material.icons.filled.Settings
import org.json.JSONArray
import org.json.JSONObject

// ─── Sort Component ───────────────────────────────────────────────────
enum class SortType { DEFAULT, TITLE_AZ, TITLE_ZA, ARTIST }

// ─── Playlist Export / Import (JSON) ──────────────────────────────────
// Exported by title/artist/album rather than device song IDs, so a playlist
// can be moved between devices or restored after a reinstall.
data class ImportedSongEntry(val title: String, val artist: String, val album: String?)
data class ImportedPlaylist(val name: String, val entries: List<ImportedSongEntry>)

fun buildPlaylistExportJson(name: String, songs: List<Song>): String {
    val root = JSONObject()
    root.put("format", "mymusic-playlist")
    root.put("version", 1)
    root.put("name", name)
    val arr = JSONArray()
    songs.forEach { song ->
        val obj = JSONObject()
        obj.put("title", song.title)
        obj.put("artist", song.artist)
        obj.put("album", song.album ?: JSONObject.NULL)
        obj.put("durationMs", song.duration)
        arr.put(obj)
    }
    root.put("songs", arr)
    return root.toString(2)
}

fun parsePlaylistImportJson(text: String): ImportedPlaylist? {
    return try {
        val root = JSONObject(text)
        val name = root.optString("name", "Imported Playlist")
        val arr = root.optJSONArray("songs") ?: JSONArray()
        val entries = mutableListOf<ImportedSongEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            entries.add(
                ImportedSongEntry(
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    album = if (obj.isNull("album")) null else obj.optString("album")
                )
            )
        }
        ImportedPlaylist(name, entries)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SortMenu(
    sortType: SortType,
    onSortChange: (SortType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val C = appleColors()
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = C.secondaryLabel, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(C.cardElevated)
        ) {
            DropdownMenuItem(
                text = { Text("Default Order", color = if (sortType == SortType.DEFAULT) C.accent else C.label) },
                onClick = { onSortChange(SortType.DEFAULT); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Title (A-Z)", color = if (sortType == SortType.TITLE_AZ) C.accent else C.label) },
                onClick = { onSortChange(SortType.TITLE_AZ); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Title (Z-A)", color = if (sortType == SortType.TITLE_ZA) C.accent else C.label) },
                onClick = { onSortChange(SortType.TITLE_ZA); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Artist", color = if (sortType == SortType.ARTIST) C.accent else C.label) },
                onClick = { onSortChange(SortType.ARTIST); expanded = false }
            )
        }
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
    val C = appleColors()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(LikedSongsGradient)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(name, color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("$songCount song${if (songCount != 1) "s" else ""} ${if (isProtected) "· Device" else "· Custom"}", color = C.secondaryLabel, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
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
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit,
    onDownload       : (Song) -> Unit,
    onEdit           : (Song) -> Unit = {},
    onShufflePlay    : () -> Unit,
    onPlayAll        : () -> Unit,
    onAddSong        : () -> Unit,
    onExportPlaylist : () -> Unit = {},
    onAddAllToQueue  : () -> Unit,
    onRemoveSong     : (Song) -> Unit,
    onRemoveSongs    : (List<Song>) -> Unit,
    onDeletePlaylist : () -> Unit,
    onRenamePlaylist : () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var selectedSongsToRemove by remember { mutableStateOf(setOf<Song>()) }
    val C = appleColors()

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        selectedSongsToRemove = emptySet()
    }

    val displaySongs = remember(songs, sortType) {
        when (sortType) {
            SortType.DEFAULT -> songs
            SortType.TITLE_AZ -> songs.sortedBy { it.title?.lowercase() ?: "" }
            SortType.TITLE_ZA -> songs.sortedByDescending { it.title?.lowercase() ?: "" }
            SortType.ARTIST -> songs.sortedBy { it.artist?.lowercase() ?: "" }
        }
    }


    LazyColumn(modifier = Modifier.fillMaxSize().background(C.background)) {
        item {
            // ── Apple Music style detail header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isEditMode) {
                        isEditMode = false
                        selectedSongsToRemove = emptySet()
                    } else { onBack() }
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.accent, modifier = Modifier.size(26.dp)) }

                if (isEditMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isEditMode = false; selectedSongsToRemove = emptySet() }) { Text("Cancel", color = C.label) }
                        TextButton(
                            onClick = { onRemoveSongs(selectedSongsToRemove.toList()); isEditMode = false; selectedSongsToRemove = emptySet() },
                            enabled = selectedSongsToRemove.isNotEmpty()
                        ) { Text("Remove (${selectedSongsToRemove.size})", color = if (selectedSongsToRemove.isNotEmpty()) C.destructive else C.secondaryLabel, fontWeight = FontWeight.Bold) }
                    }
                } else if (!isProtected) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onRenamePlaylist) { Icon(Icons.Filled.Edit, "Rename", tint = C.accent, modifier = Modifier.size(22.dp)) }
                        IconButton(onClick = onDeletePlaylist) { Icon(Icons.Filled.Delete, "Delete", tint = C.destructive, modifier = Modifier.size(22.dp)) }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(LikedSongsGradient)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(80.dp)) }
                Spacer(Modifier.height(16.dp))
                Text(playlistName, color = C.label, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("${songs.size} song${if (songs.size != 1) "s" else ""}", color = C.secondaryLabel, fontSize = 13.sp)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProtected) {
                        Icon(Icons.Filled.Lock, null, tint = C.secondaryLabel, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Device playlist", color = C.secondaryLabel, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isProtected) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel) }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                                DropdownMenuItem(text = { Text("Add Songs", color = C.label) }, onClick = { showMoreMenu = false; onAddSong() })
                                DropdownMenuItem(
                                    text = { Text(if (isEditMode) "Done Selecting" else "Select Songs", color = C.label) },
                                    onClick = {
                                        showMoreMenu = false
                                        isEditMode = !isEditMode
                                        if (!isEditMode) selectedSongsToRemove = emptySet()
                                    }
                                )
                                DropdownMenuItem(text = { Text("Export Playlist", color = C.label) }, onClick = { showMoreMenu = false; onExportPlaylist() })
                            }
                        }
                    }
                    IconButton(onClick = onAddAllToQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Queue", tint = C.secondaryLabel) }
                    SortMenu(sortType = sortType, onSortChange = onSortChange)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onShufflePlay() }) {
                        Icon(Icons.Filled.Shuffle, "Shuffle Play", tint = C.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    TextButton(onClick = { onPlayAll() }) {
                        Icon(Icons.Filled.PlayArrow, "Play All", tint = C.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }

        if (displaySongs.isEmpty()) {
            item { Text("This playlist is empty. Go add some songs!", color = C.secondaryLabel, modifier = Modifier.padding(32.dp)) }
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
                    onRemoveSong     = { onRemoveSong(song) },
                    onPlayNext       = { onPlayNext(song) },
                    onAddToQueue     = { onAddToQueue(song) },
                    onAddToPlaylist  = { onAddToPlaylist(song) },
                    onViewAlbum      = { onViewAlbum(song.album ?: "Unknown Album") },
                    onViewArtist     = { onViewArtist(song.artist ?: "Unknown Artist") },
                    onDownload       = { onDownload(song) },
                    onEdit           = { onEdit(song) }
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
    onRemoveSong     : () -> Unit,
    onPlayNext       : () -> Unit,
    onAddToQueue     : () -> Unit,
    onAddToPlaylist  : () -> Unit,
    onViewAlbum      : () -> Unit,
    onViewArtist     : () -> Unit,
    onDownload       : () -> Unit,
    onEdit           : () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val C = appleColors()
    val isOnline = song.uri.toString().startsWith("http")
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick() }, onLongClick = { if (!isEditMode) showMenu = true })
            .background(if (isCurrentSong && !isEditMode) C.card else if (isSelected) C.cardElevated else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            Icon(imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = "Select", tint = if (isSelected) C.accent else C.secondaryLabel, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Text(text = "${index + 1}", color = if (isCurrentSong) C.accent else C.secondaryLabel, fontSize = 13.sp, modifier = Modifier.width(24.dp))
            Spacer(Modifier.requiredWidth(8.dp))
        }

        AlbumArt(
            song = song,
            audioUri = song.uri,
            title = song.title,
            artist = song.artist,
            isActive = isCurrentSong && !isEditMode,
            size = 48.dp,
            cornerRadius = 4.dp
        )

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title ?: "Unknown Title", color = if (isCurrentSong && !isEditMode) C.accent else C.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (!isEditMode) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) C.accent else C.secondaryLabel, modifier = Modifier.size(18.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOnline) Icon(Icons.Filled.Cloud, contentDescription = "Online", tint = C.accent, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                Text(formatDuration(song.duration), color = C.secondaryLabel, fontSize = 12.sp)
            }
            Spacer(Modifier.width(4.dp))

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More Options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                    DropdownMenuItem(text = { Text("View Album", color = C.label) }, onClick = { showMenu = false; onViewAlbum() })
                    DropdownMenuItem(text = { Text("View Artist", color = C.label) }, onClick = { showMenu = false; onViewArtist() })
                    DropdownMenuItem(text = { Text("Play Next", color = C.label) }, onClick = { showMenu = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = { showMenu = false; onAddToQueue() })
                    DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = { showMenu = false; onAddToPlaylist() })
                    if (isOnline) {
                        DropdownMenuItem(text = { Text("Download Song", color = C.label) }, onClick = { showMenu = false; onDownload() })
                    } else {
                        DropdownMenuItem(text = { Text("Edit Song Info", color = C.label) }, onClick = { showMenu = false; onEdit() })
                    }
                    if (!isProtected) {
                        DropdownMenuItem(text = { Text("Remove from Playlist", color = C.destructive) }, onClick = { showMenu = false; onRemoveSong() })
                    }
                }
            }
        }
    }
    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
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
    val C = appleColors()

    Column(modifier = Modifier.fillMaxSize().background(C.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel", tint = C.label) }
            Text(text = "Add to $playlistName", color = C.label, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(onClick = { onAddSongs(selectedSongs.toList()) }, enabled = selectedSongs.isNotEmpty()) {
                Text(text = if (selectedSongs.isEmpty()) "Add" else "Add (${selectedSongs.size})", color = if (selectedSongs.isNotEmpty()) C.accent else C.secondaryLabel, fontWeight = FontWeight.Bold)
            }
        }

        if (availableSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("All your songs are already in this playlist!", color = C.secondaryLabel) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(availableSongs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song)

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song }
                            .background(if (isSelected) C.cardElevated else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(audioUri = song.uri, isActive = isSelected, size = 48.dp, cornerRadius = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title ?: "Unknown Title", color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) { Icon(Icons.Filled.CheckCircle, "Selected", tint = C.accent, modifier = Modifier.size(24.dp)) }
                        else { Icon(Icons.Outlined.Circle, "Select", tint = C.secondaryLabel, modifier = Modifier.size(24.dp)) }
                    }
                    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
                }
            }
        }
    }
}