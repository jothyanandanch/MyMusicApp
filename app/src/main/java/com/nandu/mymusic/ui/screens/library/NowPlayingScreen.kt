package com.nandu.mymusic.ui.screens.library

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.media3.common.Player
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.components.AppleMenu
import com.nandu.mymusic.ui.components.AppleMenuAction
import com.nandu.mymusic.ui.components.AudioRoute
import com.nandu.mymusic.ui.components.MediaOutputSheet
import com.nandu.mymusic.ui.components.NowPlayingBackdrop
import com.nandu.mymusic.ui.components.currentAudioRoute
import com.nandu.mymusic.utils.parseLrc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nandu.mymusic.ui.theme.appleColors

/**
 * Apple Music Now Playing replica:
 * - Two-page vertical pager — page 0: player; page 1: "Playing Next" queue.
 * - Top bar: collapse | PLAYING FROM {source} | favorite + iOS popover menu
 *   (Sleep Timer with live countdown, View Album/Artist, Add to Playlist,
 *   Download/Edit/Delete).
 * - Long-press drag reordering on the queue, pull-down header to collapse,
 *   Apple Music Autoplay (∞) + Clear in the queue header.
 * - Dynamic output button (bottom-left) shows the active route icon.
 */
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
    nowPlayingSource : String? = null,
    isSleepTimerActive: Boolean = false,
    sleepTimerRemainingMs: Long = 0L,
    sleepTimerTracksRemaining: Int = -1,
    isAutoplayEnabled: Boolean = false,
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
    onClearQueue     : () -> Unit = {},
    onToggleAutoplay : () -> Unit = {},
    onAddToPlaylist  : () -> Unit = {},
    onLoadLyrics     : () -> Unit = {},
    onDelete         : () -> Unit = {},
    onViewAlbum      : () -> Unit = {},
    onViewArtist     : () -> Unit = {},
    onDownload       : () -> Unit = {},
    onStartSleepTimerTime: (Int) -> Unit = {},
    onStartSleepTimerTracks: (Int) -> Unit = {},
    onStartSleepTimerQueueEnd: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {}
) {
    var showSleepTimer by remember { mutableStateOf(false) }
    var selectedTimerOption by remember { mutableStateOf<String?>(null) }
    var showOutputSheet by remember { mutableStateOf(false) }
    val C = appleColors()
    val sleepStatus = sleepTimerStatusLabel(isSleepTimerActive, sleepTimerRemainingMs, sleepTimerTracksRemaining)
    // Clear the checkmark when the timer expires or is cancelled externally
    LaunchedEffect(isSleepTimerActive) { if (!isSleepTimerActive) selectedTimerOption = null }
    val rawFraction = if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var showLyricsView by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isOnline = song.uri.toString().startsWith("http")
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    val screenH = remember { context.resources.displayMetrics.heightPixels.toFloat() }

    // ── Inline queue state (shared by the pager's queue page) ──
    val queueListState = rememberLazyListState()
    var localQueue by remember { mutableStateOf(queue) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    // Hold the local preview order while a drop is pending (draggedItemIndex
    // non-null) so the row never appears to fly back before animating into
    // its slot — fixes the rubber-band "returning to position" on drop.
    LaunchedEffect(queue) { if (draggedItemIndex == null) localQueue = queue }
    val currentIdx = localQueue.indexOfFirst { it.id == song.id }
    val upcomingSongs = remember(localQueue, currentIdx) {
        when {
            currentIdx in 0 until localQueue.size - 1 -> localQueue.drop(currentIdx + 1)
            currentIdx < 0 -> localQueue.filter { it.id != song.id }
            else -> emptyList()
        }
    }

    // Two-page vertical pager: 0 = player, 1 = Playing Next (exact Apple Music).
    val pagerState = rememberPagerState(pageCount = { 2 })
    var hasCenteredQueue by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && !hasCenteredQueue) {
            queueListState.scrollToItem(0)
            hasCenteredQueue = true
        }
    }
    // Queue-page pull-down (collapses back to the player like iOS).
    val pagerDragOffset = remember { Animatable(0f) }

    /** Drag-to-dismiss gesture (top bar only, like Apple — never fights the scroll). */
    val dismissDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val raw = offsetY.value + dragAmount
                val newOffset = if (raw < 0f) raw * 0.35f else raw
                coroutineScope.launch { offsetY.snapTo(newOffset) }
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().y
                coroutineScope.launch {
                    if (offsetY.value > 400f || velocity > 1250f) {
                        offsetY.animateTo(screenH, tween(220))
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

    // Artwork variant: only intercept DOWNWARD drags (dismiss). Upward swipes
    // remain unconsumed so the VerticalPager can take them (swipe up → queue).
    val artworkDismissDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val raw = offsetY.value + dragAmount
                val newOffset = if (raw < 0f) raw * 0.35f else raw
                coroutineScope.launch { offsetY.snapTo(newOffset) }
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().y
                coroutineScope.launch {
                    if (offsetY.value > 400f || velocity > 1250f) {
                        offsetY.animateTo(screenH, tween(220))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .graphicsLayer { translationY = offsetY.value; alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) }
    ) {
        // Art-derived dynamic gradient backdrop (Apple Music style)
        NowPlayingBackdrop(song = song)

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {

            // ── TOP BAR: chevron | PLAYING FROM {source} | heart + menu ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dismissDrag)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Collapse", tint = C.accent, modifier = Modifier.size(32.dp)) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text("PLAYING FROM", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nowPlayingSource?.takeIf { it.isNotBlank() } ?: song.album ?: "Your Library", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                            tint = if (isFavorite) C.accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        AppleMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            actions = buildList {
                                add(AppleMenuAction(
                                    label = if (sleepStatus != null) "Sleep Timer — $sleepStatus" else "Sleep Timer",
                                    icon = Icons.Filled.Bedtime
                                ) { showSleepTimer = true })
                                add(AppleMenuAction("View Album", Icons.Filled.Album) { onViewAlbum() })
                                add(AppleMenuAction("View Artist", Icons.Filled.Person) { onViewArtist() })
                                add(AppleMenuAction("Add to Playlist", Icons.Filled.PlaylistAdd) { onAddToPlaylist() })
                                if (isOnline) {
                                    add(AppleMenuAction("Download Song", Icons.Filled.Download) { onDownload() })
                                } else {
                                    add(AppleMenuAction("Delete Song", Icons.Filled.Delete, isDestructive = true) { onDelete() })
                                }
                            }
                        )
                    }
                }
            }

            // ── TWO-PAGE VERTICAL PAGER (player ⇄ Playing Next) ──
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                if (page == 0) {
                    // ── PAGE 0: PLAYER ──
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Middle section swaps art ⇄ lyrics; controls stay pinned below.
                        Crossfade(targetState = showLyricsView, animationSpec = tween(400), modifier = Modifier.fillMaxWidth().weight(1f), label = "LyricsTransition") { isLyricsMode ->
                            if (isLyricsMode) {
                                // Full lyrics takeover: shrunken art header + big synced lines
                                Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AlbumArt(song = song, audioUri = song.uri, isActive = true, size = 44.dp, cornerRadius = 6.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist ?: "Unknown Artist", color = C.accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = onToggleFavorite) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) C.accent else Color.White, modifier = Modifier.size(22.dp)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    SyncedLyricsView(lyricsText = lyrics, progress = progress, onSeek = onSeek, modifier = Modifier.fillMaxSize())
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Square art that fits both width and remaining height.
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight(), contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .fillMaxSize()
                                                .then(artworkDismissDrag)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = null, cornerRadius = 12.dp, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(song.artist ?: "Unknown Artist", color = C.accent, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Pinned controls (always visible on the player page).
                        // Apple order: scrubber → transport → volume → bottom row.
                        ScrubberSection(onSeek = onSeek, rawFraction = rawFraction, progress = progress, duration = duration)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onPrevious),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp)) }
                            Box(
                                modifier = Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onTogglePlayPause),
                                contentAlignment = Alignment.Center
                            ) { Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(48.dp)) }
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onNext),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp)) }
                        }
                        VolumeSlider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // Dynamic output button — icon reflects the ACTIVE route
                            // (Apple Music behavior), opens the iOS-style picker.
                            var outputRoute by remember { mutableStateOf(currentAudioRoute(context)) }
                            LaunchedEffect(showOutputSheet) {
                                while (true) {
                                    outputRoute = currentAudioRoute(context)
                                    delay(1000)
                                }
                            }
                            IconButton(onClick = { showOutputSheet = true }) {
                                val (routeIcon, routeDesc) = when (outputRoute) {
                                    AudioRoute.BLUETOOTH -> Icons.Filled.BluetoothAudio to "Bluetooth"
                                    AudioRoute.WIRED -> Icons.Filled.Headphones to "Headphones"
                                    AudioRoute.SPEAKER -> Icons.Filled.VolumeUp to "Speaker"
                                }
                                Icon(routeIcon, routeDesc, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Apple's quote.bubble lyrics glyph (bottom-right pair)
                                IconButton(onClick = { showLyricsView = !showLyricsView; if (showLyricsView) onLoadLyrics() }) {
                                    Icon(imageVector = Icons.Filled.ChatBubbleOutline, contentDescription = "Lyrics", tint = if (showLyricsView) C.accent else Color.White, modifier = Modifier.size(22.dp))
                                }
                                // Apple's list.bullet queue glyph (plain bulleted list, no note)
                                IconButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }) {
                                    Icon(Icons.Filled.FormatListBulleted, "Playing Next", tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                } else {
                    // -- PAGE 1: PLAYING NEXT (full-screen queue) --
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Grabber + header: pull DOWN anywhere here to collapse back
                        // to the player — no need to scroll the list to the top first.
                        var headerPull by remember { mutableStateOf(0f) }
                        val headerDrag = Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { headerPull = 0f },
                                onVerticalDrag = { change, amount ->
                                    if (amount > 0f) {
                                        change.consume()
                                        headerPull += amount
                                        coroutineScope.launch { pagerDragOffset.snapTo(headerPull.coerceAtMost(150f)) }
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (headerPull > 36f) {
                                            pagerState.animateScrollToPage(0)
                                        }
                                        headerPull = 0f
                                        pagerDragOffset.snapTo(0f)
                                    }
                                },
                                onDragCancel = {
                                    headerPull = 0f
                                    coroutineScope.launch { pagerDragOffset.animateTo(0f, tween(180)) }
                                }
                            )
                        }
                        // Grabber, like the iOS queue page
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(headerDrag)
                                .padding(top = 6.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color.White.copy(alpha = 0.3f)))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(headerDrag)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Back to player", tint = C.accent, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Playing Next", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("From ${nowPlayingSource?.takeIf { it.isNotBlank() } ?: "your library"}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                            // ∞ Autoplay (Apple Music header-right placement)
                            IconButton(onClick = onToggleAutoplay) {
                                Icon(
                                    Icons.Filled.AllInclusive,
                                    "Autoplay",
                                    tint = if (isAutoplayEnabled) C.accent else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (upcomingSongs.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearQueue,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Clear", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        LazyColumn(
                            state = queueListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = pagerDragOffset.value * 0.15f }
                                .pointerInput(localQueue.size) {
                                    // Long-press drag reordering (Apple Music style)
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val item = queueListState.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                            val keyStr = item?.key as? String
                                            val idx = when {
                                                keyStr?.startsWith("up_") == true -> {
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
                                            val draggedKey = "up_" + localQueue[draggedIdx].id
                                            val itemHeight = queueListState.layoutInfo.visibleItemsInfo.find { it.key == draggedKey }?.size ?: 150
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

                                            // Auto-scroll at viewport edges during drag
                                            val viewportHeight = queueListState.layoutInfo.viewportEndOffset - queueListState.layoutInfo.viewportStartOffset
                                            val scrollThreshold = viewportHeight * 0.15f
                                            val lastVisible = queueListState.layoutInfo.visibleItemsInfo.lastOrNull()
                                            val firstVisible = queueListState.layoutInfo.visibleItemsInfo.firstOrNull()
                                            if (lastVisible != null && change.position.y > lastVisible.offset + lastVisible.size - scrollThreshold) {
                                                coroutineScope.launch { queueListState.scrollToItem((lastVisible.index + 3).coerceAtMost(queueListState.layoutInfo.totalItemsCount - 1)) }
                                            } else if (firstVisible != null && change.position.y < firstVisible.offset + scrollThreshold) {
                                                coroutineScope.launch { queueListState.scrollToItem((firstVisible.index - 1).coerceAtLeast(0)) }
                                            }
                                        },
                                        onDragEnd = {
                                            val from = initialDraggedIndex
                                            val to = draggedItemIndex
                                            draggedItemIndex = null
                                            initialDraggedIndex = null
                                            draggedDistance = 0f
                                            if (from != null && to != null && from != to && from in localQueue.indices && to in localQueue.indices) {
                                                onQueueItemReorder(from, to)
                                            }
                                        },
                                        onDragCancel = {
                                            draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f; localQueue = queue
                                        }
                                    )
                                },
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Shuffle / Repeat pair at the head of the queue list
                            // (Apple Music layout), then the pinned current row.
                            item(key = "playing_controls") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    QueueControlButton(
                                        label = if (shuffleMode) "Shuffle On" else "Shuffle",
                                        icon = Icons.Filled.Shuffle,
                                        active = shuffleMode,
                                        onClick = onToggleShuffle,
                                        modifier = Modifier.weight(1f)
                                    )
                                    QueueControlButton(
                                        label = when (repeatMode) {
                                            Player.REPEAT_MODE_ONE -> "Repeat One"
                                            Player.REPEAT_MODE_ALL -> "Repeat All"
                                            else -> "Repeat"
                                        },
                                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                        active = repeatMode != Player.REPEAT_MODE_OFF,
                                        onClick = onToggleRepeat,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // Apple keeps the current track as the first, non-interactive
                            // queue row with the red playing indicator.
                            item(key = "cur_${song.id}") {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AlbumArt(song = song, audioUri = song.uri, isActive = true, size = 48.dp, cornerRadius = 6.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    EqualizerBars(isAnimating = isPlaying, color = C.accent, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (upcomingSongs.isEmpty()) {
                                item(key = "queue_empty") {
                                    Text(
                                        "Nothing in queue — add songs from your library",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                            itemsIndexed(upcomingSongs, key = { _, s -> "up_${s.id}" }) { _, s ->
                                InlineQueueRow(
                                    song = s,
                                    onClick = { onQueueItemClick(s) },
                                    onRemove = { onQueueItemRemove(s) },
                                    isDragging = draggedItemIndex != null && localQueue.getOrNull(draggedItemIndex ?: -1)?.id == s.id &&
                                        s.id == localQueue.getOrNull(initialDraggedIndex ?: -1)?.id
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Output picker (Apple Music style) ──
    if (showOutputSheet) {
        MediaOutputSheet(onDismiss = { showOutputSheet = false })
    }

    // ── Sleep timer sheet ──
    if (showSleepTimer) {
        SleepTimerSheet(
            isTimerActive = isSleepTimerActive,
            selectedOption = selectedTimerOption,
            liveStatus = sleepStatus,
            onSelectOption = { label ->
                when (label) {
                    "When Current Song Ends" -> { onStartSleepTimerTracks(1); selectedTimerOption = label }
                    "End of Queue" -> { onStartSleepTimerQueueEnd(); selectedTimerOption = label }
                    "5 Minutes" -> { onStartSleepTimerTime(5); selectedTimerOption = label }
                    "10 Minutes" -> { onStartSleepTimerTime(10); selectedTimerOption = label }
                    "15 Minutes" -> { onStartSleepTimerTime(15); selectedTimerOption = label }
                    "30 Minutes" -> { onStartSleepTimerTime(30); selectedTimerOption = label }
                    "45 Minutes" -> { onStartSleepTimerTime(45); selectedTimerOption = label }
                    "1 Hour" -> { onStartSleepTimerTime(60); selectedTimerOption = label }
                    "2 Hours" -> { onStartSleepTimerTime(120); selectedTimerOption = label }
                }
                showSleepTimer = false
            },
            onCancelTimer = {
                selectedTimerOption = null
                onCancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false }
        )
    }
}

/** Equal-width queue control buttons (Shuffle / Repeat) — Apple layout. */
@Composable
private fun QueueControlButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = appleColors()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (active) C.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) C.accent else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = if (active) C.accent else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Apple Music wording for the live sleep-timer subtitle:
 *   "Sleeping for 9m 53s"  /  "Sleeping until queue ends"
 * Shown in the menu label and the sheet header while a timer is active.
 */
@Composable
fun sleepTimerStatusLabel(
    active: Boolean,
    remainingMs: Long,
    tracksRemaining: Int
): String? {
    if (!active) return null
    return when {
        tracksRemaining > 0 -> "Sleeping until queue ends ($tracksRemaining left)"
        tracksRemaining == 0 -> "Sleeping until queue ends"
        remainingMs > 0 -> {
            val totalSecs = (remainingMs + 999L) / 1000L
            val m = totalSecs / 60
            val s = totalSecs % 60
            "Sleeping for ${m}m ${s}s"
        }
        else -> "Sleeping until queue ends"
    }
}

@Composable
fun VolumeSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Follow external volume changes (hardware keys)
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        // Thin 3dp track, no thumb — iOS slider proportions
        BoxWithConstraints(
            modifier = Modifier.weight(1f).height(28.dp).pointerInput(Unit) {
                detectTapGestures { offset -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((offset.x / size.width) * maxVolume).toInt().coerceIn(0, maxVolume), 0) }
            }
                .pointerInput(maxVolume) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((offset.x / size.width) * maxVolume).toInt().coerceIn(0, maxVolume), 0) },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = ((volume + (dragAmount / size.width) * maxVolume).toInt()).coerceIn(0, maxVolume)
                            if (target != volume) {
                                volume = target
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(volume / maxVolume.toFloat())
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White)
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

// --- Synced lyrics (Apple Music type scale: huge active vs dimmed lines) ---
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
                text = line.text.ifEmpty { "♪" },
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.38f),
                fontSize = if (isActive) 26.sp else 22.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                lineHeight = if (isActive) 32.sp else 28.sp,
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.startTimeMs) }.padding(vertical = 14.dp)
            )
        }
    }
}

// -- Scrubber with time labels (extracted; identical gesture engine) --
@Composable
fun ScrubberSection(onSeek: (Long) -> Unit, rawFraction: Float, progress: Long, duration: Long) {
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
        val timeStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
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
}

// -- Inline queue row (Apple Music "Playing Next"): swipe-to-delete only —
//    no drag handle; reordering is long-press anywhere on the row. --
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineQueueRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    isDragging: Boolean = false
) {
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
    val scale by animateFloatAsState(targetValue = if (isDragging) 1.04f else 1f, animationSpec = tween(150), label = "dragScale")
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) C.destructive else Color.Transparent, label = "swipeColor")
            Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
    ) {
        Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (isDragging) 12f else 0f; shape = RoundedCornerShape(8.dp); clip = false }) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song = song, audioUri = song.uri, isActive = false, size = 48.dp, cornerRadius = 6.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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

// -- Apple Music-style Sleep Timer sheet --------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    isTimerActive: Boolean,
    selectedOption: String?,
    liveStatus: String? = null,
    onSelectOption: (String) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val C = appleColors()

    val options = listOf(
        "When Current Song Ends", "End of Queue",
        "5 Minutes", "10 Minutes", "15 Minutes",
        "30 Minutes", "45 Minutes", "1 Hour", "2 Hours"
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Live subtitle while active — Apple places it just above the card.
                if (isTimerActive && liveStatus != null) {
                    Text(
                        liveStatus,
                        color = C.secondaryLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Centered grouped card — Apple Music's round, elevated surface.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.cardElevated)
                ) {
                    options.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOption(label) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = C.label, fontSize = 17.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
                            AnimatedVisibility(
                                visible = selectedOption == label && isTimerActive,
                                enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                                exit = scaleOut(tween(100)) + fadeOut(tween(100))
                            ) {
                                Icon(Icons.Filled.Check, null, tint = C.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (index < options.size - 1) {
                            HorizontalDivider(color = C.separator.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }

                // Cancel — Apple places it as a top-aligned text button above the
                // card when a timer is running; here pinned above the card so it
                // scrolls with the sheet content.
                if (isTimerActive) {
                    TextButton(
                        onClick = { onCancelTimer(); onDismiss() },
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        Text("Cancel", color = C.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
