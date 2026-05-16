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
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onDelete         : (Song) -> Unit  // ✅ 1. ADD THIS PARAMETER
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
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
                onDelete         = { onDelete(song) } // ✅ 2. PASS IT DOWN TO SONG ITEM
            )
        }
    }
}