package com.nandu.mymusic.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.screens.playlist.SortMenu
import com.nandu.mymusic.ui.screens.playlist.SortType
import com.nandu.mymusic.ui.theme.appleColors
import com.nandu.mymusic.viewmodel.MusicViewModel

/**
 * The pushed "collection" detail view — shared by the Album and Artist views
 * (and the Liked Songs main list). Shows a back header, a sort menu, the
 * Play/Shuffle actions, and the song rows. Filtering by album/artist happens in
 * the parent; this view renders whatever list it is handed.
 */
@Composable
fun CollectionDetailView(
    title: String, songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    sortType: SortType, onSortChange: (SortType) -> Unit, showSnackbar: (String) -> Unit,
    onBack: () -> Unit, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit,
    onShufflePlay: () -> Unit, onPlayAll: () -> Unit, onAddToPlaylist: (Song) -> Unit,
    onAddAllToPlaylist: (List<Song>) -> Unit = {},
    onViewAlbum: (String) -> Unit, onViewArtist: (String) -> Unit, viewModel: MusicViewModel,
    onDownload: (Song) -> Unit
) {
    val C = appleColors()
    var showCollectionMenu by remember { mutableStateOf(false) }
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
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.label) }
                Spacer(Modifier.width(8.dp))
                Text(title, color = C.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(sortType = sortType, onSortChange = onSortChange)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { showCollectionMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel)
                        }
                        DropdownMenu(expanded = showCollectionMenu, onDismissRequest = { showCollectionMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                            DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) onAddAllToPlaylist(songs)
                            })
                            DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) {
                                    viewModel.addListToQueue(songs)
                                    showSnackbar("Added ${songs.size} songs to queue")
                                }
                            })
                            DropdownMenuItem(text = { Text("Add to Queue (Shuffled)", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) {
                                    viewModel.addListToQueue(songs.shuffled())
                                    showSnackbar("Added ${songs.size} shuffled songs to queue")
                                }
                            })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onPlayAll) {
                        Icon(Icons.Filled.PlayArrow, "Play All", tint = C.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onShufflePlay) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = C.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
        items(displaySongs, key = { it.id }) { song ->
            SongItem(
                song = song, currentSong = currentSong, isFavorite = favoriteIds.contains(song.id),
                onSongClick = { onSongClick(song) }, onToggleFavorite = { onToggleFavorite(song) },
                onPlayNext = { viewModel.setPlayNext(song); showSnackbar("Will play next") },
                onAddToQueue = { viewModel.addToQueue(song); showSnackbar("Added to queue") },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onDelete = { viewModel.deleteSong(song); },
                onEdit = { showSnackbar("Edit functionality coming soon!") },
                onDownload = { onDownload(song) },
                onViewAlbum = { onViewAlbum(song.album ?: "Unknown Album") }, onViewArtist = { onViewArtist(song.artist ?: "Unknown Artist") }
            )
        }
    }
}
