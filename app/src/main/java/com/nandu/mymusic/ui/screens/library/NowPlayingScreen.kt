package com.nandu.mymusic.ui.screens.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.snapshotFlow
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
import com.nandu.mymusic.ui.components.NowPlayingBackdrop
import com.nandu.mymusic.utils.parseLrc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalLocale
import com.nandu.mymusic.ui.theme.NpFallbackGradient
import com.nandu.mymusic.ui.theme.appleColors

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
    onQueueItemReorder: (Int, Int) -> Unit,
    onClearQueue      : () -> Unit = {},
    onAddToPlaylist  : () -> Unit = {},
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
    val C = appleColors()
    val rawFraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isOnline = song.uri.toString().startsWith("http")

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = offsetY.value; alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val raw = offsetY.value + dragAmount
                        // Rubber-band resistance when dragging above the top edge
                        val newOffset = if (raw < 0f) raw * 0.35f else raw
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        coroutineScope.launch {
                            if (offsetY.value > 400f || velocity > 1250f) {
                                // Velocity handoff: flick dismisses with momentum
                                offsetY.animateTo(size.height.toFloat(), tween(220))
                                onBack()
                            } else {
                                offsetY.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { offsetY.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
                    }
                )
            }
    ) {
        // Art-derived dynamic gradient backdrop (Apple Music style)
        NowPlayingBackdrop(song = song)

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {

            // ── TOP BAR ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Collapse", tint = C.accent, modifier = Modifier.size(32.dp)) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp) // ✅ Adds weight to prevent pushing
                ) {
                    Text("PLAYING FROM YOUR LIBRARY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) // ✅ Added truncation
                    Text(song.album ?: "Unknown Album", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            "More",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(C.cardElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Album", color = C.label) },
                            onClick = { showMenu = false; onViewAlbum() })
                        DropdownMenuItem(
                            text = { Text("View Artist", color = C.label) },
                            onClick = { showMenu = false; onViewArtist() })

                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = C.label) },
                            onClick = { showMenu = false; onAddToPlaylist() })

                        if (isOnline) {
                            DropdownMenuItem(
                                text = { Text("Download Song", color = C.label) },
                                onClick = { showMenu = false; onDownload() })
                        } else {
                            // ✅ Added Edit placeholder for Offline songs
                            DropdownMenuItem(text = {
                                Text(
                                    "Edit Song Info",
                                    color = C.label
                                )
                            }, onClick = { showMenu = false; /* TODO: Implement Edit */ })
                            DropdownMenuItem(text = {
                                Text(
                                    "Delete Song",
                                    color = C.destructive
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
                                Text(song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist ?: "Unknown Artist", color = C.accent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) C.accent else Color.White) }
                        }
                        SyncedLyricsView(lyricsText = lyrics, progress = progress, onSeek = onSeek, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = null, cornerRadius = 12.dp)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(song.artist ?: "Unknown Artist", color = C.accent, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) C.accent else Color.White, modifier = Modifier.size(26.dp)) }
                        }
                    }
                }
            }

            // ── BOTTOM CONTROLS SECTION ───────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                var isDragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableStateOf(0f) }
                var dragPreviewTime by remember { mutableStateOf(0L) }

                val animatedFraction by animateFloatAsState(targetValue = if (isDragging) dragFraction else rawFraction, animationSpec = tween(durationMillis = if (isDragging) 0 else 50, easing = androidx.compose.animation.core.FastOutLinearInEasing), label = "progressAnim")
                val thumbScale by animateFloatAsState(targetValue = if (isDragging) 1f else 0f, animationSpec = tween(durationMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "thumbScale")

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
                    // Straight Apple-style scrubber track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                    Box(modifier = Modifier.size(14.dp).align(Alignment.CenterStart).offset(x = (trackWidthDp * animatedFraction) - 7.dp).scale(thumbScale).clip(CircleShape).background(Color.White).shadow(elevation = 3.dp, shape = CircleShape))
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Monospaced digits (tnum) like iOS
                    val timeStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    Text(
                        text = formatDuration(if (isDragging) dragPreviewTime else progress),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = timeStyle
                    )
                    Text(
                        text = if (duration > 0) "-" + formatDuration(duration - progress) else "-0:00",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = timeStyle
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (shuffleMode) C.accent else Color.White, modifier = Modifier.size(22.dp)) }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .clickable(onClick = onPrevious),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Box(
                        modifier = Modifier.size(76.dp).clip(CircleShape)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) { Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(48.dp)) }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .clickable(onClick = onNext),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = onToggleRepeat) { Icon(imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) C.accent else Color.White, modifier = Modifier.size(22.dp)) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSleepTimer = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            "Sleep Timer",
                            tint = if (isSleepTimerActive) C.accent else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showLyricsView = !showLyricsView; if (showLyricsView) onLoadLyrics() }) { Icon(imageVector = Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = if (showLyricsView) C.accent else Color.White, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { showQueueSheet = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = Color.White, modifier = Modifier.size(22.dp)) }
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
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = sheetState, containerColor = C.card, dragHandle = { BottomSheetDefaults.DragHandle(color = C.secondaryLabel) }) {
            QueueContent(
                queue = queue,
                currentSong = song,
                isPlaying = isPlaying,
                onSongClick = onQueueItemClick,
                onSongRemove = onQueueItemRemove,
                onSongReorder = onQueueItemReorder,
                onClearQueue = onClearQueue,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            )
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
                text = lyricsText ?: "No lyrics available.", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.verticalScroll(rememberScrollState())
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
                text = line.text.ifEmpty { "♪" }, color = if (isActive) Color.White else Color.White.copy(alpha = 0.45f), fontSize = if (isActive) 24.sp else 20.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.startTimeMs) }.padding(vertical = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueContent(
    queue: List<Song>,
    currentSong: Song,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongRemove: (Song) -> Unit,
    onSongReorder: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var localQueue by remember { mutableStateOf(queue) }
    val C = appleColors()

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Sync the local copy from the ViewModel — but never while a drag is in progress,
    // otherwise the row being held would jump around.
    LaunchedEffect(queue) {
        if (draggedItemIndex == null) localQueue = queue
    }

    // Unified queue indices (absolute positions inside the full queue list)
    val currentIdx = localQueue.indexOfFirst { it.id == currentSong.id }
    val historySongs = remember(localQueue, currentIdx) { if (currentIdx > 0) localQueue.take(currentIdx) else emptyList() }
    val upcomingSongs = remember(localQueue, currentIdx) {
        when {
            currentIdx in 0 until localQueue.size - 1 -> localQueue.drop(currentIdx + 1)
            // Current song not in the queue snapshot yet — show everything as upcoming,
            // but never duplicate the NOW PLAYING row.
            currentIdx < 0 -> localQueue.filter { it.id != currentSong.id }
            else -> emptyList()
        }
    }

    // Auto-center NOW PLAYING once per open; re-fires if queue data lands after first composition.
    var hasCentered by remember { mutableStateOf(false) }
    LaunchedEffect(historySongs.size, currentSong.id) {
        if (hasCentered) return@LaunchedEffect
        // NOW PLAYING list index: history header + history items, or 0 when there is no history block
        val nowPlayingIndex = if (historySongs.isNotEmpty()) 1 + historySongs.size else 0
        // Phase 1: instant jump so the row is on screen during the sheet's enter animation
        listState.scrollToItem(nowPlayingIndex)
        // Phase 2: wait for the viewport height to settle (ModalBottomSheet expands ~300ms)
        withTimeoutOrNull(800) {
            snapshotFlow { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { delay(80) } // debounce — resolves once height is stable for 80ms
        }
        if (!listState.isScrollInProgress) {
            val li = listState.layoutInfo
            val viewportHeight = li.viewportEndOffset - li.viewportStartOffset
            val itemInfo = li.visibleItemsInfo.firstOrNull { it.key == "now_playing" }
            if (itemInfo != null) {
                // scrollToItem's offset is ABSOLUTE: final item-top position = -scrollOffset.
                // Center the item: top at viewportH/2 - size/2  →  scrollOffset = size/2 - viewportH/2
                val centerOffset = itemInfo.size / 2 - viewportHeight / 2
                listState.animateScrollToItem(nowPlayingIndex, scrollOffset = centerOffset)
            }
        }
        hasCentered = true
    }

    Column(modifier = modifier.fillMaxWidth().background(C.card)) {

        // ── Sheet header: title + count + Clear ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Playing Next", color = C.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        upcomingSongs.isNotEmpty() -> "${upcomingSongs.size} more song${if (upcomingSongs.size > 1) "s" else ""}"
                        historySongs.isNotEmpty() -> "End of queue"
                        else -> "Nothing next"
                    },
                    color = C.secondaryLabel, fontSize = 13.sp
                )
            }
            if (upcomingSongs.isNotEmpty() || historySongs.isNotEmpty()) {
                Text(
                    text = "Clear",
                    color = C.destructive,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onClearQueue() }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        HorizontalDivider(color = C.separator, thickness = 0.5.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                            val keyStr = item?.key as? String
                            val idx = when {
                                keyStr?.startsWith("up_") == true || keyStr?.startsWith("hist_") == true -> {
                                    val idStr = keyStr!!.substringAfter('_')
                                    if (idStr.isNotEmpty() && idStr.all { it.isDigit() })
                                        localQueue.indexOfFirst { it.id.toString() == idStr }
                                    else -1
                                }
                                else -> -1
                            }
                            if (idx >= 0 && idx != currentIdx) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedItemIndex = idx
                                initialDraggedIndex = idx
                                draggedDistance = 0f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggedDistance += dragAmount.y
                            val draggedIdx = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                            if (draggedIdx < 0 || draggedIdx >= localQueue.size) return@detectDragGesturesAfterLongPress
                            val draggedKey = (if (draggedIdx < currentIdx) "hist_" else "up_") + localQueue[draggedIdx].id
                            val itemHeight = listState.layoutInfo.visibleItemsInfo.find { it.key == draggedKey }?.size ?: 150
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
                                coroutineScope.launch { listState.scrollToItem((lastVisible.index + 1).coerceAtMost(listState.layoutInfo.totalItemsCount - 1)) }
                            } else if (firstVisible != null && change.position.y < firstVisible.offset + scrollThreshold) {
                                coroutineScope.launch { listState.scrollToItem((firstVisible.index - 1).coerceAtLeast(0)) }
                            }
                        },
                        onDragEnd = {
                            val from = initialDraggedIndex
                            val to = draggedItemIndex
                            if (from != null && to != null && from != to && from in localQueue.indices && to in localQueue.indices) {
                                onSongReorder(from, to)
                            }
                            draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f
                        },
                        onDragCancel = {
                            draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f; localQueue = queue
                        }
                    )
                },
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {

            // ── Played (at the top) ──
            if (historySongs.isNotEmpty()) {
                item(key = "history_header") {
                    Text(
                        "Played",
                        color = C.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)
                    )
                }
                itemsIndexed(historySongs, key = { _, song -> "hist_${song.id}" }) { _, song ->
                    SwipeableQueueItem(
                        song = song,
                        isHistory = true,
                        isDragged = draggedItemIndex != null && localQueue.getOrNull(draggedItemIndex ?: -1)?.id == song.id,
                        onRemove = { onSongRemove(song) },
                        onClick = { onSongClick(song) }
                    )
                }
            }

            // ── Now Playing (in the middle) ──
            item(key = "now_playing") {
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.cardElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "NOW PLAYING",
                        color = C.secondaryLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    QueueSongItem(song = currentSong, isPlaying = true, isAnimating = isPlaying, onClick = {})
                }
            }

            // ── Next in queue (at the bottom) ──
            item(key = "up_next_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Next in queue", color = C.label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (upcomingSongs.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text("${upcomingSongs.size}", color = C.secondaryLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (upcomingSongs.isEmpty()) {
                item(key = "up_next_empty") {
                    Text(
                        "Nothing in queue — add songs from your library",
                        color = C.secondaryLabel,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            itemsIndexed(upcomingSongs, key = { _, song -> "up_${song.id}" }) { _, song ->
                SwipeableQueueItem(
                    song = song,
                    isHistory = false,
                    isDragged = draggedItemIndex != null && localQueue.getOrNull(draggedItemIndex ?: -1)?.id == song.id,
                    onRemove = { onSongRemove(song) },
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableQueueItem(song: Song, isHistory: Boolean = false, isDragged: Boolean = false, onRemove: () -> Unit, onClick: () -> Unit) {
    val C = appleColors()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                return@rememberSwipeToDismissBoxState true
            }
            false
        }
    )
    val scale by animateFloatAsState(targetValue = if (isDragged) 1.04f else 1f, animationSpec = tween(150), label = "dragScale")
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) C.destructive else Color.Transparent, label = "swipeColor")
            Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
    ) {
        Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (isDragged) 12f else 0f; shape = RoundedCornerShape(8.dp); clip = false }) {
            QueueSongItem(song = song, isPlaying = false, isHistory = isHistory, isDragged = isDragged, onClick = onClick)
        }
    }
}

@Composable
fun QueueSongItem(
    song: Song,
    isPlaying: Boolean,
    isHistory: Boolean = false,
    isAnimating: Boolean = false,
    isDragged: Boolean = false,
    onClick: () -> Unit
) {
    val C = appleColors()
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(song = song, audioUri = song.uri, isActive = isPlaying, size = 48.dp, cornerRadius = 6.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title ?: "Unknown Title", color = if (isPlaying) C.accent else if (isHistory) C.tertiaryLabel else C.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist ?: "Unknown Artist", color = if (isHistory) C.tertiaryLabel else C.secondaryLabel, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isPlaying) {
            EqualizerBars(isAnimating = isAnimating, color = C.accent, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Filled.DragHandle, contentDescription = "Long press to reorder", tint = if (isHistory) C.tertiaryLabel else C.secondaryLabel, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun EqualizerBars(isAnimating: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = List(3) { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 320 + index * 140, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eqBar$index"
        )
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        bars.forEach { bar ->
            val h = if (isAnimating) (5f + 13f * bar.value).dp else 10.dp
            Box(modifier = Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp)).background(color))
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

    val C = appleColors()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = C.cardElevated) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Sleep Timer", color = C.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            TabRow(selectedTabIndex = selectedTab, containerColor = C.cardElevated, contentColor = C.accent) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Time", color = if (selectedTab == 0) C.accent else C.secondaryLabel) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Tracks", color = if (selectedTab == 1) C.accent else C.secondaryLabel) })
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
                            color = C.label,
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
                                thumbColor = C.label,
                                activeTrackColor = C.accent,
                                inactiveTrackColor = C.secondaryLabel
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
                        Text("Stop audio after:", color = C.secondaryLabel, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "current" }) {
                            RadioButton(selected = selectedTrackOption == "current", onClick = { selectedTrackOption = "current" }, colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.secondaryLabel))
                            Text("Current song", color = C.label)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "custom" }) {
                            RadioButton(selected = selectedTrackOption == "custom", onClick = { selectedTrackOption = "custom" }, colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.secondaryLabel))
                            OutlinedTextField(
                                value = trackCount,
                                onValueChange = { trackCount = it.filter { char -> char.isDigit() }.take(3) },
                                modifier = Modifier.width(72.dp).height(52.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = C.label),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = C.label,
                                    unfocusedTextColor = C.label,
                                    focusedBorderColor = C.accent,
                                    unfocusedBorderColor = C.secondaryLabel,
                                    cursorColor = C.accent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Text("songs", color = C.label, modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedTrackOption = "queue" }) {
                            RadioButton(selected = selectedTrackOption == "queue", onClick = { selectedTrackOption = "queue" }, colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.secondaryLabel))
                            Text("End of queue", color = C.label)
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
                    containerColor = if (isTimerActive) C.cardElevated else C.accent,
                    contentColor = if (isTimerActive) C.destructive else Color.White
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