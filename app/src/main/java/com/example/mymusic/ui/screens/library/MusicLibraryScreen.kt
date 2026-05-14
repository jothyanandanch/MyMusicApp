package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import com.example.mymusic.ui.components.AlbumArt
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
    val favoriteIds   by viewModel.getFavoriteIds().observeAsState(initial = emptySet())
    val nowPlayingSignal by viewModel.isNowPlayingOpen().observeAsState(false)

    // Derive the favorites list reactively from favoriteIds + songs
    val favorites = songs.filter { song -> favoriteIds.contains(song.id) }

    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedTab    by remember { mutableIntStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }

    // Sync: if ViewModel signals close (e.g. back button in MainActivity), dismiss NowPlaying
    LaunchedEffect(nowPlayingSignal) {
        if (!nowPlayingSignal) showNowPlaying = false
    }

    // ── End-of-queue dialog ───────────────────────────────────────────
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
                    TextButton(onClick = { viewModel.playAllShuffled(songs) }) {
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
            onBack            = { showNowPlaying = false; viewModel.setNowPlayingOpen(false) },
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

                // Search bar shown only on Search tab
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
                        onClick           = { showNowPlaying = true; viewModel.setNowPlayingOpen(true) }
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
                onSongClick      = { song -> viewModel.playSong(song, filteredSongs) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onShufflePlay    = { viewModel.playAllShuffled(filteredSongs) },
                viewModel        = viewModel
            )
            1 -> HomeTab(
                songs            = filteredSongs,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                onSongClick      = { song -> viewModel.playSong(song, filteredSongs) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                onShufflePlay    = { viewModel.playAllShuffled(filteredSongs) },
                viewModel        = viewModel
            )
            2 -> PlaylistScreen(
                songs            = songs,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                onSongClick      = { song -> viewModel.playSong(song, songs) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                viewModel        = viewModel
            )
            3 -> FavoritesScreen(
                favorites        = favorites,
                currentSong      = currentSong,
                favoriteIds      = favoriteIds,
                onSongClick      = { song -> viewModel.playSong(song, favorites) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
        }
    }
}

// ─── Home Tab ─────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTab(
    songs            : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onShufflePlay    : () -> Unit,
    viewModel        : MusicViewModel
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        // Quick-access grid (top 6 most-played / recent)
        if (songs.isNotEmpty()) {
            item {
                QuickAccessGrid(
                    songs       = songs.take(6),
                    currentSong = currentSong,
                    onSongClick = onSongClick
                )
            }
        }

        // Shuffle button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Your songs",
                    color      = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShufflePlay) {
                        Icon(
                            Icons.Filled.Shuffle, "Shuffle",
                            tint     = SpotifyGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray)
                    }
                }
            }
        }

        // Song list
        items(songs, key = { it.id }) { song ->
            SongItem(
                song             = song,
                currentSong      = currentSong,
                isFavorite       = favoriteIds.contains(song.id),
                onSongClick      = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
    }
}

// ─── Quick Access Grid ────────────────────────────────────────────────
@Composable
fun QuickAccessGrid(
    songs       : List<Song>,
    currentSong : Song?,
    onSongClick : (Song) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Jump back in",
            color      = SpotifyWhite,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            modifier   = Modifier.padding(bottom = 8.dp)
        )
        val rows = songs.chunked(2)
        rows.forEach { rowSongs ->
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSongs.forEach { song ->
                    QuickAccessItem(
                        song     = song,
                        isActive = song.id == currentSong?.id,
                        onClick  = { onSongClick(song) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSongs.size < 2) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Song Item (in list) ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song             : Song,
    currentSong      : Song?,
    isFavorite       : Boolean,
    onSongClick      : () -> Unit,
    onToggleFavorite : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSongClick)
            .background(if (song.id == currentSong?.id) SpotifySurface else SpotifyBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AlbumArt handles null URI + load errors + fallback MusicNote icon
        AlbumArt(
            artUri       = song.albumArtUri,
            isActive     = song.id == currentSong?.id,
            size         = 48.dp,
            cornerRadius = 4.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = song.title,
                color      = if (song.id == currentSong?.id) SpotifyGreen else SpotifyWhite,
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
                contentDescription = null,
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
        // AlbumArt handles null URI + load errors + fallback MusicNote icon
        AlbumArt(
            artUri       = song.albumArtUri,
            isActive     = isActive,
            size         = 56.dp,
            cornerRadius = 4.dp
        )
        Text(
            text       = song.title,
            color      = if (isActive) SpotifyGreen else SpotifyWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ─── Mini Player Bar ──────────────────────────────────────────────────
@Composable
fun MiniPlayerBar(
    song              : Song,
    isPlaying         : Boolean,
    progress          : Long,
    duration          : Long,
    isFavorite        : Boolean,
    onTogglePlayPause : () -> Unit,
    onPrevious        : () -> Unit,
    onNext            : () -> Unit,
    onToggleFavorite  : () -> Unit,
    onClick           : () -> Unit
) {
    val fraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpotifySurface2)
            .clickable { onClick() }
    ) {
        // Thin progress line (Spotify mini-player style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(SpotifyLightGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(SpotifyGreen)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Album art — AlbumArt handles null URI + load errors + fallback icon
            AlbumArt(
                artUri       = song.albumArtUri,
                isActive     = true,
                size         = 40.dp,
                cornerRadius = 4.dp
            )

            // Song info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text       = song.title,
                    color      = SpotifyWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(song.artist, color = SpotifyGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint     = if (isFavorite) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint     = SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ─── Bottom Navigation ────────────────────────────────────────────────
@Composable
fun SpotifyBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = SpotifySurface2, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Icons.Filled.Home,              "Home",         0),
            Triple(Icons.Filled.Search,            "Search",       1),
            Triple(Icons.AutoMirrored.Filled.PlaylistPlay, "Your Library", 2),
            Triple(Icons.Filled.Favorite,          "Liked Songs",  3)
        )
        items.forEach { (icon, label, index) ->
            NavigationBarItem(
                icon     = { Icon(icon, label, modifier = Modifier.size(24.dp)) },
                label    = { Text(label, fontSize = 10.sp) },
                selected = selectedTab == index,
                onClick  = { onTabSelected(index) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = SpotifyWhite,
                    selectedTextColor   = SpotifyWhite,
                    unselectedIconColor = SpotifyGray,
                    unselectedTextColor = SpotifyGray,
                    indicatorColor      = Color.Transparent
                )
            )
        }
    }
}

// ─── Utility ──────────────────────────────────────────────────────────
fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
