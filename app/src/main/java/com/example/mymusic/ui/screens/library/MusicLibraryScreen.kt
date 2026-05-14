package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mymusic.model.Song
import com.example.mymusic.viewmodel.MusicViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.mymusic.ui.screens.favorites.FavoritesScreen
import com.example.mymusic.ui.screens.playlist.PlaylistScreen

// ─── Spotify Color Tokens ─────────────────────────────────────────────
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
    val songs         by viewModel.getSongs().observeAsState(initial = emptyList())
    val currentSong   by viewModel.getCurrentSong().observeAsState()
    val isPlaying     by viewModel.getIsPlaying().observeAsState(false)
    val progress      by viewModel.getProgress().observeAsState(0L)
    val duration      by viewModel.getDuration().observeAsState(0L)
    val repeatMode    by viewModel.getRepeatMode().observeAsState(0)
    val shuffleMode   by viewModel.getShuffleMode().observeAsState(false)
    val showEndDialog by viewModel.getShowEndDialog().observeAsState(false)
    val favoriteIds by viewModel.getFavoriteIds().observeAsState(initial = emptySet())

// Then derive the favorites list from it:
    val favorites = viewModel.getSongs().observeAsState(initial = emptyList()).value
        .filter { song -> favoriteIds.contains(song.id) }

    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableIntStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }

    // End-of-queue dialog
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEndOfQueue() },
            containerColor   = SpotifySurface2,
            title = { Text("End of Queue", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text  = { Text("What would you like to do?", color = SpotifyGray) },
            confirmButton = {
                TextButton(onClick = { viewModel.playFromBeginning() }) {
                    Text("Play Again", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.playAllShuffled() }) {
                        Text("Shuffle All", color = SpotifyGray)
                    }
                    TextButton(onClick = { viewModel.dismissEndOfQueue() }) {
                        Text("Dismiss", color = SpotifyGray)
                    }
                }
            }
        )
    }

    if (showNowPlaying && currentSong != null) {
        NowPlayingScreen(
            song              = currentSong!!,
            isPlaying         = isPlaying,
            progress          = progress,
            duration          = duration,
            repeatMode        = repeatMode,
            shuffleMode       = shuffleMode,
            isFavorite        = favoriteIds.contains(currentSong!!.id),
            onBack            = { showNowPlaying = false },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext            = { viewModel.playNextFromFullPlayer() },
            onPrevious        = { viewModel.playPrevious() },
            onSeek            = { viewModel.seekTo(it) },
            onToggleRepeat    = { viewModel.toggleRepeat() },
            onToggleShuffle   = { viewModel.toggleShuffle() },
            onToggleFavorite  = { viewModel.toggleFavorite(currentSong!!) }
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
                    verticalAlignment     = Alignment.CenterVertically,
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
                                2    -> "Playlists"
                                else -> "Liked Songs"
                            },
                            color      = SpotifyWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp
                        )
                    }
                    Row {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Notifications, null, tint = SpotifyWhite)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Settings, null, tint = SpotifyWhite)
                        }
                    }
                }

                if (selectedTab == 0) {

                }

                if (selectedTab == 1) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder  = { Text("Artists or songs", color = SpotifyGray) },
                        leadingIcon  = { Icon(Icons.Filled.Search, null, tint = SpotifyBlack) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = SpotifyWhite,
                            unfocusedContainerColor = SpotifyWhite,
                            focusedBorderColor      = Color.Transparent,
                            unfocusedBorderColor    = Color.Transparent,
                            focusedTextColor        = SpotifyBlack,
                            unfocusedTextColor      = SpotifyBlack,
                            cursorColor             = SpotifyBlack
                        ),
                        shape      = RoundedCornerShape(6.dp),
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
                        onPrevious        = { viewModel.playPrevious() },
                        onNext            = { viewModel.playNextFromMiniPlayer() },
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
                songs            = filteredSongs,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                modifier         = Modifier.padding(paddingValues),
                onSongClick      = { song -> viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
            1 -> SearchTab(
                songs            = filteredSongs,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                modifier         = Modifier.padding(paddingValues),
                onSongClick      = { song -> viewModel.playSong(song, filteredSongs, filteredSongs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
            2 -> PlaylistScreen(
                localSongs       = songs,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                modifier         = Modifier.padding(paddingValues),
                onSongClick      = { song -> viewModel.playSong(song, songs, songs.indexOf(song)) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onShufflePlay = { viewModel.playShuffled(songs) }
            )
            3 -> FavoritesScreen(
                favorites = favorites,  // ✅ Will update when favoriteIds changes
                currentSong = currentSong,
                favoriteIds = favoriteIds,
                modifier = Modifier.padding(paddingValues)
                    .fillMaxSize(),
                onSongClick = { song ->
                    viewModel.playSong(song, favorites, favorites.indexOf(song))
                },
                onToggleFavorite = { song ->
                    val favList = viewModel.getFavoritesList()
                    viewModel.toggleFavorite(song)
                }
            )
        }
    }
}


// ─── Home Tab ─────────────────────────────────────────────────────────
@Composable
fun HomeTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        if (songs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recently Added",
                    color      = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val gridSongs = songs.take(6)
            items(gridSongs.chunked(2)) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { song ->
                        QuickAccessItem(
                            song     = song,
                            isActive = song.id == currentSong?.id,
                            onClick  = { onSongClick(song) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "All Songs",
                    color      = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(songs) { song ->
            SongListItem(
                song             = song,
                isCurrentSong    = song.id == currentSong?.id,
                isFavorite       = favoriteIds.contains(song.id),
                onClick          = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Search Tab ───────────────────────────────────────────────────────
@Composable
fun SearchTab(
    songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    modifier: Modifier, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(SpotifyBlack)) {
        items(songs) { song ->
            SongListItem(
                song             = song,
                isCurrentSong    = song.id == currentSong?.id,
                isFavorite       = favoriteIds.contains(song.id),
                onClick          = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Song List Item ───────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song, isCurrentSong: Boolean, isFavorite: Boolean,
    onClick: () -> Unit, onToggleFavorite: () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            containerColor   = SpotifySurface2,
            title = {
                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(song.artist, color = SpotifyGray, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = { showTooltip = false }) {
                    Text("Close", color = SpotifyGreen)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { showTooltip = true }
            )
            .background(if (isCurrentSong) SpotifySurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyElevated),
            contentAlignment = Alignment.Center
        ) {
            // ✅ Show album art if available, music note if not
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art for ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback: music note icon
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }


        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text       = song.title,
                color      = if (isCurrentSong) SpotifyGreen else SpotifyWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = song.artist,
                color    = SpotifyGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                tint               = if (isFavorite) SpotifyGreen else SpotifyGray,
                modifier           = Modifier.size(18.dp)
            )
        }
        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
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
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (isActive) SpotifyGreen.copy(alpha = 0.3f) else SpotifyElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote, null,
                tint     = if (isActive) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text       = song.title,
            color      = SpotifyWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ─── Mini Player Bar — WITH Previous button ───────────────────────────
@Composable
fun MiniPlayerBar(
    song              : Song,
    isPlaying         : Boolean,
    progress          : Long,
    duration          : Long,
    isFavorite        : Boolean,
    onTogglePlayPause : () -> Unit,
    onPrevious        : () -> Unit,   // ← ADDED
    onNext            : () -> Unit,
    onToggleFavorite  : () -> Unit,
    onClick           : () -> Unit
) {
    val progressFraction =
        if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpotifySurface2)
            .clickable { onClick() }
    ) {
        // Green progress strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(SpotifyLightGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(SpotifyGreen)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SpotifyElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }

            // Song title + artist
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = SpotifyGray,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // Like / unlike
            IconButton(
                onClick  = onToggleFavorite,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector        = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Unlike" else "Like",
                    tint               = if (isFavorite) SpotifyGreen else SpotifyWhite,
                    modifier           = Modifier.size(22.dp)
                )
            }

            // Previous ← ADDED
            IconButton(
                onClick  = onPrevious,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.SkipPrevious, "Previous",
                    tint     = SpotifyWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Play / Pause
            IconButton(
                onClick  = onTogglePlayPause,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint               = SpotifyWhite,
                    modifier           = Modifier.size(28.dp)
                )
            }

            // Next
            IconButton(
                onClick  = onNext,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext, "Next",
                    tint     = SpotifyWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ─── Bottom Navigation ────────────────────────────────────────────────
@Composable
fun SpotifyBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = SpotifyBlack, tonalElevation = 0.dp) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor   = SpotifyWhite,
            selectedTextColor   = SpotifyWhite,
            unselectedIconColor = SpotifyGray,
            unselectedTextColor = SpotifyGray,
            indicatorColor      = Color.Transparent
        )
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick  = { onTabSelected(0) },
            icon     = { Icon(Icons.Filled.Home, "Home", modifier = Modifier.size(24.dp)) },
            label    = { Text("Home", fontSize = 11.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick  = { onTabSelected(1) },
            icon     = { Icon(Icons.Filled.Search, "Search", modifier = Modifier.size(24.dp)) },
            label    = { Text("Search", fontSize = 11.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick  = { onTabSelected(2) },
            icon     = { Icon(Icons.Filled.PlaylistPlay, "Playlists", modifier = Modifier.size(24.dp)) },
            label    = { Text("Playlists", fontSize = 11.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick  = { onTabSelected(3) },
            icon = {
                Icon(
                    imageVector        = if (selectedTab == 3) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Liked Songs",
                    modifier           = Modifier.size(24.dp)
                )
            },
            label    = { Text("Liked", fontSize = 11.sp) },
            colors   = navColors
        )
    }
}

// ─── Chip ─────────────────────────────────────────────────────────────
@Composable
fun SpotifyChip(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) SpotifyGreen else SpotifySurface2)
            .clickable {}
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color      = if (selected) SpotifyBlack else SpotifyWhite,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize   = 13.sp
        )
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}