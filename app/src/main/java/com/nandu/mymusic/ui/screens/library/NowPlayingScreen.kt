package com.nandu.mymusic.ui.screens.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.media3.common.Player
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.utils.parseLrc
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song             : Song,
    queue            : List<Song>,
    lyrics           : String?,
    isPlaying        : Boolean,
    progress         : Long,
    duration         : Long,
    repeatMode       : Int,
    shuffleMode      : Boolean,
    isFavorite       : Boolean,
    onBack           : () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext           : () -> Unit,
    onPrevious       : () -> Unit,
    onSeek           : (Long) -> Unit,
    onToggleRepeat   : () -> Unit,
    onToggleShuffle  : () -> Unit,
    onToggleFavorite : () -> Unit,
    onQueueItemClick : (Song) -> Unit,
    onQueueItemRemove: (Song) -> Unit,
    onQueueItemsRemove: (Set<Song>) -> Unit,
    onQueueItemReorder: (Int, Int) -> Unit,
    onClearQueue      : () -> Unit = {},
    onAddToPlaylist  : () -> Unit = {},
    onAddToQueue     : () -> Unit = {},
    onLoadLyrics     : () -> Unit = {},
    onDelete         : () -> Unit = {},
    onViewAlbum      : () -> Unit = {},
    onViewArtist     : () -> Unit = {},
    onDownload       : () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    sleepTimerRemainingMs: Long = 0L, // ✅ Added to catch live timer
    sleepTimerTracksRemaining: Int = -1,
    onStartSleepTimerTime: (Int) -> Unit = {},
    onStartSleepTimerTracks: (Int) -> Unit = {},
    onStartSleepTimerQueue: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {}
) {
    var showSleepTimer by remember { mutableStateOf(false) }
    val rawFraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isOnline = song.uri.toString().startsWith("http")

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3D6B4F), SpotifyBlack), startY = 0f, endY = 1800f))
            .graphicsLayer { translationY = offsetY.value; alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (offsetY.value > 400f) onBack() else coroutineScope.launch { offsetY.animateTo(0f) } },
                    onVerticalDrag = { change, dragAmount -> change.consume(); val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f); coroutineScope.launch { offsetY.snapTo(newOffset) } }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {

            // ── TOP BAR ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Collapse", tint = SpotifyWhite, modifier = Modifier.size(32.dp)) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp) // ✅ Adds weight to prevent pushing
                ) {
                    Text("PLAYING FROM YOUR LIBRARY", color = SpotifyGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) // ✅ Added truncation
                    Text(song.album ?: "Unknown Album", color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            "More",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SpotifySurface2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Album", color = SpotifyWhite) },
                            onClick = { showMenu = false; onViewAlbum() })
                        DropdownMenuItem(
                            text = { Text("View Artist", color = SpotifyWhite) },
                            onClick = { showMenu = false; onViewArtist() })

                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = SpotifyWhite) },
                            onClick = { showMenu = false; onAddToPlaylist() })
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = SpotifyWhite) },
                            onClick = { showMenu = false; onAddToQueue() })

                        if (isOnline) {
                            DropdownMenuItem(
                                text = { Text("Download Song", color = SpotifyWhite) },
                                onClick = { showMenu = false; onDownload() })
                        } else {
                            // ✅ Added Edit placeholder for Offline songs
                            DropdownMenuItem(text = {
                                Text(
                                    "Edit Song Info",
                                    color = SpotifyWhite
                                )
                            }, onClick = { showMenu = false; /* TODO: Implement Edit */ })
                            DropdownMenuItem(text = {
                                Text(
                                    "Delete Song",
                                    color = Color(0xFFFF5555)
                                )
                            }, onClick = { showMenu = false; onDelete() })
                        }
                    }
                }
            }

            // ── DYNAMIC MIDDLE SECTION: ALBUM ART vs LYRICS ────────────────
            Crossfade(targetState = showLyricsView, animationSpec = tween(400), modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), label = "LyricsTransition") { isLyricsMode ->
                if (isLyricsMode) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = 64.dp, cornerRadius = 8.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = SpotifyGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) SpotifyGreen else SpotifyWhite) }
                        }
                        SyncedLyricsView(lyricsText = lyrics, progress = progress, onSeek = onSeek, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(SpotifySurface2), contentAlignment = Alignment.Center) {
                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = null, cornerRadius = 12.dp)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(song.artist, color = SpotifyGray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) SpotifyGreen else SpotifyWhite, modifier = Modifier.size(26.dp)) }
                        }
                    }
                }
            }

            // ── BOTTOM CONTROLS SECTION ───────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                var isDragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableStateOf(0f) }
                var dragPreviewTime by remember { mutableStateOf(0L) }

                val animatedFraction by animateFloatAsState(targetValue = if (isDragging) dragFraction else rawFraction, animationSpec = tween(durationMillis = if (isDragging) 0 else 100), label = "progressAnim")
                val thumbScale by animateFloatAsState(targetValue = if (isDragging) 1f else 0f, animationSpec = tween(durationMillis = 150), label = "thumbScale")

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f); dragPreviewTime = (dragFraction * duration).toLong(); isDragging = true },
                                onHorizontalDrag = { _, dragAmount -> dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f); dragPreviewTime = (dragFraction * duration).toLong() },
                                onDragEnd = { onSeek((dragFraction * duration).toLong().coerceIn(0L, duration)); isDragging = false },
                                onDragCancel = { isDragging = false }
                            )
                        }
                        .pointerInput(duration) { detectTapGestures { offset -> val tapped = (offset.x / size.width).coerceIn(0f, 1f); dragFraction = tapped; onSeek((tapped * duration).toLong()) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val trackWidthDp = this.maxWidth
                    Box(modifier = Modifier.fillMaxWidth().height(if (isDragging) 5.dp else 4.dp).align(Alignment.Center).clip(RoundedCornerShape(50)).background(SpotifyLightGray))
                    Box(modifier = Modifier.fillMaxWidth(animatedFraction).height(if (isDragging) 5.dp else 4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(50)).background(SpotifyGreen))
                    Box(modifier = Modifier.size(14.dp).align(Alignment.CenterStart).offset(x = (trackWidthDp * animatedFraction) - 7.dp).scale(thumbScale).clip(CircleShape).background(SpotifyWhite).shadow(elevation = 3.dp, shape = CircleShape))
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatDuration(if (isDragging) dragPreviewTime else progress), color = if (isDragging) SpotifyGreen else SpotifyGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (isDragging) Text(text = "Seeking to: ${formatDuration(dragPreviewTime)}", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = if (duration > 0) formatDuration(duration) else "0:00", color = SpotifyGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (shuffleMode) SpotifyGreen else SpotifyGray, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = SpotifyWhite, modifier = Modifier.size(38.dp)) }
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(SpotifyWhite), contentAlignment = Alignment.Center) { IconButton(onClick = onTogglePlayPause) { Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = SpotifyBlack, modifier = Modifier.size(38.dp)) } }
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = SpotifyWhite, modifier = Modifier.size(38.dp)) }
                    IconButton(onClick = onToggleRepeat) { Icon(imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray, modifier = Modifier.size(22.dp)) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSleepTimer = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            "Sleep Timer",
                            tint = if (isSleepTimerActive) SpotifyGreen else SpotifyGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showLyricsView = !showLyricsView; if (showLyricsView) onLoadLyrics() }) { Icon(imageVector = Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = if (showLyricsView) SpotifyGreen else SpotifyWhite, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { showQueueSheet = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = SpotifyWhite, modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }

    // Render the sleep timer at the bottom of the Box
    if (showSleepTimer) {
        SleepTimerSheet(
            isTimerActive = isSleepTimerActive,
            sleepTimerRemainingMs = sleepTimerRemainingMs, // ✅ PASSED TO SHEET
            sleepTimerTracksRemaining = sleepTimerTracksRemaining,
            onStartTimerTime = onStartSleepTimerTime,
            onStartTimerTracks = onStartSleepTimerTracks,
            onStartTimerQueue = onStartSleepTimerQueue,
            onCancelTimer = onCancelSleepTimer,
            onDismiss = { showSleepTimer = false }
        )
    }

    if (showQueueSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = sheetState, containerColor = SpotifySurface, dragHandle = { BottomSheetDefaults.DragHandle(color = SpotifyGray) }) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                QueueContent(queue = queue, currentSong = song, onSongClick = onQueueItemClick, onSongRemove = onQueueItemRemove, onSongsRemove = onQueueItemsRemove, onSongReorder = onQueueItemReorder, onClearQueue = onClearQueue, isFullyExpanded = sheetState.currentValue == SheetValue.Expanded)
            }
        }
    }
}

// ─── Downstream Queueing logic and Lyrics ─────────
@Composable
fun SyncedLyricsView(lyricsText: String?, progress: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val lyricsLines = remember(lyricsText) { parseLrc(lyricsText) }
    val listState = rememberLazyListState()

    if (lyricsLines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = lyricsText ?: "No lyrics available.", color = SpotifyGray, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
        return
    }

    val activeIndex = lyricsLines.indexOfLast { it.startTimeMs <= progress }.coerceAtLeast(0)
    val isUserScrolling = listState.isScrollInProgress

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !isUserScrolling) {
            listState.animateScrollToItem(index = maxOf(0, activeIndex - 3))
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp)) {
        itemsIndexed(lyricsLines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text.ifEmpty { "♪" }, color = if (isActive) SpotifyWhite else SpotifyGray, fontSize = if (isActive) 24.sp else 20.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.startTimeMs) }.padding(vertical = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueContent(
    queue: List<Song>, currentSong: Song, onSongClick: (Song) -> Unit,
    onSongRemove: (Song) -> Unit, onSongsRemove: (Set<Song>) -> Unit,
    onSongReorder: (Int, Int) -> Unit, onClearQueue: () -> Unit, isFullyExpanded: Boolean
) {
    val listState = rememberLazyListState()
    var localQueue by remember { mutableStateOf(queue) }

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(queue) {
        if (draggedItemIndex == null) localQueue = queue
    }

    // Core unified index tracking
    val currentIdx = localQueue.indexOfFirst { it.id == currentSong.id }
    // ✅ ADDED: Split the queue into selectable sections
    val historySongs = remember(localQueue, currentIdx) { localQueue.take(maxOf(0, currentIdx)) }
    val upcomingSongs = remember(localQueue, currentIdx) {
        if (currentIdx >= 0 && currentIdx < localQueue.size - 1) localQueue.drop(currentIdx + 1) else emptyList()
    }
    val allSelectableSongs = remember(localQueue, currentSong) { localQueue.filter { it.id != currentSong.id } }
    // ✅ ADDED: State for the dropdown menu
    var showSelectMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (currentIdx >= 0) {
            // Scroll to currentIdx - 1 so the "Now Playing" header
            // and a previous song are visible right above it
            listState.scrollToItem(maxOf(0, currentIdx - 1))
        }
    }

    LaunchedEffect(isFullyExpanded) {
        if (!isFullyExpanded) {
            isSelectionMode = false; selectedSongs = emptySet()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .pointerInput(isSelectionMode) {
                    if (isSelectionMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                            val keyStr = item?.key as? String
                            if (keyStr?.startsWith("song_") == true) {
                                val idxStr = keyStr.removePrefix("song_")
                                val idx = localQueue.indexOfFirst { it.id.toString() == idxStr }
                                if (idx >= 0 && idx != currentIdx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggedItemIndex = idx
                                    initialDraggedIndex = idx
                                    draggedDistance = 0f
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggedDistance += dragAmount.y
                            val draggedIdx = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                            val itemHeight = listState.layoutInfo.visibleItemsInfo.find { it.key == "song_${localQueue[draggedIdx].id}" }?.size ?: 150
                            if (itemHeight == 0) return@detectDragGesturesAfterLongPress

                            val targetDelta = (draggedDistance / itemHeight).toInt()
                            if (targetDelta != 0) {
                                var targetIdx = (draggedIdx + targetDelta).coerceIn(0, localQueue.size - 1)
                                if (targetIdx == currentIdx) {
                                    targetIdx += if (targetDelta > 0) 1 else -1
                                    targetIdx = targetIdx.coerceIn(0, localQueue.size - 1)
                                }

                                if (draggedIdx != targetIdx && targetIdx != currentIdx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newList = localQueue.toMutableList()
                                    val item = newList.removeAt(draggedIdx)
                                    newList.add(targetIdx, item)
                                    localQueue = newList
                                    draggedItemIndex = targetIdx
                                    draggedDistance -= (targetDelta * itemHeight)
                                }
                            }

                            val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                            val scrollThreshold = viewportHeight * 0.15f
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                            if (lastVisible != null && change.position.y > lastVisible.offset + lastVisible.size - scrollThreshold) {
                                coroutineScope.launch { listState.scrollToItem((lastVisible.index + 1).coerceAtMost(localQueue.size - 1)) }
                            } else if (firstVisible != null && change.position.y < firstVisible.offset + scrollThreshold) {
                                coroutineScope.launch { listState.scrollToItem((firstVisible.index - 1).coerceAtLeast(0)) }
                            }
                        },
                        onDragEnd = {
                            if (initialDraggedIndex != null && draggedItemIndex != null && initialDraggedIndex != draggedItemIndex) {
                                onSongReorder(initialDraggedIndex!!, draggedItemIndex!!)
                            }
                            draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f
                        },
                        onDragCancel = {
                            draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f; localQueue = queue
                        }
                    )
                }
        ) {
            // ✅ Unified rendering loop
            itemsIndexed(items = localQueue, key = { _, song -> "song_${song.id}" }) { index, song ->
                val isPlaying = index == currentIdx
                val isDragged = draggedItemIndex == index
                val isSelected = selectedSongs.contains(song)
                val elevation by animateFloatAsState(if (isDragged) 8f else 0f, label = "elevation")
                val scale by animateFloatAsState(if (isDragged) 1.03f else 1f, label = "scale")
                val offsetY = if (isDragged) draggedDistance else 0f
                val zIndex = if (isDragged) 1f else 0f

                Column(modifier = Modifier.fillMaxWidth().zIndex(zIndex)) {

                    // 1. Headers (These sit static above specific indices to anchor sections)
                    if (index == 0 && currentIdx > 0) {
                        Text("Previously Played", color = SpotifyGray, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                    }
                    if (index == currentIdx) {
                        Text("Now Playing", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                    }
                    if (index == currentIdx + 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Next In Queue", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (isFullyExpanded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectionMode) {
                                        // ✅ FIXED: New Dropdown Menu for Selection Options
                                        Box {
                                            Text(
                                                text = "Select Options ▼",
                                                color = SpotifyGreen,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.clickable { showSelectMenu = true }
                                            )
                                            DropdownMenu(
                                                expanded = showSelectMenu,
                                                onDismissRequest = { showSelectMenu = false },
                                                modifier = Modifier.background(SpotifySurface2)
                                            ) {
                                                if (upcomingSongs.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("All Upcoming", color = SpotifyWhite) },
                                                        onClick = { selectedSongs = selectedSongs + upcomingSongs; showSelectMenu = false }
                                                    )
                                                }
                                                if (historySongs.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("All Previously Played", color = SpotifyWhite) },
                                                        onClick = { selectedSongs = selectedSongs + historySongs; showSelectMenu = false }
                                                    )
                                                }
                                                if (allSelectableSongs.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("Select All", color = SpotifyWhite) },
                                                        onClick = { selectedSongs = allSelectableSongs.toSet(); showSelectMenu = false }
                                                    )
                                                }
                                                if (selectedSongs.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("Deselect All", color = SpotifyGray) },
                                                        onClick = { selectedSongs = emptySet(); showSelectMenu = false }
                                                    )
                                                }
                                            }
                                        }

                                        if (selectedSongs.isNotEmpty()) {
                                            Text(
                                                text = "Remove",
                                                color = Color(0xFFFF5555), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                                modifier = Modifier.clickable {
                                                    onSongsRemove(selectedSongs)
                                                    selectedSongs = emptySet()
                                                    isSelectionMode = false
                                                }
                                            )
                                        }
                                    }
                                    if (!isSelectionMode) {
                                        Text(
                                            text = "Clear",
                                            color = Color(0xFFFF5555), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                            modifier = Modifier.clickable { onClearQueue() }
                                        )
                                    }
                                    Text(
                                        text = if (isSelectionMode) "Cancel" else "Select",
                                        color = SpotifyGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        modifier = Modifier.clickable {
                                            isSelectionMode = !isSelectionMode; if (!isSelectionMode) selectedSongs = emptySet()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. The Item Box (Graphic Layer allows it to be dragged visually up and down)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .graphicsLayer { translationY = offsetY; scaleX = scale; scaleY = scale }
                            .shadow(elevation.dp, RoundedCornerShape(8.dp))
                            .background(if (isDragged || isSelected) SpotifySurface2 else Color.Transparent)
                    ) {
                        if (isPlaying) {
                            QueueSongItem(song = song, isPlaying = true, onClick = {})
                        } else {
                            if (isSelectionMode) {
                                QueueSongItem(
                                    song = song, isPlaying = false, isHistory = index < currentIdx,
                                    isSelectionMode = true, isSelected = isSelected,
                                    onClick = { selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song }
                                )
                            } else {
                                SwipeableQueueItem(
                                    song = song,
                                    onRemove = { onSongRemove(song) },
                                    onClick = { onSongClick(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableQueueItem(song: Song, onRemove: () -> Unit, onClick: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) { onRemove(); return@rememberSwipeToDismissBoxState false }
            false
        }
    )
    LaunchedEffect(song.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled || dismissState.targetValue != SwipeToDismissBoxValue.Settled) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color(0xFFE57373).copy(alpha = 0.8f) else Color.Transparent, label = "swipeColor")
            Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
    ) { QueueSongItem(song = song, isPlaying = false, onClick = onClick) }
}

@Composable
fun QueueSongItem(song: Song, isPlaying: Boolean, isHistory: Boolean = false, isSelectionMode: Boolean = false, isSelected: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(song = song, audioUri = song.uri, isActive = isPlaying, size = 48.dp, cornerRadius = 4.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, color = if (isPlaying) SpotifyGreen else if (isHistory) SpotifyGray else SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist, color = SpotifyGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isSelectionMode) {
            Icon(imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = "Select", tint = if (isSelected) SpotifyGreen else SpotifyGray, modifier = Modifier.size(24.dp))
        } else if (isPlaying) {
            Icon(Icons.Filled.GraphicEq, contentDescription = "Playing", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        } else {
            // ✅ FIXED: Drag handles now appear on ALL non-playing queue items
            Icon(Icons.Filled.DragHandle, contentDescription = "Long press to Reorder", tint = SpotifyGray, modifier = Modifier.size(24.dp))
        }
    }
}
// 2. Replace the entire SleepTimerSheet Composable at the bottom of the file
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SleepTimerSheet(
    isTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    sleepTimerTracksRemaining: Int = -1,
    onStartTimerTime: (Int) -> Unit,
    onStartTimerTracks: (Int) -> Unit,
    onStartTimerQueue: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tab State
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Time, 1 = Tracks

    // ✅ FIXED: Modern Slider State instead of clunky LazyColumn
    var sliderValue by remember { mutableFloatStateOf(30f) }

    // Track State
    var trackCount by remember { mutableStateOf("3") }
    var selectedTrackOption by remember { mutableStateOf("current") } // current, custom, queue

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SpotifySurface2) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Sleep Timer", color = SpotifyWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            TabRow(selectedTabIndex = selectedTab, containerColor = SpotifySurface2, contentColor = SpotifyGreen) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Time", color = if (selectedTab == 0) SpotifyGreen else SpotifyGray) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Tracks", color = if (selectedTab == 1) SpotifyGreen else SpotifyGray) })
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TAB CONTENT ---
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                if (selectedTab == 0) {
                    // ✅ FIXED: Slider Design Redesign
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${sliderValue.toInt()} minutes",
                            color = SpotifyWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 5f..120f,
                            steps = 22, // Enables 5-minute jumping increments
                            colors = SliderDefaults.colors(
                                thumbColor = SpotifyWhite,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = SpotifyGray
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // TRACK PICKER
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Stop audio after:", color = SpotifyGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "current" }) {
                            RadioButton(selected = selectedTrackOption == "current", onClick = { selectedTrackOption = "current" }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = SpotifyGray))
                            Text("Current song", color = SpotifyWhite)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "custom" }) {
                            RadioButton(selected = selectedTrackOption == "custom", onClick = { selectedTrackOption = "custom" }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = SpotifyGray))
                            OutlinedTextField(
                                value = trackCount,
                                onValueChange = { trackCount = it.filter { char -> char.isDigit() }.take(3) },
                                modifier = Modifier.width(72.dp).height(52.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = SpotifyWhite),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SpotifyWhite,
                                    unfocusedTextColor = SpotifyWhite,
                                    focusedBorderColor = SpotifyGreen,
                                    unfocusedBorderColor = SpotifyGray,
                                    cursorColor = SpotifyGreen
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Text("songs", color = SpotifyWhite, modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "queue" }) {
                            RadioButton(selected = selectedTrackOption == "queue", onClick = { selectedTrackOption = "queue" }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = SpotifyGray))
                            Text("End of queue", color = SpotifyWhite)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- START / CANCEL BUTTON ---
            Button(
                onClick = {
                    if (isTimerActive) {
                        onCancelTimer()
                    } else {
                        if (selectedTab == 0) {
                            onStartTimerTime(sliderValue.toInt())
                        } else {
                            when (selectedTrackOption) {
                                "current" -> onStartTimerTracks(1)
                                "custom" -> onStartTimerTracks(trackCount.toIntOrNull() ?: 3)
                                "queue" -> onStartTimerQueue()
                            }
                        }
                        onDismiss() // Close only when actually starting the timer
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTimerActive) Color(0xFF333333) else SpotifyGreen,
                    contentColor = if (isTimerActive) Color(0xFFFF5555) else SpotifyBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                val buttonText = if (isTimerActive) {
                    if (sleepTimerRemainingMs > 0) {
                        val m = (sleepTimerRemainingMs / 1000) / 60
                        val s = (sleepTimerRemainingMs / 1000) % 60
                        String.format(LocalLocale.current.platformLocale, "Cancel Timer (%02d:%02d)", m, s)
                    } else if (sleepTimerTracksRemaining >= 0) {
                        "Cancel Timer ($sleepTimerTracksRemaining tracks left)"
                    } else {
                        "Cancel Sleep Timer"
                    }
                } else {
                    "Start Timer"
                }

                Text(text = buttonText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}