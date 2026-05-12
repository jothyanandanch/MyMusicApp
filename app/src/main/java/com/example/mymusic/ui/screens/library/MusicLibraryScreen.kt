package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mymusic.model.Song
import com.example.mymusic.viewmodel.MusicViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

// Spotify-style dark colors
val SpotifyBlack  = Color(0xFF121212)
val SpotifySurface = Color(0xFF1E1E1E)
val SpotifyGreen  = Color(0xFF1DB954)
val SpotifyWhite  = Color(0xFFFFFFFF)
val SpotifyGray   = Color(0xFFB3B3B3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(viewModel: MusicViewModel) {
    val songs       by viewModel.songs.observeAsState(initial = emptyList())
    val currentSong by viewModel.currentSong.observeAsState()  // add getter in VM
    val isPlaying   by viewModel.isPlaying.observeAsState(false) // add getter in VM

    Scaffold(
        containerColor = SpotifyBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text("My Music", color = SpotifyWhite, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpotifyBlack)
            )
        },
        bottomBar = {
            // Only show the player bar when a song is selected
            currentSong?.let { song ->
                NowPlayingBar(
                    song = song,
                    isPlaying = isPlaying,
                    onTogglePlayPause = { viewModel.togglePlayPause() }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(SpotifyBlack)
        ) {
            items(songs) { song ->
                SongListItem(
                    song = song,
                    isCurrentSong = song.id == currentSong?.id,
                    onClick = { viewModel.playSong(song) }
                )
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, isCurrentSong: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isCurrentSong) SpotifySurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(SpotifySurface, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isCurrentSong) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentSong) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyGray,
                maxLines = 1
            )
        }

        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyGray
        )
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}

@Composable
fun NowPlayingBar(song: Song, isPlaying: Boolean, onTogglePlayPause: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpotifySurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini album art
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SpotifyBlack, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null,
                tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(song.title, color = SpotifyWhite,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(song.artist, color = SpotifyGray,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }

        IconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = SpotifyWhite,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

