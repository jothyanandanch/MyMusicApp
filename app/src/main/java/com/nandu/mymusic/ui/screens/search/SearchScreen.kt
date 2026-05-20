package com.nandu.mymusic.ui.screens.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.screens.library.SongItem

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
    onDelete         : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,  // ✅ Added
    onViewArtist     : (String) -> Unit   // ✅ Added
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
                onDelete         = { onDelete(song) },
                onViewAlbum      = { onViewAlbum(song.album ?: "Unknown Album") }, // ✅ Pass album name
                onViewArtist     = { onViewArtist(song.artist) }                   // ✅ Pass artist name
            )
        }
    }
}