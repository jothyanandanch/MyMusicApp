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
import androidx.media3.common.Player
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.utils.parseLrc
import kotlinx.coroutines.launch

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
    onAddToPlaylist  : () -> Unit = {},
    onAddToQueue     : () -> Unit = {},
    onDelete         : () -> Unit = {}
) {
    val rawFraction =
        if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3D6B4F), SpotifyBlack), startY = 0f, endY = 1800f))
            .graphicsLayer { translationY = offsetY.value; alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > 400f) {
                            onBack()
                        } else {
                            coroutineScope.launch { offsetY.animateTo(0f) }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── TOP BAR ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Collapse", tint = SpotifyWhite, modifier = Modifier.size(32.dp)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR LIBRARY", color = SpotifyGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("All Songs", color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = SpotifyWhite, modifier = Modifier.size(24.dp)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(SpotifySurface2)) {
                        DropdownMenuItem(text = { Text("Add to Playlist", color = SpotifyWhite) }, onClick = { showMenu = false; onAddToPlaylist() })
                        DropdownMenuItem(text = { Text("Add to Queue", color = SpotifyWhite) }, onClick = { showMenu = false; onAddToQueue() })
                        DropdownMenuItem(text = { Text("Delete Song", color = Color(0xFFFF5555)) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }

            // ── DYNAMIC MIDDLE SECTION: ALBUM ART vs LYRICS ────────────────
            Crossfade(
                targetState = showLyricsView,
                animationSpec = tween(400),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
                label = "LyricsTransition"
            ) { isLyricsMode ->
                if (isLyricsMode) {
                    // ✅ COMPACT HEADER + LYRICS VIEW
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // ✅ Passed 'song = song'
                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = 64.dp, cornerRadius = 8.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = SpotifyGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) {
                                Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) SpotifyGreen else SpotifyWhite)
                            }
                        }
                        SyncedLyricsView(lyricsText = lyrics, progress = progress, onSeek = onSeek, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    // ✅ STANDARD HUGE ALBUM ART + INFO
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(SpotifySurface2), contentAlignment = Alignment.Center) {
                            // ✅ Passed 'song = song'
                            AlbumArt(song = song, audioUri = song.uri, isActive = true, size = null, cornerRadius = 12.dp)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(song.artist, color = SpotifyGray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onToggleFavorite) {
                                Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like", tint = if (isFavorite) SpotifyGreen else SpotifyWhite, modifier = Modifier.size(26.dp))
                            }
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
                        .pointerInput(duration) {
                            detectTapGestures { offset -> val tapped = (offset.x / size.width).coerceIn(0f, 1f); dragFraction = tapped; onSeek((tapped * duration).toLong()) }
                        },
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
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(SpotifyWhite), contentAlignment = Alignment.Center) {
                        IconButton(onClick = onTogglePlayPause) { Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = SpotifyBlack, modifier = Modifier.size(38.dp)) }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = SpotifyWhite, modifier = Modifier.size(38.dp)) }
                    IconButton(onClick = onToggleRepeat) { Icon(imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray, modifier = Modifier.size(22.dp)) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) { Icon(Icons.Filled.DevicesOther, "Devices", tint = SpotifyGray, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { showLyricsView = !showLyricsView }) {
                        Icon(imageVector = Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = if (showLyricsView) SpotifyGreen else SpotifyWhite, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { showQueueSheet = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = SpotifyWhite, modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }

    if (showQueueSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = SpotifySurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = SpotifyGray) }
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                QueueContent(queue = queue, currentSong = song, onSongClick = onQueueItemClick, onSongRemove = onQueueItemRemove, onSongsRemove = onQueueItemsRemove, onSongReorder = onQueueItemReorder, isFullyExpanded = sheetState.currentValue == SheetValue.Expanded)
            }
        }
    }
}

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
    queue: List<Song>, currentSong: Song, onSongClick: (Song) -> Unit, onSongRemove: (Song) -> Unit, onSongsRemove: (Set<Song>) -> Unit, onSongReorder: (Int, Int) -> Unit, isFullyExpanded: Boolean
) {
    val currentIndex = queue.indexOfFirst { it.id == currentSong.id }
    val history = if (currentIndex > 0) queue.take(currentIndex) else emptyList()
    val upstreamUpcoming = if (currentIndex >= 0) queue.drop(currentIndex + 1) else emptyList()

    val listState = rememberLazyListState()
    var localUpcoming by remember { mutableStateOf(upstreamUpcoming) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }

    LaunchedEffect(upstreamUpcoming) { if (draggedItemIndex == null) localUpcoming = upstreamUpcoming }
    LaunchedEffect(isFullyExpanded) { if (!isFullyExpanded){ isSelectionMode = false; selectedSongs = emptySet() } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).pointerInput(isSelectionMode) {
                if (isSelectionMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        listState.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }?.let { item ->
                            val keyStr = item.key as? String
                            if (keyStr?.startsWith("upcoming_") == true) {
                                val idx = keyStr.removePrefix("upcoming_").toInt()
                                draggedItemIndex = idx
                                initialDraggedIndex = idx
                                draggedDistance = 0f
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume(); draggedDistance += dragAmount.y
                        val draggedIdx = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                        val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                        if (itemHeight == 0) return@detectDragGesturesAfterLongPress
                        val targetDelta = (draggedDistance / itemHeight).toInt()

                        if (targetDelta != 0) {
                            val targetIdx = (draggedIdx + targetDelta).coerceIn(0, localUpcoming.size - 1)
                            if (draggedIdx != targetIdx) {
                                val newList = localUpcoming.toMutableList()
                                val item = newList.removeAt(draggedIdx)
                                newList.add(targetIdx, item)
                                localUpcoming = newList
                                draggedItemIndex = targetIdx
                                draggedDistance -= (targetDelta * itemHeight)
                            }
                        }
                    },
                    onDragEnd = {
                        if (initialDraggedIndex != null && draggedItemIndex != null && initialDraggedIndex != draggedItemIndex) {
                            onSongReorder(initialDraggedIndex!!, draggedItemIndex!!)
                        }
                        draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f
                    },
                    onDragCancel = { draggedItemIndex = null; initialDraggedIndex = null; draggedDistance = 0f; localUpcoming = upstreamUpcoming }
                )
            }
        ) {
            item { Text("Now Playing", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            item { QueueSongItem(song = currentSong, isPlaying = true, onClick = {}) }

            if (localUpcoming.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Next In Queue", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (isFullyExpanded) {
                            Text(text = if (isSelectionMode) "Cancel" else "Select", color = SpotifyGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { isSelectionMode = !isSelectionMode; if (!isSelectionMode) selectedSongs = emptySet() })
                        }
                    }
                }

                itemsIndexed(items = localUpcoming, key = { index, _ -> "upcoming_$index" }) { index, song ->
                    val isDragged = draggedItemIndex == index
                    val elevation by animateFloatAsState(if (isDragged) 8f else 0f, label = "elevation")
                    val offsetY = if (isDragged) draggedDistance else 0f
                    val zIndex = if (isDragged) 1f else 0f
                    val isSelected = selectedSongs.contains(song)

                    Box(modifier = Modifier.fillMaxWidth().zIndex(zIndex).graphicsLayer { translationY = offsetY }.shadow(elevation.dp, RoundedCornerShape(8.dp)).background(if (isDragged || isSelected) SpotifySurface2 else Color.Transparent)) {
                        if (isSelectionMode) {
                            QueueSongItem(song = song, isPlaying = false, isSelectionMode = true, isSelected = isSelected, onClick = { selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song })
                        } else {
                            SwipeableQueueItem(song = song, onRemove = { onSongRemove(song) }, onClick = { onSongClick(song) })
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Text("Previously Played", color = SpotifyGray, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) }
                itemsIndexed(history) { _, song -> QueueSongItem(song = song, isPlaying = false, isHistory = true, onClick = { onSongClick(song) }) }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (isSelectionMode && selectedSongs.isNotEmpty()) {
            Button(
                onClick = { onSongsRemove(selectedSongs); isSelectionMode = false; selectedSongs = emptySet() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).fillMaxWidth(0.8f).height(50.dp)
            ) {
                Text("Remove ${selectedSongs.size} Songs", color = Color.White, fontWeight = FontWeight.Bold)
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
        // ✅ Passed 'song = song'
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
        } else if (!isHistory) {
            Icon(Icons.Filled.DragHandle, contentDescription = "Long press to Reorder", tint = SpotifyGray, modifier = Modifier.size(24.dp))
        }
    }
}