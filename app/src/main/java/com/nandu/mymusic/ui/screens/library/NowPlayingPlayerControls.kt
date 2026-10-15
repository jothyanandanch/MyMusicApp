package com.nandu.mymusic.ui.screens.library

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.utils.parseLrc
import com.nandu.mymusic.ui.theme.appleColors

/**
 * The player controls used by the Now Playing screen: the volume slider, the
 * scrubber with time labels, the synced-lyrics view, and the equalizer
 * "now playing" bars.
 */

@Composable
fun VolumeSlider(
    modifier: Modifier = Modifier,
    isBluetoothRoute: Boolean = false,
    onCapReached: () -> Unit = {}
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Over Bluetooth the music stream is capped at 60% — any increase above that
    // is rejected (clamped) so loud audio can't play over the BT link.
    val capVolume = if (isBluetoothRoute) (0.6f * maxVolume).toInt() else maxVolume
    // Read through a live holder so the gesture handlers see the latest callback
    // without re-subscribing their pointer input.
    val capReached = rememberUpdatedState(onCapReached)

    // One toast per "episode": arm the latch when the volume drops back below the
    // cap (or we leave BT), so repeated clamps in a single swipe don't spam.
    var capToastArmed by remember { mutableStateOf(true) }
    LaunchedEffect(volume, isBluetoothRoute) {
        capToastArmed = !isBluetoothRoute || volume < capVolume
    }

    // Snap down to the cap the moment Bluetooth becomes active (handles a volume
    // that was already too loud before the route flipped).
    LaunchedEffect(isBluetoothRoute) {
        if (isBluetoothRoute) {
            val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (actual > capVolume) {
                volume = capVolume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, capVolume, 0)
                capToastArmed = false
                capReached.value()
            }
        }
    }

    // Follow external volume changes (hardware keys); clamp to the cap over BT.
    DisposableEffect(isBluetoothRoute) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val clamped = if (isBluetoothRoute) actual.coerceAtMost(capVolume) else actual
                if (clamped != actual) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
                    if (capToastArmed) {
                        capToastArmed = false
                        capReached.value()
                    }
                }
                volume = clamped
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        // Thin 3dp track, no thumb — iOS slider proportions
        BoxWithConstraints(
            modifier = Modifier.weight(1f).height(28.dp).pointerInput(capVolume) {
                detectTapGestures { offset ->
                    val raw = ((offset.x / size.width) * maxVolume).toInt()
                    val target = raw.coerceIn(0, capVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    volume = target
                    if (isBluetoothRoute && raw > capVolume && capToastArmed) {
                        capToastArmed = false
                        capReached.value()
                    }
                }
            }
                .pointerInput(maxVolume, capVolume) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val raw = ((offset.x / size.width) * maxVolume).toInt()
                            val target = raw.coerceIn(0, capVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            volume = target
                            if (isBluetoothRoute && raw > capVolume && capToastArmed) {
                                capToastArmed = false
                                capReached.value()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val raw = ((volume + (dragAmount / size.width) * maxVolume).toInt())
                            val target = raw.coerceIn(0, capVolume)
                            if (target != volume) {
                                volume = target
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                if (isBluetoothRoute && raw > capVolume && capToastArmed) {
                                    capToastArmed = false
                                    capReached.value()
                                }
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

    // Keep the active line at the vertical CENTER of the pane in a single smooth
    // motion. We scroll to an anchor item just above the active one (by ~half the
    // visible rows) so the active line lands centered — one animation, no
    // "jump to top then slide down" two-phase motion.
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val info = listState.layoutInfo
        val viewportPx = info.viewportEndOffset - info.viewportStartOffset
        if (viewportPx <= 0) return@LaunchedEffect

        // Average lyric-row height (the active row is a touch taller; an estimate
        // is fine). Fall back to a fixed px value if the list isn't laid out yet.
        val avgRowPx = if (info.visibleItemsInfo.isNotEmpty())
            info.visibleItemsInfo.sumOf { it.size }.toLong() / info.visibleItemsInfo.size
        else 72
        // How many rows fit vertically in the pane.
        val rowsVisible = (viewportPx / avgRowPx).toInt().coerceAtLeast(1)
        // Place this anchor at the top so the active line sits ~half-rows below,
        // i.e. near the vertical center.
        val anchor = (activeIndex - rowsVisible / 2).coerceAtLeast(0)
        listState.animateScrollToItem(anchor)
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
