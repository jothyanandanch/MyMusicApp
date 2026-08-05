package com.nandu.mymusic.ui.screens.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.snackbar.Snackbar
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.screens.library.LibraryGroupItem
import com.nandu.mymusic.ui.screens.library.SongItem
import com.nandu.mymusic.ui.screens.library.SpotifyWhite
import com.nandu.mymusic.ui.screens.library.downloadSong
import com.nandu.mymusic.ui.screens.library.splitArtists
import java.util.LinkedHashMap

@Composable
fun SearchScreen(
    searchQuery      : String,
    filteredSongs    : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    showSnackbar: (String) -> Unit,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onAddSongsToPlaylist: (List<Song>) -> Unit = {},
    onAddListToQueue: (List<Song>) -> Unit = {},
    onAddShuffledToQueue: (List<Song>) -> Unit = {},
    onDelete         : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit,
    onDownload: (Song) -> Unit
) {
    val context = LocalContext.current

    // Dynamically extract matching artists based on the search query
    val matchingArtists = remember(filteredSongs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val queryLower = searchQuery.lowercase()
            val artistMap = LinkedHashMap<String, MutableList<Song>>()
            for (song in filteredSongs) {
                val artistName = song.artist ?: "Unknown Artist"
                for (artist in splitArtists(artistName)) {
                    if (artist.lowercase().contains(queryLower)) {
                        artistMap.getOrPut(artist) { mutableListOf() }.add(song)
                    }
                }
            }
            artistMap.entries.toList()
        }
    }

    // Dynamically extract matching albums based on the search query
    val matchingAlbums = remember(filteredSongs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else filteredSongs
            .filter { it.album != null && it.album.lowercase().contains(searchQuery.lowercase()) }
            .groupBy { it.album!! }
            .entries.toList()
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {

        // 1. SHOW MATCHING ALBUMS AT THE TOP
        if (matchingAlbums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(matchingAlbums) { (albumName, albumSongs) ->
                val firstSong = albumSongs.firstOrNull()
                LibraryGroupItem(
                    title = albumName,
                    subtitle = "Album • ${firstSong?.artist ?: "Unknown Artist"}",
                    audioUri = firstSong?.uri,
                    song = firstSong,
                    onClick = { onViewAlbum(albumName) },
                    isCircular = false,
                    songs = albumSongs,
                    onAddToPlaylist = onAddSongsToPlaylist,
                    onAddToQueue = onAddListToQueue,
                    onAddShuffledToQueue = onAddShuffledToQueue
                )
            }
        }

        // 2. SHOW MATCHING ARTISTS
        if (matchingArtists.isNotEmpty()) {
            item {
                Text(
                    text = "Artists",
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(matchingArtists) { (artistName, artistSongs) ->
                val firstSong = artistSongs.firstOrNull()
                LibraryGroupItem(
                    title = artistName,
                    subtitle = "Artist • ${artistSongs.size} songs",
                    audioUri = firstSong?.uri,
                    song = firstSong,
                    onClick = { onViewArtist(artistName) },
                    isCircular = true,
                    songs = artistSongs,
                    onAddToPlaylist = onAddSongsToPlaylist,
                    onAddToQueue = onAddListToQueue,
                    onAddShuffledToQueue = onAddShuffledToQueue
                )
            }
        }

        // 3. SHOW MATCHING SONGS BELOW
        if (filteredSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Songs",
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(filteredSongs, key = { it.id }) { song ->
                SongItem(
                    song             = song,
                    currentSong      = currentSong,
                    isFavorite       = favoriteIds.contains(song.id),
                    onSongClick      = { onSongClick(song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onPlayNext       = { onPlayNext(song) },
                    onAddToQueue     = { onAddToQueue(song) },
                    onAddToPlaylist  = { onAddToPlaylist(song) },
                    onDelete         = { onDelete(song) },
                    onEdit           = {showSnackbar("Edit functionality coming soon!")},
                    onDownload = { onDownload(song) }  ,
                    onViewAlbum      = { onViewAlbum(song.album ?: "Unknown Album") },
                    onViewArtist     = { onViewArtist(song.artist ?: "Unknown Artist") }
                )
            }
        }
    }
}
