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
import androidx.compose.runtime.key

fun splitArtists(artistString: String?): List<String> {
    if (artistString.isNullOrBlank()) return listOf("Unknown Artist")
    val parts = artistString.split(
        Regex(",\\s*|\\s*&\\s*|\\s+and\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+x\\s+", RegexOption.IGNORE_CASE)
    )
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(artistString.trim()) }
}

sealed class CollectionView {
    data class Album(val name: String) : CollectionView()
    data class Artist(val name: String) : CollectionView()
    data class Playlist(val name: String, val isMain: Boolean = false) : CollectionView()
    data object LikedSongs : CollectionView()
}

fun downloadSong(context: Context, song: Song, isHighQuality: Boolean, showSnackbar: (String) -> Unit) {
    try {
        var downloadUrl = song.uri.toString()

        if (isHighQuality && downloadUrl.contains("/upload/q_auto,f_auto/")) {
            downloadUrl = downloadUrl.replace("/upload/q_auto,f_auto/", "/upload/")
        } else if (!isHighQuality && !downloadUrl.contains("q_auto,f_auto") && downloadUrl.contains("/upload/")) {
            downloadUrl = downloadUrl.replace("/upload/", "/upload/q_auto,f_auto/")
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(song.title)
            .setDescription("Downloading ${song.title}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "${song.title}.mp3")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        val qualityText = if (isHighQuality) "High Quality" else "Data Saver"
        showSnackbar("Downloading $qualityText...")
    } catch (e: Exception) {
        showSnackbar("Failed to start download")
    }
}

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

    val tabHistory = remember { mutableStateListOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabResets = remember { mutableStateListOf(0, 0, 0, 0) }
    var backPressedTime by remember { mutableStateOf(0L) }
    val viewStack = remember { mutableStateListOf<CollectionView>() }
    val context = LocalContext.current

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
                                        onRemoveSongs    = { list -> list.forEach { viewModel.removeSongFromPlaylist(pName, it) }; showSnackbar("Removed ${list.size} songs") },
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
                                        list.forEach {song -> if (!favoriteIds.contains(song.id)) viewModel.toggleFavorite(song)}
                                        showSnackbar("Added ${list.size} song/songs to Liked Songs")
                                    },
                                    onRemoveSongs = {list ->
                                        list.forEach { song -> if (favoriteIds.contains(song.id)) viewModel.toggleFavorite(song) }
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
                                currentSong = currentSong,
                                playlists = playlists,
                                autoGeneratedPlaylists = autoGeneratedPlaylists,
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
                                isRefreshing = isRefreshingOnline,
                                onRefresh = { viewModel.refreshOnlineMusic() },
                                currentSong = currentSong,
                                autoGeneratedPlaylists = autoGeneratedPlaylists,
                                isLoading = isLoading,
                                onSongClick = { song, list -> viewModel.playSong(song, list) },
                                onOpenPlaylist = { name -> handleOpenPlaylist(name, false) }
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
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                sleepTimerTracksRemaining = sleepTimerTracksRemaining,
                onStartSleepTimerTime = { minutes ->
                    viewModel.startTimeSleepTimer(minutes)
                    showSnackbar("Sleep timer set for $minutes minutes")
                },
                onStartSleepTimerTracks = { tracks ->
                    viewModel.startTrackSleepTimer(tracks)
                    showSnackbar("Audio will stop after $tracks songs")
                },
                onStartSleepTimerQueue = {
                    viewModel.startQueueEndSleepTimer()
                    showSnackbar("Audio will stop at end of queue")
                },
                onCancelSleepTimer = {
                    viewModel.cancelSleepTimer()
                    showSnackbar("Sleep timer cancelled")
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

// ─── iOS-style section header with optional trailing content ──────────
@Composable
fun LargeTitleHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val C = appleColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppleText.LargeTitle, color = C.label)
        trailing?.invoke(this)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val C = appleColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppleText.Title2, color = C.label)
        trailing?.invoke(this)
    }
}

// ─── Library mode segmented control (Local / Cloud / Hybrid) ──────────
@Composable
fun ModeSwitcher(
    libraryMode: Int,
    onModeChange: (Int) -> Unit,
    isDataSaverMode: Boolean,
    onDataSaverChange: (Boolean) -> Unit,
    hybridLocalOnly: Boolean,
    hybridOnlineOnly: Boolean,
    onHybridLocalOnly: (Boolean) -> Unit,
    onHybridOnlineOnly: (Boolean) -> Unit
) {
    val C = appleColors()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(C.fill)
                .padding(2.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf("Local", "Cloud", "Hybrid")
                modes.forEachIndexed { index, modeTitle ->
                    val isSelected = libraryMode == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) C.card else Color.Transparent)
                            .clickable { onModeChange(index) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeTitle,
                            color = C.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DataUsage, "Data Saver", tint = if (isDataSaverMode) C.accent else C.secondaryLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Data Saver", color = C.secondaryLabel, fontSize = 13.sp)
            }
            Switch(
                checked = isDataSaverMode, onCheckedChange = onDataSaverChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = C.systemGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = C.fill,
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.scale(0.75f)
            )
        }
        if (libraryMode == 2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Only toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (hybridLocalOnly) C.accent else C.fill)
                        .clickable { onHybridLocalOnly(!hybridLocalOnly) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Local Only",
                        color = if (hybridLocalOnly) Color.White else C.secondaryLabel,
                        fontWeight = if (hybridLocalOnly) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
                // Cloud Only toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (hybridOnlineOnly) C.accent else C.fill)
                        .clickable { onHybridOnlineOnly(!hybridOnlineOnly) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cloud Only",
                        color = if (hybridOnlineOnly) Color.White else C.secondaryLabel,
                        fontWeight = if (hybridOnlineOnly) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─── Apple Music style song card for horizontal shelves ───────────────
@Composable
fun SongShelfCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val C = appleColors()
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = false, size = null, cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(song.title ?: "Unknown Title", color = C.label, style = AppleText.Footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, style = AppleText.Caption1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistShelfCard(name: String, songCount: Int, gradient: List<Color>? = null, artSong: Song? = null, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val C = appleColors()
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(gradient ?: listOf(C.fill, C.fill))),
            contentAlignment = Alignment.Center
        ) {
            if (artSong != null) {
                AlbumArt(song = artSong, audioUri = artSong.uri, title = artSong.title, artist = artSong.artist, isActive = false, size = null, cornerRadius = 10.dp, modifier = Modifier.fillMaxSize())
            } else {
                Icon(icon ?: Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, color = C.label, style = AppleText.Footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$songCount song${if (songCount != 1) "s" else ""}", color = C.secondaryLabel, style = AppleText.Caption1, maxLines = 1)
    }
}

@Composable
fun CollectionDetailView(
    title: String, songs: List<Song>, currentSong: Song?, favoriteIds: Set<Long>,
    sortType: SortType, onSortChange: (SortType) -> Unit, showSnackbar: (String) -> Unit,
    onBack: () -> Unit, onSongClick: (Song) -> Unit, onToggleFavorite: (Song) -> Unit,
    onShufflePlay: () -> Unit, onPlayAll: () -> Unit, onAddToPlaylist: (Song) -> Unit,
    onAddAllToPlaylist: (List<Song>) -> Unit = {},
    onViewAlbum: (String) -> Unit, onViewArtist: (String) -> Unit, viewModel: MusicViewModel,
    onDownload: (Song) -> Unit
) {
    val C = appleColors()
    var showCollectionMenu by remember { mutableStateOf(false) }
    val displaySongs = remember(songs, sortType) {
        when (sortType) {
            SortType.DEFAULT -> songs
            SortType.TITLE_AZ -> songs.sortedBy { it.title?.lowercase() ?: "" }
            SortType.TITLE_ZA -> songs.sortedByDescending { it.title?.lowercase() ?: "" }
            SortType.ARTIST -> songs.sortedBy { it.artist?.lowercase() ?: "" }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(C.background)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.label) }
                Spacer(Modifier.width(8.dp))
                Text(title, color = C.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(sortType = sortType, onSortChange = onSortChange)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { showCollectionMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel)
                        }
                        DropdownMenu(expanded = showCollectionMenu, onDismissRequest = { showCollectionMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                            DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) onAddAllToPlaylist(songs)
                            })
                            DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) {
                                    viewModel.addListToQueue(songs)
                                    showSnackbar("Added ${songs.size} songs to queue")
                                }
                            })
                            DropdownMenuItem(text = { Text("Add to Queue (Shuffled)", color = C.label) }, onClick = {
                                showCollectionMenu = false
                                if (songs.isNotEmpty()) {
                                    viewModel.addListToQueue(songs.shuffled())
                                    showSnackbar("Added ${songs.size} shuffled songs to queue")
                                }
                            })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onPlayAll) {
                        Icon(Icons.Filled.PlayArrow, "Play All", tint = C.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onShufflePlay) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = C.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", color = C.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
        items(displaySongs, key = { it.id }) { song ->
            SongItem(
                song = song, currentSong = currentSong, isFavorite = favoriteIds.contains(song.id),
                onSongClick = { onSongClick(song) }, onToggleFavorite = { onToggleFavorite(song) },
                onPlayNext = { viewModel.setPlayNext(song); showSnackbar("Will play next") },
                onAddToQueue = { viewModel.addToQueue(song); showSnackbar("Added to queue") },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onDelete = { viewModel.deleteSong(song); },
                onEdit = { showSnackbar("Edit functionality coming soon!") },
                onDownload = { onDownload(song) },
                onViewAlbum = { onViewAlbum(song.album ?: "Unknown Album") }, onViewArtist = { onViewArtist(song.artist ?: "Unknown Artist") }
            )
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
    onEdit           : () -> Unit = {},
    onDownload       : () -> Unit = {},
    onViewAlbum      : () -> Unit = {},
    onViewArtist     : () -> Unit = {}
) {
    val C = appleColors()
    var showMenu by remember { mutableStateOf(false) }
    val isOnline = song.uri.toString().startsWith("http")
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onSongClick)
            .background(if (song.id == currentSong?.id) C.card else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = song.id == currentSong?.id, size = 48.dp, cornerRadius = 6.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title ?: "Unknown Title", color = if (song.id == currentSong?.id) C.accent else C.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (isFavorite) C.accent else C.secondaryLabel, modifier = Modifier.size(18.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOnline) Icon(Icons.Filled.Cloud, contentDescription = "Online", tint = C.accent, modifier = Modifier.size(14.dp).padding(end = 4.dp))
            Text(formatDuration(song.duration), color = C.secondaryLabel, fontSize = 12.sp)
        }

        Spacer(Modifier.width(4.dp))

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp)) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                DropdownMenuItem(text = { Text("View Album", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onViewAlbum() })
                DropdownMenuItem(text = { Text("View Artist", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onViewArtist() })
                DropdownMenuItem(text = { Text("Play Next", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onPlayNext() })
                DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onAddToQueue() })
                DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onAddToPlaylist() })

                if (isOnline) {
                    DropdownMenuItem(text = { Text("Download Song", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onDownload() })
                } else {
                    DropdownMenuItem(text = { Text("Edit Song Info", color = C.label) }, onClick = { showMenu = false; focusManager.clearFocus(); onEdit() })
                    DropdownMenuItem(text = { Text("Delete from Device", color = C.destructive) }, onClick = { showMenu = false; focusManager.clearFocus(); onDelete() })
                }
            }
        }
    }
    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
}

@Composable
fun LibraryGroupItem(
    title: String, subtitle: String, audioUri: Uri?, song: Song? = null, onClick: () -> Unit, isCircular: Boolean = false,
    songs: List<Song> = emptyList(),
    onAddToPlaylist: ((List<Song>) -> Unit)? = null,
    onAddToQueue: ((List<Song>) -> Unit)? = null,
    onAddShuffledToQueue: ((List<Song>) -> Unit)? = null
) {
    val C = appleColors()
    var showMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isCircular) { AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 28.dp)
        } else { AlbumArt(audioUri = audioUri, song = song, title = title, artist = "", isActive = false, size = 56.dp, cornerRadius = 8.dp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = C.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = C.secondaryLabel, fontSize = 13.sp)
        }
        if (onAddToPlaylist != null || onAddToQueue != null || onAddShuffledToQueue != null) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, "More options", tint = C.secondaryLabel, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(C.cardElevated)) {
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(text = { Text("Add to Playlist", color = C.label) }, onClick = { showMenu = false; onAddToPlaylist(songs) })
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(text = { Text("Add to Queue", color = C.label) }, onClick = { showMenu = false; if (songs.isNotEmpty()) onAddToQueue(songs) })
                    }
                    if (onAddShuffledToQueue != null) {
                        DropdownMenuItem(text = { Text("Add to Queue (Shuffled)", color = C.label) }, onClick = { showMenu = false; if (songs.isNotEmpty()) onAddShuffledToQueue(songs) })
                    }
                }
            }
        }
    }
}

// ─── Floating Apple Music mini player ─────────────────────────────────
@Composable
fun MiniPlayerBar(
    song: Song, isPlaying: Boolean, progress: Long, duration: Long, isFavorite: Boolean,
    onTogglePlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onToggleFavorite: () -> Unit, onClick: () -> Unit
) {
    val C = appleColors()
    val fraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.bar)
            .border(0.5.dp, C.barSeparator, RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        // Thin progress line across the top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .align(Alignment.TopStart)
                .background(C.barSeparator.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.5.dp)
                    .background(C.accent)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 0.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AlbumArt(song = song, audioUri = song.uri, title = song.title, artist = song.artist, isActive = true, size = 54.dp, cornerRadius = 0.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(text = song.title ?: "Unknown Title", color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "Unknown Artist", color = C.secondaryLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = C.label, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = C.label, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun AppleBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val C = appleColors()
    NavigationBar(containerColor = C.bar, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Icons.Filled.Home, "Listen Now", 0),
            Triple(Icons.Filled.GridView, "New", 1),
            Triple(Icons.AutoMirrored.Filled.QueueMusic, "Library", 2),
            Triple(Icons.Filled.Search, "Search", 3)
        )
        items.forEach { (icon, label, index) ->
            NavigationBarItem(
                icon = { Icon(icon, label, modifier = Modifier.size(24.dp)) }, label = { Text(label, fontSize = 10.sp) }, selected = selectedTab == index, onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = C.accent, selectedTextColor = C.accent, unselectedIconColor = C.secondaryLabel, unselectedTextColor = C.secondaryLabel, indicatorColor = Color.Transparent)
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
