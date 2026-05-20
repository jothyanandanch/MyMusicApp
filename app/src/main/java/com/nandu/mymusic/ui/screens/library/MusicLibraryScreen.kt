package com.nandu.mymusic.ui.screens.library

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.screens.favorites.FavoritesScreen
import com.nandu.mymusic.ui.screens.playlist.PlaylistScreen
import com.nandu.mymusic.ui.screens.playlist.SortMenu
import com.nandu.mymusic.ui.screens.playlist.SortType
import com.nandu.mymusic.ui.screens.search.SearchScreen
import com.nandu.mymusic.viewmodel.MusicViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Spotify Color Tokens ─────────────────────────────────────────────
val SpotifyBlack     = Color(0xFF121212)
val SpotifySurface   = Color(0xFF1E1E1E)
val SpotifySurface2  = Color(0xFF282828)
val SpotifyGreen     = Color(0xFF1DB954)
val SpotifyWhite     = Color(0xFFFFFFFF)
val SpotifyGray      = Color(0xFFB3B3B3)
val SpotifyLightGray = Color(0xFF535353)

sealed class CollectionView {
    data class Album(val name: String) : CollectionView()
    data class Artist(val name: String) : CollectionView()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(viewModel: MusicViewModel) {
    // 0 = Local, 1 = Cloud, 2 = Hybrid
    val libraryMode by viewModel.libraryMode.observeAsState(initial = 0)

    val localSongs by viewModel.getSongs().observeAsState(initial = emptyList())
    val onlineSongs by viewModel.allOnlineSongs.observeAsState(initial = emptyList())

    // ✅ DYNAMIC 3-WAY LIST MERGING
    val currentDisplaySongs = remember(libraryMode, localSongs, onlineSongs) {
        when (libraryMode) {
            1 -> onlineSongs
            2 -> localSongs + onlineSongs // Hybrid combines both!
            else -> localSongs
        }
    }

    val onlineSearchSongs by viewModel.onlineSearchResults.observeAsState(initial = emptyList<Song>())
    val currentSong by viewModel.currentSong.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val progress by viewModel.progress.observeAsState(0L)
    val duration by viewModel.duration.observeAsState(0L)
    val repeatMode by viewModel.repeatMode.observeAsState(0)
    val shuffleMode by viewModel.shuffleMode.observeAsState(false)
    val showEndDialog by viewModel.showEndDialog.observeAsState(false)
    val favoriteIds by viewModel.favoriteIds.observeAsState(initial = emptySet())
    val isLoading by viewModel.isLoading.observeAsState(true)
    val queue by viewModel.queue.observeAsState(initial = emptyList())

    val favorites = currentDisplaySongs.filter { song -> favoriteIds.contains(song.id) }
    val showNowPlaying by viewModel.isNowPlayingOpen().observeAsState(false)
    val currentLyrics by viewModel.lyrics.observeAsState("Loading...")

    val tabHistory = remember { mutableStateListOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var backPressedTime by remember { mutableStateOf(0L) }
    val viewStack = remember { mutableStateListOf<CollectionView>() }
    val context = LocalContext.current

    val handleViewAlbum: (String) -> Unit = { viewStack.add(CollectionView.Album(it)) }
    val handleViewArtist: (String) -> Unit = { viewStack.add(CollectionView.Artist(it)) }

    BackHandler {
        if (showNowPlaying) {
            viewModel.setNowPlayingOpen(false)
        } else if (viewStack.isNotEmpty()) {
            viewStack.removeAt(viewStack.lastIndex)
        } else if (tabHistory.size > 1) {
            tabHistory.removeAt(tabHistory.lastIndex)
            selectedTab = tabHistory.last()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                (context as? Activity)?.finishAffinity()
            } else {
                backPressedTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val playlists by viewModel.customPlaylists.observeAsState(initial = emptyMap())
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val deleteIntentSender by viewModel.deleteIntentSender.observeAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.confirmPendingDeletion()
            Toast.makeText(context, "Successfully Deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        viewModel.clearDeleteIntent()
    }

    LaunchedEffect(deleteIntentSender) {
        deleteIntentSender?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    if (songForPlaylistDialog != null) {
        AlertDialog(
            onDismissRequest = { songForPlaylistDialog = null },
            containerColor = SpotifySurface2,
            title = { Text("Add to Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Text("+ Create New Playlist", color = SpotifyGreen)
                    }
                    playlists.keys.forEach { playlistName ->
                        TextButton(
                            onClick = {
                                viewModel.addSongToPlaylist(playlistName, songForPlaylistDialog!!)
                                songForPlaylistDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlistName, color = SpotifyWhite, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songForPlaylistDialog = null }) { Text("Cancel", color = SpotifyGray) }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = SpotifySurface2,
            title = { Text("Name your playlist", color = SpotifyWhite) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = SpotifyWhite)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.createPlaylist(newPlaylistName)
                        viewModel.addSongToPlaylist(newPlaylistName, songForPlaylistDialog!!)
                    }
                    showCreatePlaylistDialog = false
                    songForPlaylistDialog = null
                    newPlaylistName = ""
                }) { Text("Create", color = SpotifyGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel", color = SpotifyGray) }
            }
        )
    }

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
                    TextButton(onClick = { viewModel.playAllShuffled(currentDisplaySongs) }) {
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
            song = currentSong!!,
            queue = queue,
            lyrics = currentLyrics,
            isPlaying = isPlaying,
            progress = progress,
            duration = duration,
            repeatMode = repeatMode,
            shuffleMode = shuffleMode,
            isFavorite = favoriteIds.contains(currentSong!!.id),
            onBack = { viewModel.setNowPlayingOpen(false) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.playNextFromFullPlayer() },
            onPrevious = { viewModel.playPrevious() },
            onSeek = { viewModel.seekTo(it) },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleFavorite = { viewModel.toggleFavorite(currentSong!!) },
            onQueueItemClick = { clickedSong -> viewModel.playSongFromQueue(clickedSong) },
            onQueueItemRemove = { removedSong -> viewModel.removeSongFromUpcoming(removedSong) },
            onQueueItemsRemove = { removedSongs -> viewModel.removeSongsFromUpcoming(removedSongs) },
            onQueueItemReorder = { from, to -> viewModel.moveSongInUpcoming(from, to) },
            onAddToPlaylist = { songForPlaylistDialog = currentSong },
            onAddToQueue = {
                viewModel.addToQueue(currentSong!!)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteSong(currentSong!!)
                viewModel.setNowPlayingOpen(false)
            }
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SpotifyGreen),
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

                    // ✅ 3-WAY PILL TOGGLE (Local / Cloud / Hybrid)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SpotifySurface2)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modes = listOf("Local", "Cloud", "Hybrid")
                        modes.forEachIndexed { index, modeTitle ->
                            val isSelected = libraryMode == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) SpotifyGreen else Color.Transparent)
                                    .clickable { viewModel.setLibraryMode(index) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeTitle,
                                    color = if (isSelected) SpotifyBlack else SpotifyGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 1) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            // Triggers online search if in Cloud or Hybrid mode
                            if (libraryMode == 1 || libraryMode == 2) {
                                viewModel.searchOnlineMusic(it)
                            }
                        },
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder  = { Text("Artists or songs", color = SpotifyGray) },
                        leadingIcon  = { Icon(Icons.Filled.Search, null, tint = SpotifyBlack) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SpotifyWhite, unfocusedContainerColor = SpotifyWhite,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = SpotifyBlack, unfocusedTextColor = SpotifyBlack, cursorColor = SpotifyBlack
                        ),
                        shape      = RoundedCornerShape(6.dp), singleLine = true
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(SpotifyBlack)) {
                currentSong?.let { song ->
                    MiniPlayerBar(
                        song = song,
                        isPlaying = isPlaying,
                        progress = progress,
                        duration = duration,
                        isFavorite = favoriteIds.contains(song.id),
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onPrevious = { viewModel.playPrevious() },
                        onNext = { viewModel.playNextFromMiniPlayer() },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onClick = { viewModel.setNowPlayingOpen(true) },
                    )
                }
                SpotifyBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        if (newTab != selectedTab) {
                            tabHistory.remove(newTab)
                            tabHistory.add(newTab)
                            selectedTab = newTab
                        }
                    }
                )
            }
        }
    ) { paddingValues ->

        // ✅ SMART MULTI-SEARCH FILTERING
        val filteredSongs = if (searchQuery.isBlank()) currentDisplaySongs else {
            val searchLower = searchQuery.lowercase()
            when (libraryMode) {
                1 -> onlineSearchSongs // Cloud only search
                2 -> { // Hybrid Search
                    val localMatches = localSongs.filter {
                        it.title.lowercase().contains(searchLower) || it.artist.lowercase().contains(searchLower)
                    }
                    localMatches + onlineSearchSongs
                }
                else -> { // Local only search
                    localSongs.filter {
                        it.title.lowercase().contains(searchLower) || it.artist.lowercase().contains(searchLower)
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading && libraryMode == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            } else if (currentDisplaySongs.isEmpty() && selectedTab == 0) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val emptyIcon = if (libraryMode == 1) Icons.Filled.CloudOff else Icons.Filled.MusicOff
                    Icon(emptyIcon, null, tint = SpotifyGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))

                    val emptyTitle = when(libraryMode) {
                        1 -> "No online songs found"
                        2 -> "Your library is totally empty"
                        else -> "No local music found"
                    }
                    Text(emptyTitle, color = SpotifyWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    val emptySub = if (libraryMode == 1) "Check your internet connection" else "Download some songs to get started"
                    Text(emptySub, color = SpotifyGray)
                }
            } else if (viewStack.isNotEmpty()) {
                val currentView = viewStack.last()
                when (currentView) {
                    is CollectionView.Album -> {
                        val albumSongs = currentDisplaySongs.filter { it.album == currentView.name }
                        CollectionDetailView(
                            title = currentView.name, songs = albumSongs, currentSong = currentSong, favoriteIds = favoriteIds,
                            onBack = { viewStack.removeAt(viewStack.lastIndex) }, onSongClick = { song -> viewModel.playSong(song, albumSongs) },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) }, onShufflePlay = { viewModel.playShuffled(albumSongs) },
                            onAddToPlaylist = { songForPlaylistDialog = it }, onViewAlbum = handleViewAlbum, onViewArtist = handleViewArtist, viewModel = viewModel
                        )
                    }
                    is CollectionView.Artist -> {
                        val artistSongs = currentDisplaySongs.filter { it.artist == currentView.name }
                        CollectionDetailView(
                            title = currentView.name, songs = artistSongs, currentSong = currentSong, favoriteIds = favoriteIds,
                            onBack = { viewStack.removeAt(viewStack.lastIndex) }, onSongClick = { song -> viewModel.playSong(song, artistSongs) },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) }, onShufflePlay = { viewModel.playShuffled(artistSongs) },
                            onAddToPlaylist = { songForPlaylistDialog = it }, onViewAlbum = handleViewAlbum, onViewArtist = handleViewArtist, viewModel = viewModel
                        )
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> HomeTab(
                        songs = currentDisplaySongs, favorites = favorites, currentSong = currentSong, favoriteIds = favoriteIds,
                        onSongClick = { song -> viewModel.playSong(song, currentDisplaySongs) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onShufflePlay = { viewModel.playAllShuffled(currentDisplaySongs) },
                        onAddToPlaylist = { song -> songForPlaylistDialog = song },
                        onViewAlbum = handleViewAlbum,
                        onViewArtist = handleViewArtist,
                        viewModel = viewModel
                    )
                    1 -> SearchScreen(
                        filteredSongs = filteredSongs, currentSong = currentSong, favoriteIds = favoriteIds,
                        onSongClick = { song -> viewModel.playSong(song, filteredSongs) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onPlayNext = { song -> viewModel.setPlayNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onAddToPlaylist = { song -> songForPlaylistDialog = song },
                        onDelete = { song -> viewModel.deleteSong(song)},
                        onViewAlbum = handleViewAlbum,
                        onViewArtist = handleViewArtist
                    )
                    2 -> PlaylistScreen(
                        songs = currentDisplaySongs, currentSong = currentSong, favoriteIds = favoriteIds,
                        onSongClick = { song -> viewModel.playSong(song, currentDisplaySongs) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        viewModel = viewModel
                    )
                    3 -> FavoritesScreen(
                        favorites = favorites, currentSong = currentSong, favoriteIds = favoriteIds,
                        onSongClick = { song -> viewModel.playSong(song, favorites) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
                    )
                }
            }
        }
    }
}

// ─── Home Tab ─────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTab(
    songs            : List<Song>,
    favorites        : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    showShuffle      : Boolean = true,
    showQuickAccess  : Boolean = true,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onShufflePlay    : () -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit,
    viewModel        : MusicViewModel
) {
    var selectedCategory by remember { mutableStateOf("Songs") }
    var sortType by remember { mutableStateOf(SortType.DEFAULT) }

    val displaySongs = remember(songs, sortType) {
        when (sortType) {
            SortType.DEFAULT -> songs
            SortType.TITLE_AZ -> songs.sortedBy { it.title.lowercase() }
            SortType.TITLE_ZA -> songs.sortedByDescending { it.title.lowercase() }
            SortType.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }

    val displayAlbums = remember(songs, sortType) {
        val albums = songs.groupBy { it.album ?: "Unknown Album" }.entries.toList()
        when (sortType) {
            SortType.DEFAULT -> albums
            SortType.TITLE_AZ -> albums.sortedBy { it.key.lowercase() }
            SortType.TITLE_ZA -> albums.sortedByDescending { it.key.lowercase() }
            SortType.ARTIST -> albums.sortedBy { it.value.firstOrNull()?.artist?.lowercase() ?: "" }
        }
    }

    val displayArtists = remember(songs, sortType) {
        val artists = songs.groupBy { it.artist }.entries.toList()
        when (sortType) {
            SortType.DEFAULT -> artists
            SortType.TITLE_AZ, SortType.ARTIST -> artists.sortedBy { it.key.lowercase() }
            SortType.TITLE_ZA -> artists.sortedByDescending { it.key.lowercase() }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        if (showQuickAccess && songs.isNotEmpty()) {
            item { QuickAccessGrid(songs = songs.take(6), favorites = favorites, currentSong = currentSong, onSongClick = onSongClick) }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip("Songs", selectedCategory == "Songs") { selectedCategory = "Songs" }
                CategoryChip("Albums", selectedCategory == "Albums") { selectedCategory = "Albums" }
                CategoryChip("Artists", selectedCategory == "Artists") { selectedCategory = "Artists" }
            }
        }
        if (showShuffle) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    val headerText = when (selectedCategory) {
                        "Albums" -> "Your albums"
                        "Artists" -> "Your artists"
                        else -> "Your songs"
                    }
                    Text(headerText, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SortMenu(sortType = sortType, onSortChange = { sortType = it })
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onShufflePlay) {
                            Icon(Icons.Filled.Shuffle, "Shuffle", tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        when (selectedCategory) {
            "Songs" -> {
                items(displaySongs, key = { it.id }) { song ->
                    SongItem(
                        song = song, currentSong = currentSong, isFavorite = favoriteIds.contains(song.id),
                        onSongClick = { onSongClick(song) }, onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { viewModel.setPlayNext(song) }, onAddToQueue = { viewModel.addToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) }, onDelete = { viewModel.deleteSong(song) },
                        onViewAlbum = { onViewAlbum(song.album ?: "Unknown Album") }, onViewArtist = { onViewArtist(song.artist) }
                    )
                }
            }
            "Albums" -> {
                items(displayAlbums) { (albumName, albumSongs) ->
                    val firstSong = albumSongs.firstOrNull()
                    LibraryGroupItem(
                        title = albumName, subtitle = "${albumSongs.size} songs",
                        audioUri = firstSong?.uri, song = firstSong,
                        onClick = { onViewAlbum(albumName) }
                    )
                }
            }
            "Artists" -> {
                items(displayArtists) { (artistName, artistSongs) ->
                    val firstSong = artistSongs.firstOrNull()
                    LibraryGroupItem(
                        title = artistName, subtitle = "${artistSongs.size} songs",
                        audioUri = firstSong?.uri, song = firstSong,
                        onClick = { onViewArtist(artistName) }, isCircular = true
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) SpotifyGreen else SpotifySurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (isSelected) SpotifyBlack else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun LibraryGroupItem(title: String, subtitle: String, audioUri: Uri?, song: Song? = null, onClick: () -> Unit, isCircular: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCircular) {
            AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 28.dp)
        } else {
            AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 4.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = SpotifyWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SpotifyGray, fontSize = 13.sp)
        }
    }
}

@Composable
fun CollectionDetailView(
    title: String, songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    onBack: () -> Unit, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit,
    onShufflePlay: () -> Unit, onAddToPlaylist: (Song) -> Unit,
    onViewAlbum: (String) -> Unit, onViewArtist: (String) -> Unit,
    viewModel: MusicViewModel
) {
    var sortType by remember { mutableStateOf(SortType.DEFAULT) }
    val displaySongs = remember(songs, sortType) {
        when (sortType) {
            SortType.DEFAULT -> songs
            SortType.TITLE_AZ -> songs.sortedBy { it.title.lowercase() }
            SortType.TITLE_ZA -> songs.sortedByDescending { it.title.lowercase() }
            SortType.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(SpotifyBlack)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SpotifyWhite) }
                Spacer(Modifier.width(8.dp))
                Text(title, color = SpotifyWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(sortType = sortType, onSortChange = { sortType = it })
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(SpotifyGreen).clickable(onClick = onShufflePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = SpotifyBlack, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        items(displaySongs, key = { it.id }) { song ->
            SongItem(
                song = song, currentSong = currentSong, isFavorite = favoriteIds.contains(song.id),
                onSongClick = { onSongClick(song) }, onToggleFavorite = { onToggleFavorite(song) },
                onPlayNext = { viewModel.setPlayNext(song) }, onAddToQueue = { viewModel.addToQueue(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }, onDelete = { viewModel.deleteSong(song) },
                onViewAlbum = { onViewAlbum(song.album ?: "Unknown Album") }, onViewArtist = { onViewArtist(song.artist) }
            )
        }
    }
}

@Composable
fun QuickAccessGrid(
    songs       : List<Song>,
    favorites   : List<Song>,
    currentSong : Song?,
    onSongClick : (Song) -> Unit
) {
    val displaySongs = if (favorites.isNotEmpty()) favorites.take(6) else songs.take(6)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (favorites.isNotEmpty()) "Liked Songs" else "Jump back in",
            color      = SpotifyWhite,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            modifier   = Modifier.padding(bottom = 8.dp)
        )
        val rows = displaySongs.chunked(2)
        rows.forEach { rowSongs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSongs.forEach { song ->
                    QuickAccessItem(
                        song = song, isActive = song.id == currentSong?.id, onClick = { onSongClick(song) }, modifier = Modifier.weight(1f)
                    )
                }
                if (rowSongs.size < 2) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song             : Song,
    currentSong      : Song?,
    isFavorite       : Boolean,
    onSongClick      : () -> Unit,
    onToggleFavorite : () -> Unit,
    onPlayNext       : () -> Unit = {},
    onAddToQueue     : () -> Unit = {},
    onAddToPlaylist  : () -> Unit = {},
    onDelete         : () -> Unit = {},
    onViewAlbum      : () -> Unit = {},
    onViewArtist     : () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onSongClick)
            .background(if (song.id == currentSong?.id) SpotifySurface else SpotifyBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = song.id == currentSong?.id, size = 48.dp, cornerRadius = 4.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, color = if (song.id == currentSong?.id) SpotifyGreen else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist, color = SpotifyGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (isFavorite) SpotifyGreen else SpotifyGray, modifier = Modifier.size(18.dp))
        }
        Text(formatDuration(song.duration), color = SpotifyGray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, "More options", tint = SpotifyGray, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(SpotifySurface2)) {
                DropdownMenuItem(text = { Text("View Album", color = SpotifyWhite) }, onClick = { showMenu = false; onViewAlbum() })
                DropdownMenuItem(text = { Text("View Artist", color = SpotifyWhite) }, onClick = { showMenu = false; onViewArtist() })
                DropdownMenuItem(text = { Text("Play Next", color = SpotifyWhite) }, onClick = { showMenu = false; onPlayNext(); Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show() })
                DropdownMenuItem(text = { Text("Add to Queue", color = SpotifyWhite) }, onClick = { showMenu = false; onAddToQueue(); Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show() })
                DropdownMenuItem(text = { Text("Add to Playlist", color = SpotifyWhite) }, onClick = { showMenu = false; onAddToPlaylist() })
                DropdownMenuItem(text = {Text("Delete", color = SpotifyWhite)}, onClick = { showMenu = false; onDelete() })
            }
        }
    }
    HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
}

@Composable
fun QuickAccessItem(song: Song, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(4.dp)).background(SpotifySurface2).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = isActive, size = 56.dp, cornerRadius = 4.dp)
        Text(text = song.title, color = if (isActive) SpotifyGreen else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

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

    Column(modifier = Modifier.fillMaxWidth().background(SpotifySurface2).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(SpotifyLightGray)) {
            Box(modifier = Modifier.fillMaxWidth(fraction).height(2.dp).background(SpotifyGreen))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = true, size = 40.dp, cornerRadius = 4.dp)

            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(text = song.title, color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SpotifyGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (isFavorite) SpotifyGreen else SpotifyWhite, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.SkipPrevious, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) { Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(28.dp)) }
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.SkipNext, null, tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}

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

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}