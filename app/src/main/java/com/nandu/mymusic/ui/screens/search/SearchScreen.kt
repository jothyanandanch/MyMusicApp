package com.nandu.mymusic.ui.screens.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.screens.library.LibraryGroupItem
import com.nandu.mymusic.ui.screens.library.SongItem
import com.nandu.mymusic.ui.screens.library.SpotifyWhite

@Composable
fun SearchScreen(
    searchQuery      : String,
    filteredSongs    : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onDelete         : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit
) {
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
                    onClick = { onViewAlbum(albumName) }, // Takes the user to the Album View!
                    isCircular = false
                )
            }
        }

        // 2. SHOW MATCHING SONGS BELOW
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
                    onViewAlbum      = { onViewAlbum(song.album ?: "Unknown Album") },
                    onViewArtist     = { onViewArtist(song.artist) }
                )
            }
        }
    }
}