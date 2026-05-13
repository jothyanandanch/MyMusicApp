package com.example.mymusic.ui.screens.library

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymusic.model.Song
import com.example.mymusic.ui.components.AlbumArt
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
    val favoriteIds by viewModel.favoriteIds.observeAsState(initial = emptySet())

    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }

    if (showNowPlaying && currentSong != null) {
        NowPlayingScreen(
            viewModel         = viewModel,
            song              = currentSong!!,
            isPlaying         = isPlaying,
            progress          = progress,
            duration          = duration,
            repeatMode        = repeatMode,
            shuffleMode       = shuffleMode,
            onBack            = { showNowPlaying = false },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext            = { viewModel.playNextFromFullPlayer() },
            onPrevious        = { viewModel.playPrevious() },
            onSeek            = { viewModel.seekTo(it) },
            onToggleRepeat    = { viewModel.toggleRepeat() },
            onToggleShuffle   = { viewModel.toggleShuffle() }
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
                            text = when (selectedTab) {
                                0    -> "Good evening"
                                1    -> "Search"
                                2    -> "Your Library"
                                else -> "Liked Songs"
                            },
                            color = SpotifyWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                    Row {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Notifications, "Notifications",
                                tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Settings, "Settings",
                                tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                        }
                    }
                }

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
                        isFavorite        = favoriteIds.contains(song.id),
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext            = { viewModel.playNextFromMiniPlayer() },
                        onPrevious        = { viewModel.playPrevious() },
                        onToggleFavorite  = { viewModel.toggleFavorite(song) },
                        onClick           = { showNowPlaying = true }
                    )
                }
                SpotifyBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        }
    ) { paddingValues ->
        val filteredSongs = if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }

        when (selectedTab) {
            0 -> HomeTab(
                songs       = filteredSongs,
                currentSong = currentSong,
                favoriteIds = favoriteIds,
                modifier    = Modifier.padding(paddingValues),
                onSongClick = { song -> viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
            1 -> SearchTab(
                songs       = filteredSongs,
                currentSong = currentSong,
                favoriteIds = favoriteIds,
                modifier    = Modifier.padding(paddingValues),
                onSongClick = { song -> viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
            2 -> LibraryTab(
                songs       = filteredSongs,
                currentSong = currentSong,
                favoriteIds = favoriteIds,
                modifier    = Modifier.padding(paddingValues),
                onSongClick = { song -> viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
            3 -> FavoritesTab(
                songs       = viewModel.getFavoritesList(),
                currentSong = currentSong,
                favoriteIds = favoriteIds,
                modifier    = Modifier.padding(paddingValues),
                onSongClick = { song ->
                    val favList = viewModel.getFavoritesList()
                    viewModel.playSong(song, favList, favList.indexOf(song))
                },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
        }
    }
}

// ─── Home Tab ────────────────────────────────────────────────────────
@Composable
fun HomeTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        if (songs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Recently Added", color = SpotifyWhite, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val gridSongs = songs.take(6)
            items(gridSongs.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { song ->
                        QuickAccessItem(song, song.id == currentSong?.id,
                            { onSongClick(song) }, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("All Songs", color = SpotifyWhite, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id,
                favoriteIds.contains(song.id), { onSongClick(song) }, { onToggleFavorite(song) })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Search Tab ──────────────────────────────────────────────────────
@Composable
fun SearchTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id,
                favoriteIds.contains(song.id), { onSongClick(song) }, { onToggleFavorite(song) })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Library Tab ─────────────────────────────────────────────────────
@Composable
fun LibraryTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Your Library", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Icon(Icons.Filled.Add, "Add", tint = SpotifyWhite, modifier = Modifier.size(24.dp))
            }
        }
        items(songs) { song ->
            SongListItem(song, song.id == currentSong?.id,
                favoriteIds.contains(song.id), { onSongClick(song) }, { onToggleFavorite(song) })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Favorites Tab ───────────────────────────────────────────────────
@Composable
fun FavoritesTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            // Header mimicking Spotify's Liked Songs header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF4B2D8C), SpotifyBlack)
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Favorite, null,
                        tint = SpotifyWhite, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Liked Songs", color = SpotifyWhite,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("${songs.size} songs", color = SpotifyGray, fontSize = 13.sp)
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null,
                        tint = SpotifyGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Songs you like will appear here",
                        color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap the heart icon on any song to add it to your Liked Songs.",
                        color = SpotifyGray, fontSize = 13.sp)
                }
            }
        } else {
            items(songs) { song ->
                SongListItem(song, song.id == currentSong?.id,
                    true, { onSongClick(song) }, { onToggleFavorite(song) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Quick Access Grid Item ──────────────────────────────────────────
@Composable
fun QuickAccessItem(song: Song, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) SpotifyGreen.copy(alpha = 0.15f) else SpotifySurface2)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(artUri = song.albumArtUri, isActive = isActive, size = 56.dp, cornerRadius = 4.dp)
        Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp))
    }
}

// ─── Song List Item ──────────────────────────────────────────────────
@Composable
fun SongListItem(
    song: Song, isCurrentSong: Boolean, isFavorite: Boolean,
    onClick: () -> Unit, onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isCurrentSong) SpotifySurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(artUri = song.albumArtUri, isActive = isCurrentSong, size = 48.dp, cornerRadius = 4.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title,
                color = if (isCurrentSong) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = SpotifyGray, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        // ── Heart button inline in the list row ──
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                tint = if (isFavorite) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(18.dp)
            )
        }
        Icon(Icons.Filled.MoreVert, "More", tint = SpotifyGray, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}

// ─── Mini Player Bar ─────────────────────────────────────────────────
@Composable
fun MiniPlayerBar(
    song: Song, isPlaying: Boolean, progress: Long, duration: Long,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit, onNext: () -> Unit,
    onPrevious: () -> Unit, onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val pf = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier.fillMaxWidth().background(SpotifySurface2).clickable { onClick() }
    ) {
        // Progress strip
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(SpotifyLightGray)) {
            Box(modifier = Modifier.fillMaxWidth(pf).fillMaxHeight().background(SpotifyGreen))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(artUri = song.albumArtUri, isActive = isPlaying, size = 42.dp, cornerRadius = 4.dp)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SpotifyGray, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Heart
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                    tint = if (isFavorite) SpotifyGreen else SpotifyWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous",
                    tint = SpotifyWhite, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpotifyWhite, modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.SkipNext, "Next",
                    tint = SpotifyWhite, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Bottom Navigation ────────────────────────────────────────────────
@Composable
fun SpotifyBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = SpotifyBlack, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selectedTab == 0, onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Filled.Home, "Home", modifier = Modifier.size(24.dp)) },
            label = { Text("Home", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SpotifyWhite, selectedTextColor = SpotifyWhite,
                unselectedIconColor = SpotifyGray, unselectedTextColor = SpotifyGray,
                indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = selectedTab == 1, onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Filled.Search, "Search", modifier = Modifier.size(24.dp)) },
            label = { Text("Search", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SpotifyWhite, selectedTextColor = SpotifyWhite,
                unselectedIconColor = SpotifyGray, unselectedTextColor = SpotifyGray,
                indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = selectedTab == 2, onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Filled.LibraryMusic, "Library", modifier = Modifier.size(24.dp)) },
            label = { Text("Library", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SpotifyWhite, selectedTextColor = SpotifyWhite,
                unselectedIconColor = SpotifyGray, unselectedTextColor = SpotifyGray,
                indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = selectedTab == 3, onClick = { onTabSelected(3) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == 3) Icons.Filled.Favorite
                                  else Icons.Filled.FavoriteBorder,
                    contentDescription = "Liked Songs",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Liked", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SpotifyGreen, selectedTextColor = SpotifyGreen,
                unselectedIconColor = SpotifyGray, unselectedTextColor = SpotifyGray,
                indicatorColor = Color.Transparent)
        )
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
            fontSize = 13.sp)
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
