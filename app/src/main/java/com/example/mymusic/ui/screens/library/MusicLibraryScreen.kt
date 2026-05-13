package com.example.mymusic.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mymusic.model.Song
import com.example.mymusic.viewmodel.MusicViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Spotify Color Tokens ────────────────────────────────────────────
val SpotifyBlack     = Color(0xFF121212)
val SpotifySurface   = Color(0xFF1E1E1E)
val SpotifySurface2  = Color(0xFF282828)
val SpotifyGreen     = Color(0xFF1DB954)
val SpotifyWhite     = Color(0xFFFFFFFF)
val SpotifyGray      = Color(0xFFB3B3B3)
val SpotifyLightGray = Color(0xFF535353)
val SpotifyElevated  = Color(0xFF3E3E3E)

// ─── Album Art Composable ────────────────────────────────────────────
@Composable
fun AlbumArt(
    artUri: Uri?,
    isActive: Boolean,
    size: Dp = 48.dp,
    cornerRadius: Dp = 4.dp
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(SpotifySurface2),
        contentAlignment = Alignment.Center
    ) {
        if (artUri != null) {
            var loadFailed by remember { mutableStateOf(false) }

            if (!loadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { loadFailed = true }   // ← callback, not composable
                )
            }

            // Show icon if load failed
            if (loadFailed) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (isActive) SpotifyGreen else SpotifyGray,
                    modifier = Modifier.size(size / 2)
                )
            }
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isActive) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}
// ─── Main Screen ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(viewModel: MusicViewModel) {
    val songs       by viewModel.songs.observeAsState(initial = emptyList())
    val currentSong by viewModel.currentSong.observeAsState()
    val isPlaying   by viewModel.isPlaying.observeAsState(false)
    val progress    by viewModel.progress.observeAsState(0L)
    val duration    by viewModel.duration.observeAsState(0L)
    val repeatMode  by viewModel.repeatMode.observeAsState(0)
    val shuffleMode by viewModel.shuffleMode.observeAsState(false)

    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }

    // ── Full-Screen Now Playing ──────────────────────────────────────
    if (showNowPlaying && currentSong != null) {
        NowPlayingScreen(
            song            = currentSong!!,
            isPlaying       = isPlaying,
            progress        = progress,
            duration        = duration,
            repeatMode      = repeatMode,
            shuffleMode     = shuffleMode,
            onBack          = { showNowPlaying = false },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext          = { viewModel.playNext() },
            onPrevious      = { viewModel.playPrevious() },
            onSeek          = { viewModel.seekTo(it) },
            onToggleRepeat  = { viewModel.toggleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() }
        )
        return
    }

    Scaffold(
        containerColor = SpotifyBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpotifyBlack)
                    .statusBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = when (selectedTab) { 1 -> "Search"; 2 -> "Your Library"; else -> "Good evening" },
                            color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp
                        )
                    }
                    Row {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Notifications, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Settings, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Filter chips (Home tab)
                if (selectedTab == 0) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { SpotifyChip("All", true) }
                        item { SpotifyChip("Music", false) }
                        item { SpotifyChip("Podcasts", false) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Search bar (Search tab)
                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Artists, songs, or podcasts", color = SpotifyGray) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = SpotifyBlack) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = SpotifyWhite,
                            unfocusedContainerColor = SpotifyWhite,
                            focusedBorderColor      = Color.Transparent,
                            unfocusedBorderColor    = Color.Transparent,
                            focusedTextColor        = SpotifyBlack,
                            unfocusedTextColor      = SpotifyBlack,
                            cursorColor             = SpotifyBlack
                        ),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(SpotifyBlack)) {
                currentSong?.let { song ->
                    MiniPlayerBar(
                        song              = song,
                        isPlaying         = isPlaying,
                        progress          = progress,
                        duration          = duration,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext            = { viewModel.playNext() },
                        onClick           = { showNowPlaying = true }
                    )
                }
                SpotifyBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        }
    ) { paddingValues ->
        val filteredSongs = if (searchQuery.isBlank()) songs
        else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }

        when (selectedTab) {
            0 -> HomeTab(filteredSongs, currentSong, Modifier.padding(paddingValues)) { song ->
                viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song))
            }
            1 -> SongListTab(filteredSongs, currentSong, Modifier.padding(paddingValues)) { song ->
                viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song))
            }
            2 -> LibraryTab(filteredSongs, currentSong, Modifier.padding(paddingValues)) { song ->
                viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song))
            }
        }
    }
}

// ─── Home Tab ────────────────────────────────────────────────────────
@Composable
fun HomeTab(songs: List<Song>, currentSong: Song?, modifier: Modifier, onSongClick: (Song) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        if (songs.isNotEmpty()) {
            item {
                Text("Recently Added", color = SpotifyWhite, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(songs.take(6).chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { song ->
                        QuickAccessItem(song, song.id == currentSong?.id, { onSongClick(song) }, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Text("All Songs", color = SpotifyWhite, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id) { onSongClick(song) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Search / Generic Song List Tab ──────────────────────────────────
@Composable
fun SongListTab(songs: List<Song>, currentSong: Song?, modifier: Modifier, onSongClick: (Song) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id) { onSongClick(song) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Library Tab ─────────────────────────────────────────────────────
@Composable
fun LibraryTab(songs: List<Song>, currentSong: Song?, modifier: Modifier, onSongClick: (Song) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Library", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Icon(Icons.Filled.Add, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
            }
        }
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id) { onSongClick(song) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Quick Access Grid Item ───────────────────────────────────────────
@Composable
fun QuickAccessItem(song: Song, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SpotifySurface2)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(artUri = song.albumArtUri, isActive = isActive, size = 56.dp, cornerRadius = 0.dp)
        Text(
            text = song.title,
            color = SpotifyWhite, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ─── Song List Item ───────────────────────────────────────────────────
@Composable
fun SongListItem(song: Song, isCurrentSong: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isCurrentSong) SpotifySurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(artUri = song.albumArtUri, isActive = isCurrentSong, size = 48.dp)

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrentSong) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist, color = SpotifyGray,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}

// ─── Mini Player Bar ──────────────────────────────────────────────────
@Composable
fun MiniPlayerBar(
    song: Song, isPlaying: Boolean, progress: Long, duration: Long,
    onTogglePlayPause: () -> Unit, onNext: () -> Unit, onClick: () -> Unit
) {
    val fraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpotifySurface2)
            .clickable { onClick() }
    ) {
        // Green progress strip
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(SpotifyLightGray)) {
            Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(SpotifyGreen))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(artUri = song.albumArtUri, isActive = true, size = 42.dp)

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SpotifyGray, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FavoriteBorder, null, tint = SpotifyWhite, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpotifyWhite, modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.SkipNext, null, tint = SpotifyWhite, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Bottom Navigation ────────────────────────────────────────────────
@Composable
fun SpotifyBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = SpotifyBlack, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Icons.Filled.Home, "Home", 0),
            Triple(Icons.Filled.Search, "Search", 1),
            Triple(Icons.Filled.LibraryMusic, "Library", 2)
        )
        items.forEach { (icon, label, idx) ->
            NavigationBarItem(
                selected = selectedTab == idx,
                onClick  = { onTabSelected(idx) },
                icon     = { Icon(icon, label, modifier = Modifier.size(24.dp)) },
                label    = { Text(label, fontSize = 11.sp) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = SpotifyWhite, selectedTextColor   = SpotifyWhite,
                    unselectedIconColor = SpotifyGray,  unselectedTextColor = SpotifyGray,
                    indicatorColor      = Color.Transparent
                )
            )
        }
    }
}

// ─── Filter Chip ──────────────────────────────────────────────────────
@Composable
fun SpotifyChip(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) SpotifyGreen else SpotifySurface2)
            .clickable {}
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label,
            color = if (selected) SpotifyBlack else SpotifyWhite,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

// ─── Duration Formatter ───────────────────────────────────────────────
fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}