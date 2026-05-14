package com.example.mymusic.ui.screens.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.mymusic.model.Song
import com.example.mymusic.ui.screens.library.SongItem

@Composable
fun SearchScreen(
    filteredSongs    : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onPlayNext       : (Song) -> Unit, // ✅ NEW
    onAddToQueue     : (Song) -> Unit,  // ✅ NEW
    onAddToPlaylist  : (Song) -> Unit  // ✅ ADD THIS
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        items(filteredSongs, key = { it.id }) { song ->
            SongItem(
                song             = song,
                currentSong      = currentSong,
                isFavorite       = favoriteIds.contains(song.id),
                onSongClick      = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) },
                onPlayNext       = { onPlayNext(song) },    // ✅ Pass down
                onAddToQueue     = { onAddToQueue(song) },   // ✅ Pass down
                onAddToPlaylist  = { onAddToPlaylist(song) } // ✅ ADD THIS
            )
        }
    }
}