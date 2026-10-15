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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
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
    onCancelSleepTimer: () -> Unit = {},
    abLoopState: Int = 0,
    onSetAbLoopPointA: () -> Unit = {},
    onSetAbLoopPointB: () -> Unit = {},
    onStopAbLoop: () -> Unit = {}
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

    // ── Active audio route (kept fresh every second; drives the output icon
    //    and the volume cap — the music stream is limited to 60% over BT). ──
    var outputRoute by remember { mutableStateOf(currentAudioRoute(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            outputRoute = currentAudioRoute(context)
            delay(1000)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    val screenH = remember { context.resources.displayMetrics.heightPixels.toFloat() }

    // Reduced motion: detect system animation scale
    var reduceAnimations by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        reduceAnimations = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    // Spring specs following Apple Design: critically damped for settle,
    // slight bounce only when release velocity carries momentum.
    val springSettle: androidx.compose.animation.core.AnimationSpec<Float> = if (reduceAnimations)
        tween(durationMillis = 150) else spring(dampingRatio = 1f, stiffness = 400f)

    val springMomentum: androidx.compose.animation.core.AnimationSpec<Float> = if (reduceAnimations)
        tween(durationMillis = 180) else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    // ── Inline queue state (shared by the pager's queue page) ──
    val queueListState = rememberLazyListState()
    var localQueue by remember { mutableStateOf(queue) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) } // absolute localQueue index of the dragged row (== dragStartIndex; the list is NOT live-reordered mid-drag)
    var dragStartIndex by remember { mutableStateOf<Int?>(null) } // absolute localQueue index where the drag began
    var draggedDistancePx by remember { mutableStateOf(0f) } // accumulated Y delta (px) since drag start — drives both finger-follow and the drop slot
    var draggedItemHeight by remember { mutableStateOf(0f) } // measured height of one row (px)
    var queueSnapshot by remember { mutableStateOf<List<Song>>(emptyList()) } // localQueue at drag start, for cancel restore
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Hold the local preview order while a drop is pending (draggedItemIndex
    // non-null) so the row never appears to fly back before animating into
    // its slot.
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

    // Recovery: a fling can settle the pager on a fractional page (e.g. 0.47),
    // leaving the player and queue views both half-visible at the same time.
    // When scrolling goes idle, snap back onto the nearest whole page and clear
    // the header-lag offset that can otherwise stay stuck mid-collapse.
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val target = Math.round(pagerState.currentPage.toDouble()).toInt().coerceIn(0, 1)
            if (Math.abs(pagerState.currentPage - target) > 0.05f) {
                try { pagerState.animateScrollToPage(target) } catch (e: Exception) {}
            }
            if (target == 0) pagerDragOffset.snapTo(0f)
        }
    }

    /** Drag-to-dismiss gesture (top bar only, like Apple — never fights the scroll).
     *  Consumes only DOWNWARD moves (dismiss). Upward moves are left unconsumed so
     *  the VerticalPager below can take them (swipe up → Playing Next queue). */
    val dismissDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                // Only steal the gesture once the user is dragging DOWN (dismiss).
                // Upward swipes pass through to the VerticalPager so they can open
                // the "Playing Next" queue page.
                if (dragAmount > 0f) {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val raw = offsetY.value + dragAmount
                    val newOffset = if (raw < 0f) raw * 0.35f else raw
                    coroutineScope.launch { offsetY.snapTo(newOffset) }
                }
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

    // Artwork variant: downward drives dismiss (offsetY); upward drives the
    // pager to the queue page.
    val artworkDismissDrag = Modifier.pointerInput(Unit) {
        var upPull = 0f
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking(); upPull = 0f },
            onVerticalDrag = { change, dragAmount ->
                when {
                    dragAmount > 0f -> {
                        // Downward = dismiss.
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val raw = offsetY.value + dragAmount
                        val newOffset = if (raw < 0f) raw * 0.35f else raw
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                    }
                    else -> {
                        // Upward = advance to the queue page. The detector claims
                        // the vertical gesture at the art node, so we explicitly
                        // drive the pager rather than relying on the VerticalPager
                        // underneath to receive the unconsumed move.
                        change.consume()
                        upPull += -dragAmount
                        if (upPull > 60f) {
                            upPull = 0f
                            coroutineScope.launch {
                                if (pagerState.currentPage == 0) pagerState.animateScrollToPage(1)
                            }
                        }
                    }
                }
            },
            onDragEnd = {
                upPull = 0f
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
                upPull = 0f
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
                                // A-B point-to-point loop (a DJ-cue style repeat on the current song).
                                add(AppleMenuAction(
                                    label = if (abLoopState == 2) "A-B Loop — Running" else "Set Loop Point A",
                                    icon = Icons.Filled.Loop
                                ) { onSetAbLoopPointA() })
                                if (abLoopState >= 1) {
                                    add(AppleMenuAction(
                                        label = if (abLoopState == 2) "Set Loop Point B" else "A-B Loop — Set B to start",
                                        icon = Icons.Filled.Loop
                                    ) { onSetAbLoopPointB() })
                                }
                                if (abLoopState == 2) {
                                    add(AppleMenuAction("Stop A-B Loop", Icons.Filled.Stop, isDestructive = true) { onStopAbLoop() })
                                }
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

            // ── SLEEP-TIMER CANCEL CHIP: a visible, one-tap way to stop a
            //    running timer without opening the sheet (e.g. when the song is
            //    in single-loop and "When Current Song Ends" is pending). ──
            if (isSleepTimerActive && sleepStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(onClick = { showSleepTimer = true })
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Filled.Bedtime, "Sleep timer", tint = C.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(sleepStatus, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Direct one-tap cancel — no need to open the sheet.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(C.accent.copy(alpha = 0.22f))
                            .clickable(onClick = { onCancelSleepTimer() })
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Close, "Cancel sleep timer", tint = C.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Cancel", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                            Text(song.artist ?: "Unknown Artist", color = C.accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onViewArtist() })
                                        }
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
                                    // Tappable artist name — drills into the artist's songs.
                                    // The name is shown in full; if it's wider than the screen it
                                    // auto-scrolls (marquee) instead of truncating with an ellipsis.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onViewArtist() }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        val artist = song.artist ?: "Unknown Artist"
                                        // Natural single-line width of the name, measured off-screen
                                        // so the marquee trigger is reliable (independent of layout
                                        // constraints — the previous maxWidth check was Infinity here).
                                        val textMeasurer = rememberTextMeasurer()
                                        val naturalW = remember(artist) {
                                            textMeasurer.measure(
                                                AnnotatedString(artist),
                                                style = TextStyle(color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            ).size.width
                                        }
                                        // Give the text a real bounded width (weight) so maxWidth is finite.
                                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                                            val maxWpx = with(LocalDensity.current) { maxWidth.toPx() }
                                            val needsMarquee = naturalW > maxWpx + 8f
                                            val scrollX = remember { Animatable(0f) }
                                            LaunchedEffect(artist, needsMarquee, maxWpx) {
                                                if (!needsMarquee) { scrollX.snapTo(0f); return@LaunchedEffect }
                                                val gap = 32f
                                                while (true) {
                                                    scrollX.snapTo(0f)
                                                    delay(1500)
                                                    val distance = naturalW - maxWpx + gap
                                                    val durMs = ((distance / 40f) * 1000f).toInt().coerceIn(4000, 30000)
                                                    scrollX.animateTo(-distance, tween(durationMillis = durMs, easing = LinearEasing))
                                                    delay(1500)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp)),
                                                contentAlignment = if (needsMarquee) Alignment.CenterStart else Alignment.Center
                                            ) {
                                                Text(
                                                    artist,
                                                    color = C.accent,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Clip,
                                                    modifier = Modifier
                                                        .wrapContentWidth()
                                                        .graphicsLayer { translationX = scrollX.value }
                                                )
                                            }
                                        }
                                    }
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
                        VolumeSlider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            isBluetoothRoute = outputRoute == AudioRoute.BLUETOOTH,
                            onCapReached = {
                                android.widget.Toast.makeText(context, "Volume capped at 60% over Bluetooth", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // Dynamic output button — icon reflects the ACTIVE route
                            // (Apple Music behavior), opens the iOS-style picker.
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
                        // A small downward swipe at the top of the queue collapses it back to
                        // the player. Implemented as a NestedScrollConnection: when the list
                        // is at its top and the user keeps dragging down, the unconsumed
                        // overscroll reaches us, so even a short swipe triggers the collapse.
                        val bodyCollapseArmed = remember { mutableStateOf(false) }
                        val bodyCollapse = Modifier.nestedScroll(
                            object : NestedScrollConnection {
                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    // Down-overscroll at the top of the list: the LazyColumn
                                    // consumes 0 (can't scroll further up), so the whole
                                    // downward delta arrives as the unconsumed remainder.
                                    val remaining = available - consumed
                                    val atTop = queueListState.firstVisibleItemIndex == 0 &&
                                            queueListState.firstVisibleItemScrollOffset <= 0
                                    if (remaining.y > 0f && atTop && !pagerState.isScrollInProgress) {
                                        bodyCollapseArmed.value = true
                                        headerPull += remaining.y
                                        coroutineScope.launch {
                                            if (headerPull > 36f) {
                                                headerPull = 0f
                                                pagerDragOffset.snapTo(0f)
                                                pagerState.animateScrollToPage(0)
                                            } else {
                                                pagerDragOffset.snapTo(headerPull.coerceAtMost(150f))
                                            }
                                        }
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    // A downward fling after a top overscroll also collapses.
                                    if (bodyCollapseArmed.value && !pagerState.isScrollInProgress) {
                                        bodyCollapseArmed.value = false
                                        headerPull = 0f
                                        coroutineScope.launch {
                                            pagerDragOffset.snapTo(0f)
                                            pagerState.animateScrollToPage(0)
                                        }
                                    }
                                    return Velocity.Zero
                                }
                            }
                        )
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
                                .then(bodyCollapse)
                                .graphicsLayer { translationY = pagerDragOffset.value * 0.15f },
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
                                val idx = localQueue.indexOfFirst { it.id == s.id }
                                val dragging = draggedItemIndex != null
                                val isDraggingThis = draggedItemIndex == idx
                                val startIdx = dragStartIndex ?: -1
                                val rowHeightPx = draggedItemHeight
                                // Signed row-delta from the accumulated drag distance.
                                val delta = if (dragging && startIdx >= 0 && rowHeightPx > 0f)
                                    (draggedDistancePx / rowHeightPx).roundToInt() else 0
                                // Drop slot the dragged row is pointing at. An upcoming
                                // song can only land at/after the now-playing row, so
                                // clamp into [currentIdx+1, last].
                                val targetIdx = if (dragging && startIdx >= 0) {
                                    val low = if (currentIdx >= 0) currentIdx + 1 else 0
                                    (startIdx + delta).coerceIn(low, localQueue.size - 1)
                                } else -1

                                // Visual gap-close for neighbors: when the dragged row moves,
                                // the rows it vacates are pulled in to fill its old space,
                                // the way Apple Music slides the above song down into the
                                // gap when you drag up (no list mutation until release).
                                val shiftY = when {
                                    !dragging -> 0f
                                    isDraggingThis -> 0f
                                    targetIdx > startIdx && idx in (startIdx + 1)..targetIdx -> -rowHeightPx
                                    targetIdx < startIdx && idx in targetIdx..(startIdx - 1) -> rowHeightPx
                                    else -> 0f
                                }

                                // Per-row long-press vertical drag → reorder. Horizontal
                                // swipe → remove (InlineQueueRow's SwipeToDismissBox).
                                val rowReorderModifier = Modifier.pointerInput(s.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (draggedItemIndex != null) return@detectDragGesturesAfterLongPress
                                            val startIdx = localQueue.indexOfFirst { it.id == s.id }
                                            if (startIdx < 0 || startIdx == currentIdx) return@detectDragGesturesAfterLongPress
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedItemIndex = startIdx
                                            dragStartIndex = startIdx
                                            draggedDistancePx = 0f
                                            draggedItemHeight = with(density) { 64.dp.toPx() }
                                            queueSnapshot = localQueue
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (draggedItemIndex == null) return@detectDragGesturesAfterLongPress
                                            // Take the gesture for ourselves so the LazyColumn
                                            // doesn't scroll underneath the reorder.
                                            change.consume()
                                            draggedDistancePx += dragAmount.y
                                        },
                                        onDragEnd = {
                                            val from = dragStartIndex
                                            if (from != null && draggedItemHeight > 0f) {
                                                val delta = (draggedDistancePx / draggedItemHeight).roundToInt()
                                                val low = if (currentIdx >= 0) currentIdx + 1 else 0
                                                val to = (from + delta).coerceIn(low, localQueue.size - 1)
                                                if (to != from && to in localQueue.indices) {
                                                    onQueueItemReorder(from, to)
                                                }
                                            }
                                            draggedDistancePx = 0f
                                            draggedItemIndex = null
                                            dragStartIndex = null
                                            queueSnapshot = emptyList()
                                        },
                                        onDragCancel = {
                                            draggedDistancePx = 0f
                                            draggedItemIndex = null
                                            dragStartIndex = null
                                            queueSnapshot = emptyList()
                                        }
                                    )
                                }
                                InlineQueueRow(
                                    song = s,
                                    onClick = { onQueueItemClick(s) },
                                    onRemove = { onQueueItemRemove(s) },
                                    isDragging = isDraggingThis,
                                    dragOffsetY = if (isDraggingThis) draggedDistancePx else 0f,
                                    shiftY = shiftY,
                                    alpha = if (dragging) (if (isDraggingThis) 1f else 0.5f) else 1f,
                                    reorderModifier = rowReorderModifier
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
