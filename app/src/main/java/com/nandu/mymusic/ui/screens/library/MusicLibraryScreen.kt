package com.nandu.mymusic.ui.screens.library

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.components.IosAlertAction
import com.nandu.mymusic.ui.components.IosAlertDialog
import com.nandu.mymusic.ui.screens.favorites.LikedSongsView
import com.nandu.mymusic.ui.screens.home.ListenNowTab
import com.nandu.mymusic.ui.screens.browse.NewTab
import com.nandu.mymusic.ui.screens.library.NowPlayingScreen
import com.nandu.mymusic.ui.screens.playlist.AddSongsScreen
import com.nandu.mymusic.ui.screens.playlist.PlaylistDetailView
import com.nandu.mymusic.ui.screens.playlist.SortMenu
import com.nandu.mymusic.ui.screens.playlist.SortType
import com.nandu.mymusic.ui.screens.playlist.buildPlaylistExportJson
import com.nandu.mymusic.ui.screens.search.SearchData
import com.nandu.mymusic.ui.screens.search.SearchScreen
import com.nandu.mymusic.ui.theme.AppleText
import com.nandu.mymusic.ui.theme.LikedSongsGradient
import com.nandu.mymusic.ui.theme.appleColors
import com.nandu.mymusic.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import androidx.compose.runtime.key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(viewModel: MusicViewModel) {
    val C = appleColors()
    val libraryMode by viewModel.libraryMode.observeAsState(initial = 0)
    val isDataSaverMode by viewModel.dataSaverMode.observeAsState(initial = true)

    val savedSortTypeStr by viewModel.sortType.observeAsState(initial = "DEFAULT")
    val persistentSortType = remember(savedSortTypeStr) {
        try { SortType.valueOf(savedSortTypeStr) } catch(e: Exception) { SortType.DEFAULT }
    }
    val onSortTypeChange: (SortType) -> Unit = { viewModel.setSortType(it.name) }

    val localSongs by viewModel.getSongs().observeAsState(initial = emptyList())
    val onlineSongs by viewModel.allOnlineSongs.observeAsState(initial = emptyList())

    var hybridLocalOnly by remember { mutableStateOf(false) }
    var hybridOnlineOnly  by remember { mutableStateOf(false) }

    LaunchedEffect(libraryMode) {
        if (libraryMode != 2) {
            hybridLocalOnly = false
            hybridOnlineOnly = false
        }
    }

    val currentDisplaySongs = remember(libraryMode, localSongs, onlineSongs, hybridLocalOnly,hybridOnlineOnly) {
        when (libraryMode) {
            1 -> onlineSongs
            2 -> if (hybridLocalOnly) localSongs else  if (hybridOnlineOnly) onlineSongs else localSongs + onlineSongs
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
    val onlineSongBlocked by viewModel.onlineSongBlocked.observeAsState(false)
    val isRefreshingOnline by viewModel.isRefreshingOnline.observeAsState(false)
    var showBlockedDialog by remember { mutableStateOf(false) }
    // ✨ NEW: Automatically trigger the dialog the moment the song gets blocked
    LaunchedEffect(onlineSongBlocked) {
        if (onlineSongBlocked) {
            showBlockedDialog = true
        } else {
            showBlockedDialog = false
        }
    }
    val favorites = currentDisplaySongs.filter { song -> favoriteIds.contains(song.id) }
    val showNowPlaying by viewModel.isNowPlayingOpen().observeAsState(false)
    val currentLyrics by viewModel.lyrics.observeAsState("Loading...")
    val autoGeneratedPlaylists by viewModel.autoGeneratedPlaylists.observeAsState(initial = emptySet())

    val isSleepTimerActive by viewModel.isSleepTimerActive.observeAsState(false)
    val sleepTimerRemainingMs by viewModel.sleepTimerRemaining.observeAsState(0L)
    val sleepTimerTracksRemaining by viewModel.sleepTimerTracksRemaining.observeAsState(-1)
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.observeAsState(false)
    val nowPlayingSource by viewModel.nowPlayingSource.observeAsState()
    val playHistory by viewModel.playHistory.observeAsState(initial = emptyList())
    val hotTracks by viewModel.hotTracks.observeAsState(initial = emptyList())

    val tabHistory = remember { mutableStateListOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabResets = remember { mutableStateListOf(0, 0, 0, 0) }
    var backPressedTime by remember { mutableStateOf(0L) }
    val viewStack = remember { mutableStateListOf<CollectionView>() }
    val context = LocalContext.current

    // Top Picks rotation seed — fixed for the app session so the shelf is stable
    // while using the app but refreshes on each launch (Apple Music behavior).
    val topPicksRotationSeed = remember { Random.nextInt() }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val showSnackbar: (String) -> Unit = { msg ->
        snackbarHostState.currentSnackbarData?.dismiss()
        coroutineScope.launch {
            val job = launch { snackbarHostState.showSnackbar(msg) }
            kotlinx.coroutines.delay(1300)
            job.cancel()
        }
    }
    val showActionSnackbar: (String, String, () -> Unit) -> Unit = { msg, actionLabel, action ->
        snackbarHostState.currentSnackbarData?.dismiss()
        coroutineScope.launch {
            val job = launch {
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed) {
                    action()
                }
            }
            kotlinx.coroutines.delay(3000)
            job.cancel()
        }
    }
    val handleToggleFavorite: (Song) -> Unit = { song ->
        val isAdding = !favoriteIds.contains(song.id)
        viewModel.toggleFavorite(song)
        if (isAdding) {
            showActionSnackbar("Added to Liked Songs", "Play Next") {
                viewModel.setPlayNext(song)
            }
        } else {
            showSnackbar("Removed from Liked Songs")
        }
    }
    val handleViewAlbum: (String) -> Unit = { viewStack.add(CollectionView.Album(it)) }
    val handleViewArtist: (String) -> Unit = { viewStack.add(CollectionView.Artist(it)) }
    val handleOpenPlaylist: (String, Boolean) -> Unit = { name, isMain -> viewStack.add(CollectionView.Playlist(name, isMain)) }
    val handleOpenLikedSongs: () -> Unit = { viewStack.add(CollectionView.LikedSongs) }
    val popView: () -> Unit = { if (viewStack.isNotEmpty()) viewStack.removeAt(viewStack.lastIndex) }

    val npVisible = showNowPlaying && currentSong != null
    val bottomBarAlpha by animateFloatAsState(
        targetValue = if (npVisible) 0f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "bottomBarAlpha"
    )

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
                showSnackbar("Press back again to exit")
            }
        }
    }

    val playlists by viewModel.customPlaylists.observeAsState(initial = emptyMap())
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var songsForPlaylistDialog by remember { mutableStateOf<List<Song>?>(null) }
    val targetSongsForPlaylistDialog = songForPlaylistDialog?.let { listOf(it) } ?: songsForPlaylistDialog ?: emptyList()
    val clearPlaylistDialogTargets: () -> Unit = { songForPlaylistDialog = null; songsForPlaylistDialog = null }
    var songToDownload by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Playlist detail management state (rename / delete / export / add-songs)
    var playlistToRename by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<String?>(null) }
    var playlistToAddSongsTo by remember { mutableStateOf<String?>(null) }
    var playlistToExport by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = playlistToExport
        if (uri != null && pending != null) {
            val (exportName, exportSongs) = pending
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(buildPlaylistExportJson(exportName, exportSongs).toByteArray())
                }
                showSnackbar("Exported \"$exportName\"")
            } catch (e: Exception) {
                showSnackbar("Export failed")
            }
        }
        playlistToExport = null
    }

    val deleteIntentSender by viewModel.deleteIntentSender.observeAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.confirmPendingDeletion()
            showSnackbar("Successfully Deleted")
        } else {
            showSnackbar("Deletion cancelled")
        }
        viewModel.clearDeleteIntent()
    }

    LaunchedEffect(deleteIntentSender) {
        deleteIntentSender?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    if (songForPlaylistDialog != null || songsForPlaylistDialog != null) {
        IosAlertDialog(
            onDismissRequest = clearPlaylistDialogTargets,
            title = "Add to Playlist",
            actions = listOf(
                IosAlertAction("Cancel", onClick = { clearPlaylistDialogTargets() })
            ),
            content = {
                Column {
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Text("+ Create New Playlist", color = C.accent)
                    }

                    // Filter out auto-generated playlists so they don't appear in the manual add list
                    playlists.filter { !autoGeneratedPlaylists.contains(it.key) }.forEach { (playlistName, songIds) ->
                        val missingSongs = targetSongsForPlaylistDialog.filter { !songIds.contains(it.id) }
                        val isAlreadyInPlaylist = missingSongs.isEmpty()
                        TextButton(
                            onClick = {
                                if (missingSongs.isNotEmpty()) {
                                    viewModel.addSongsToPlaylist(playlistName, missingSongs)
                                    showSnackbar(if (targetSongsForPlaylistDialog.size <= 1) "Added to $playlistName" else "Added ${missingSongs.size} song(s) to $playlistName")
                                    clearPlaylistDialogTargets()
                                }
                            },
                            enabled = !isAlreadyInPlaylist,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = playlistName,
                                    color = if (isAlreadyInPlaylist) C.secondaryLabel.copy(alpha = 0.5f) else C.label
                                )
                                if (isAlreadyInPlaylist) {
                                    Text(
                                        text = "Already in playlist",
                                        color = C.secondaryLabel.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (songToDownload != null) {
        IosAlertDialog(
            onDismissRequest = { songToDownload = null },
            title = "Download Quality",
            message = "Choose the audio quality for '${songToDownload?.title}'",
            actions = listOf(
                IosAlertAction("High Quality", onClick = {
                    downloadSong(context, songToDownload!!, isHighQuality = true, showSnackbar)
                    songToDownload = null
                }, isDefault = true),
                IosAlertAction("Data Saver", onClick = {
                    downloadSong(context, songToDownload!!, isHighQuality = false, showSnackbar)
                    songToDownload = null
                }),
                IosAlertAction("Cancel", onClick = { songToDownload = null })
            )
        )
    }

    if (showBlockedDialog && currentSong != null) {
        IosAlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = "Online Song",
            message = "'${currentSong?.title}' is from your online library. Switch to Online mode to play it, or start a local shuffle instead.",
            actions = listOf(
                IosAlertAction("Switch to Online", onClick = { showBlockedDialog = false; viewModel.switchToOnlineAndPlay() }, isDefault = true),
                IosAlertAction("Play Local Shuffle", onClick = { showBlockedDialog = false; viewModel.playLocalShuffled() })
            )
        )
    }

    if (showCreatePlaylistDialog) {
        IosAlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = "Name your playlist",
            actions = listOf(
                IosAlertAction("Create", onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.createPlaylist(newPlaylistName)
                        if (targetSongsForPlaylistDialog.isNotEmpty()) {
                            viewModel.addSongsToPlaylist(newPlaylistName, targetSongsForPlaylistDialog)
                        }
                        showSnackbar("Added to new playlist: $newPlaylistName")
                    }
                    showCreatePlaylistDialog = false
                    clearPlaylistDialogTargets()
                    newPlaylistName = ""
                }, isDefault = true),
                IosAlertAction("Cancel", onClick = { showCreatePlaylistDialog = false })
            ),
            content = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.label)
                )
            }
        )
    }

    if (playlistToRename != null) {
        IosAlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = "Rename Playlist",
            actions = listOf(
                IosAlertAction("Save", onClick = {
                    val old = playlistToRename
                    if (old != null && renameValue.isNotBlank() && renameValue != old) {
                        viewModel.renamePlaylist(old, renameValue)
                        showSnackbar("Playlist renamed to $renameValue")
                    }
                    playlistToRename = null
                    renameValue = ""
                }, isDefault = true),
                IosAlertAction("Cancel", onClick = { playlistToRename = null })
            ),
            content = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.label)
                )
            }
        )
    }

    if (playlistToDelete != null) {
        IosAlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = "Delete Playlist?",
            message = "Are you sure you want to delete '$playlistToDelete'? This action cannot be undone.",
            actions = listOf(
                IosAlertAction("Delete", onClick = {
                    viewModel.deletePlaylist(playlistToDelete!!)
                    showSnackbar("Playlist deleted")
                    playlistToDelete = null
                }, isDestructive = true),
                IosAlertAction("Cancel", onClick = { playlistToDelete = null })
            )
        )
    }

    if (showEndDialog) {
        IosAlertDialog(
            onDismissRequest = { viewModel.dismissEndOfQueue() },
            title = "End of Queue",
            message = "What would you like to do?",
            actions = listOf(
                IosAlertAction("Play Again", onClick = { viewModel.playFromBeginning() }, isDefault = true),
                IosAlertAction("Shuffle All", onClick = { viewModel.playAllShuffled(currentDisplaySongs) }),
                IosAlertAction("Dismiss", onClick = { viewModel.dismissEndOfQueue() })
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(C.background)) {

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                // Mini player + nav morph away as the Now Playing sheet covers them
                Column(
                    modifier = Modifier
                        .background(Color.Transparent)
                        .graphicsLayer {
                            alpha = bottomBarAlpha
                            scaleX = 0.96f + 0.04f * bottomBarAlpha
                            scaleY = 0.96f + 0.04f * bottomBarAlpha
                        }
                ) {
                    currentSong?.let { song ->
                        MiniPlayerBar(
                            song = song, isPlaying = isPlaying, progress = progress, duration = duration,
                            isFavorite = favoriteIds.contains(song.id),
                            onTogglePlayPause = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.togglePlayPause() },
                            onPrevious = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.playPrevious() },
                            onNext = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.playNextFromMiniPlayer() },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onClick = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.setNowPlayingOpen(true) },
                        )
                    }
                    AppleBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { newTab ->
                            if (newTab != selectedTab) {
                                // Switched to a DIFFERENT tab
                                tabHistory.remove(newTab)
                                tabHistory.add(newTab)
                                selectedTab = newTab
                                viewStack.clear() // Closes pushed views when switching tabs
                            } else {
                                // Re-clicked the CURRENT tab
                                if (viewStack.isNotEmpty()) {
                                    viewStack.clear() // Closes pushed view on double-click
                                } else {
                                    tabResets[newTab]++ // Resets the internal state of the tab
                                }
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->

            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading && libraryMode == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.accent) }
                } else if (viewStack.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().background(C.background).statusBarsPadding()) {
                        val currentView = viewStack.last()
                        when (currentView) {
                            is CollectionView.Album -> {
                                val albumSongs = currentDisplaySongs.filter { it.album == currentView.name }.distinctBy { it.id }
                                CollectionDetailView(
                                    title = currentView.name, songs = albumSongs, currentSong = currentSong, favoriteIds = favoriteIds,
                                    sortType = persistentSortType, onSortChange = onSortTypeChange, showSnackbar = showSnackbar,
                                    onBack = popView, onSongClick = { song -> viewModel.playSong(song, albumSongs) },
                                    onToggleFavorite = { song -> handleToggleFavorite(song) },
                                    onShufflePlay = { viewModel.playShuffled(albumSongs) },
                                    onPlayAll = { if (albumSongs.isNotEmpty()) viewModel.playSong(albumSongs.first(), albumSongs) },
                                    onAddToPlaylist = { songForPlaylistDialog = it }, onAddAllToPlaylist = { songs -> songsForPlaylistDialog = songs },
                                    onViewAlbum = handleViewAlbum, onViewArtist = handleViewArtist, viewModel = viewModel,
                                    onDownload = {song -> songToDownload =song}
                                )
                            }
                            is CollectionView.Artist -> {
                                val searchNames = splitArtists(currentView.name)
                                val artistSongs = currentDisplaySongs.filter { song ->
                                    splitArtists(song.artist).any { songArtist ->
                                        searchNames.any { searchName ->
                                            songArtist.equals(searchName, ignoreCase = true)
                                        }
                                    }
                                }.distinctBy { it.id }
                                CollectionDetailView(
                                    title = currentView.name, songs = artistSongs, currentSong = currentSong, favoriteIds = favoriteIds,
                                    sortType = persistentSortType, onSortChange = onSortTypeChange, showSnackbar = showSnackbar,
                                    onBack = popView, onSongClick = { song -> viewModel.playSong(song, artistSongs) },
                                    onToggleFavorite = { song -> handleToggleFavorite(song) },
                                    onShufflePlay = { viewModel.playShuffled(artistSongs) },
                                    onPlayAll = { if (artistSongs.isNotEmpty()) viewModel.playSong(artistSongs.first(), artistSongs) },
                                    onAddToPlaylist = { songForPlaylistDialog = it }, onAddAllToPlaylist = { songs -> songsForPlaylistDialog = songs },
                                    onViewAlbum = handleViewAlbum, onViewArtist = handleViewArtist, viewModel = viewModel,
                                    onDownload = { song -> songToDownload = song }
                                )
                            }
                            is CollectionView.Playlist -> {
                                val pName = currentView.name
                                val isProtected = currentView.isMain || autoGeneratedPlaylists.contains(pName)
                                val pSongs = if (currentView.isMain) currentDisplaySongs else {
                                    val pIds = playlists[pName] ?: emptyList()
                                    currentDisplaySongs.filter { pIds.contains(it.id) }.distinctBy { it.id }
                                }
                                if (playlistToAddSongsTo == pName) {
                                    val existingIds = playlists[pName] ?: emptyList()
                                    AddSongsScreen(
                                        playlistName = pName,
                                        availableSongs = currentDisplaySongs.filter { !existingIds.contains(it.id) },
                                        onDismiss = { playlistToAddSongsTo = null },
                                        onAddSongs = { selectedSongs ->
                                            viewModel.addSongsToPlaylist(pName, selectedSongs)
                                            showSnackbar("Added ${selectedSongs.size} songs to $pName")
                                            playlistToAddSongsTo = null
                                        }
                                    )
                                } else {
                                    PlaylistDetailView(
                                        playlistName     = pName,
                                        songs            = pSongs,
                                        currentSong      = currentSong,
                                        favoriteIds      = favoriteIds,
                                        sortType         = persistentSortType,
                                        onSortChange     = onSortTypeChange,
                                        showSnackbar     = showSnackbar,
                                        isProtected      = isProtected,
                                        onBack           = popView,
                                        onSongClick      = { song -> viewModel.playSong(song, pSongs) },
                                        onToggleFavorite = { song -> handleToggleFavorite(song) },
                                        onPlayNext       = { song -> viewModel.setPlayNext(song); showSnackbar("Will play next") },
                                        onAddToQueue     = { song -> viewModel.addToQueue(song); showSnackbar("Added to queue") },
                                        onAddToPlaylist  = { song -> songForPlaylistDialog = song },
                                        onViewAlbum      = handleViewAlbum,
                                        onViewArtist     = handleViewArtist,
                                        onDownload       = { song -> songToDownload = song },
                                        onEdit           = { showSnackbar("Edit functionality coming soon!") },
                                        onShufflePlay    = { viewModel.playShuffled(pSongs) },
                                        onPlayAll        = { if (pSongs.isNotEmpty()) viewModel.playSong(pSongs.first(), pSongs) },
                                        onAddSong        = { if (!isProtected) playlistToAddSongsTo = pName },
                                        onExportPlaylist = { playlistToExport = pName to pSongs; exportLauncher.launch("$pName.json") },
                                        onAddAllToQueue  = {
                                            if (pSongs.isNotEmpty()) {
                                                viewModel.addListToQueue(pSongs)
                                                showSnackbar("Added ${pSongs.size} songs to queue")
                                            }
                                        },
                                        onRemoveSong     = { song -> viewModel.removeSongFromPlaylist(pName, song); showSnackbar("Removed 1 song") },
                                        onRemoveSongs    = { list -> viewModel.removeSongsFromPlaylist(pName, list); showSnackbar("Removed ${list.size} songs") },
                                        onDeletePlaylist = { playlistToDelete = pName },
                                        onRenamePlaylist = { renameValue = pName; playlistToRename = pName }
                                    )
                                }
                            }
                            is CollectionView.LikedSongs -> {
                                LikedSongsView(
                                    allSongs = currentDisplaySongs,
                                    favorites = favorites, currentSong = currentSong, favoriteIds = favoriteIds,
                                    sortType = persistentSortType, onSortChange = onSortTypeChange,
                                    onSongClick = { song -> viewModel.playSong(song, favorites) },
                                    onToggleFavorite = { song -> handleToggleFavorite(song) },
                                    onAddSongs = {list ->
                                        viewModel.setFavoriteBatch(list, true)
                                        showSnackbar("Added ${list.size} song/songs to Liked Songs")
                                    },
                                    onRemoveSongs = {list ->
                                        viewModel.setFavoriteBatch(list, false)
                                        showSnackbar("Removed ${list.size} song/songs from Liked Songs")
                                    },
                                    onPlayNext = { song -> viewModel.setPlayNext(song); showSnackbar("Will play next") },
                                    onAddToQueue = { song -> viewModel.addToQueue(song); showSnackbar("Added to queue") },
                                    onAddToPlaylist = { song -> songForPlaylistDialog = song },
                                    onViewAlbum = handleViewAlbum,
                                    onViewArtist = handleViewArtist,
                                    onDownload = { song -> songToDownload = song },
                                    onShufflePlay = { viewModel.playShuffled(favorites) },
                                    onEdit = { showSnackbar("Edit functionality coming soon!") }
                                )
                            }
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> key(tabResets[0]) {
                            ListenNowTab(
                                songs = currentDisplaySongs,
                                favorites = favorites,
                                queue = queue,
                                playHistory = playHistory,
                                currentSong = currentSong,
                                playlists = playlists,
                                autoGeneratedPlaylists = autoGeneratedPlaylists,
                                rotationSeed = topPicksRotationSeed,
                                onSongClick = { song -> viewModel.playSong(song, currentDisplaySongs) },
                                onSongClickIn = { song, playlistSongs -> viewModel.playSong(song, playlistSongs) },
                                onToggleFavorite = { song -> handleToggleFavorite(song) },
                                onOpenPlaylist = { name -> handleOpenPlaylist(name, false) },
                                onOpenLikedSongs = handleOpenLikedSongs,
                                onViewAlbum = handleViewAlbum,
                                onViewArtist = handleViewArtist
                            )
                        }
                        1 -> key(tabResets[1]) {
                            NewTab(
                                allOnlineSongs = onlineSongs,
                                hotTracks = hotTracks,
                                isRefreshing = isRefreshingOnline,
                                onRefresh = { viewModel.refreshOnlineMusic() },
                                currentSong = currentSong,
                                autoGeneratedPlaylists = autoGeneratedPlaylists,
                                rotationSeed = topPicksRotationSeed,
                                isLoading = isLoading,
                                onSongClick = { song, list -> viewModel.playSong(song, list) },
                                onOpenPlaylist = { name -> handleOpenPlaylist(name, false) },
                                onOpenLikedSongs = handleOpenLikedSongs
                            )
                        }
                        2 -> key(tabResets[2]) {
                            LibraryTab(
                                viewModel = viewModel,
                                songs = currentDisplaySongs,
                                currentSong = currentSong,
                                favoriteIds = favoriteIds,
                                playlists = playlists,
                                autoGeneratedPlaylists = autoGeneratedPlaylists,
                                libraryMode = libraryMode,
                                isDataSaverMode = isDataSaverMode,
                                hybridLocalOnly = hybridLocalOnly,
                                hybridOnlineOnly = hybridOnlineOnly,
                                onHybridLocalOnly = { hybridLocalOnly = it; if (it) hybridOnlineOnly = false },
                                onHybridOnlineOnly = { hybridOnlineOnly = it; if (it) hybridLocalOnly = false },
                                sortType = persistentSortType,
                                onSortChange = onSortTypeChange,
                                showSnackbar = showSnackbar,
                                onSongClick = { song -> viewModel.playSong(song, currentDisplaySongs) },
                                onToggleFavorite = { song -> handleToggleFavorite(song) },
                                onShuffleAll = { viewModel.playAllShuffled(currentDisplaySongs) },
                                onAddToPlaylist = { song -> songForPlaylistDialog = song },
                                onAddSongsToPlaylist = { songs -> songsForPlaylistDialog = songs },
                                onViewAlbum = handleViewAlbum,
                                onViewArtist = handleViewArtist,
                                onDownload = { song -> songToDownload = song },
                                onOpenLikedSongs = handleOpenLikedSongs,
                                onOpenPlaylist = { name -> handleOpenPlaylist(name, false) },
                                onOpenMainPlaylist = { name -> handleOpenPlaylist(name, true) }
                            )
                        }
                        3 -> key(tabResets[3]) {
                            SearchScreen(
                                searchQuery = null,
                                onQueryChange = { query -> if (libraryMode == 1 || libraryMode == 2) viewModel.searchOnlineMusic(query) },
                                libraryData = SearchData(
                                    localSongs = localSongs,
                                    onlineSongs = onlineSongs,
                                    onlineSearchSongs = onlineSearchSongs.distinctBy { it.id },
                                    libraryMode = libraryMode,
                                    hybridLocalOnly = hybridLocalOnly,
                                    hybridOnlineOnly = hybridOnlineOnly
                                ),
                                currentSong = currentSong, favoriteIds = favoriteIds,
                                showSnackbar = showSnackbar,
                                onSongClick = { song -> viewModel.playSong(song, listOf(song)) },
                                onToggleFavorite = { song -> handleToggleFavorite(song) },
                                onPlayNext = { song -> viewModel.setPlayNext(song); showSnackbar("Will play next") },
                                onAddToQueue = { song -> viewModel.addToQueue(song); showSnackbar("Added to queue") },
                                onAddToPlaylist = { song -> songForPlaylistDialog = song },
                                onAddSongsToPlaylist = { songs -> songsForPlaylistDialog = songs },
                                onAddListToQueue = { queueSongs -> viewModel.addListToQueue(queueSongs); showSnackbar("Added ${queueSongs.size} songs to queue") },
                                onAddShuffledToQueue = { queueSongs -> viewModel.addListToQueue(queueSongs.shuffled()); showSnackbar("Added ${queueSongs.size} shuffled songs to queue") },
                                onDelete = { song -> viewModel.deleteSong(song);  },
                                onViewAlbum = handleViewAlbum, onViewArtist = handleViewArtist,
                                onDownload = { song -> songToDownload = song }
                            )
                        }
                    }
                }
            }
        }

        val npVisible = showNowPlaying && currentSong != null
        AnimatedVisibility(
            visible = npVisible,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
                initialOffsetY = { it }
            ) + fadeIn(tween(90)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
                targetOffsetY = { it }
            ) + fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize().zIndex(1f)
        ) {
            // Capture the song: during the exit animation currentSong may become null
            val npSong = currentSong ?: return@AnimatedVisibility
            NowPlayingScreen(
                song = npSong,
                queue = queue,
                lyrics = currentLyrics,
                isPlaying = isPlaying,
                progress = progress,
                duration = duration,
                repeatMode = repeatMode,
                shuffleMode = shuffleMode,
                isFavorite = favoriteIds.contains(npSong.id),
                onBack = { viewModel.setNowPlayingOpen(false) },
                onTogglePlayPause = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.togglePlayPause() },
                onNext = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.playNextFromFullPlayer() },
                onPrevious = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.playPrevious() },
                onSeek = { if (onlineSongBlocked) showBlockedDialog = true else viewModel.seekTo(it) },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleFavorite = { viewModel.toggleFavorite(npSong) },
                onQueueItemClick = { clickedSong -> viewModel.playSongFromQueue(clickedSong) },
                onQueueItemRemove = { removedSong -> viewModel.removeSongFromUpcoming(removedSong) },
                onQueueItemReorder = { from, to -> viewModel.moveSongInUpcoming(from, to) },
                onClearQueue = { viewModel.clearQueue(); showSnackbar("Queue cleared") },
                onLoadLyrics = { viewModel.loadLyrics() },
                onAddToPlaylist = { songForPlaylistDialog = npSong },
                onDelete = {
                    viewModel.deleteSong(npSong)
                    viewModel.setNowPlayingOpen(false)
                },
                onViewAlbum = {
                    viewModel.setNowPlayingOpen(false)
                    handleViewAlbum(npSong.album ?: "Unknown")
                },
                onViewArtist = {
                    viewModel.setNowPlayingOpen(false)
                    handleViewArtist(npSong.artist ?: "Unknown Artist")
                },
                onDownload = { songToDownload = npSong },
                isSleepTimerActive = isSleepTimerActive,
                onStartSleepTimerTime = { minutes ->
                    viewModel.startTimeSleepTimer(minutes)
                    showSnackbar("Sleep timer set for $minutes minutes")
                },
                onStartSleepTimerTracks = { tracks ->
                    viewModel.startTrackSleepTimer(tracks)
                    showSnackbar(if (tracks == 1) "Audio will stop when the current song ends" else "Audio will stop after $tracks songs")
                },
                onCancelSleepTimer = {
                    viewModel.cancelSleepTimer()
                    showSnackbar("Sleep timer cancelled")
                },
                onStartSleepTimerQueueEnd = {
                    viewModel.startQueueEndSleepTimer()
                    showSnackbar("Audio will stop at the end of the queue")
                },
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                sleepTimerTracksRemaining = sleepTimerTracksRemaining,
                nowPlayingSource = nowPlayingSource,
                isAutoplayEnabled = isAutoplayEnabled,
                onToggleAutoplay = {
                    viewModel.setAutoplayEnabled(!isAutoplayEnabled)
                    showSnackbar(if (isAutoplayEnabled) "Autoplay off" else "Autoplay on — the queue keeps playing at its end")
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showNowPlaying && currentSong != null) 16.dp else 140.dp)
                .zIndex(2f)
        ) { data ->
            Snackbar(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = Color(0xE63A3A3C),
                contentColor = Color.White,
                actionContentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                action = {
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = { data.performAction() }) {
                            Text(label, color = C.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                Text(
                    text = data.visuals.message,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

