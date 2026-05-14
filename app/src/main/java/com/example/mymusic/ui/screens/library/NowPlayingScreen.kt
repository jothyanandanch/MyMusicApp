package com.example.mymusic.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.mymusic.model.Song

/**
 * Fixed NowPlayingScreen:
 * 1. Progress bar thumb position uses BoxWithConstraints so it tracks
 *    correctly across all screen widths — no more fixed 100.dp offset math.
 * 2. Drag logic snaps to tap position on first touch via onDragStart,
 *    then accumulates delta — bar never jumps.
 * 3. Thumb is hidden at rest and appears only while dragging (Spotify style),
 *    smoothly animated with animateFloatAsState.
 * 4. animateFloatAsState interpolates between 200 ms polling ticks for a
 *    buttery-smooth bar movement.
 * 5. Shuffle toggle: current song is not restarted (fixed in ViewModel).
 */
@Composable
fun NowPlayingScreen(
    song             : Song,
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
    onToggleFavorite : () -> Unit
) {
    val rawFraction =
        if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3D6B4F), SpotifyBlack),
                    startY = 0f,
                    endY   = 1800f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── TOP BAR ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, "Collapse",
                        tint     = SpotifyWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM YOUR LIBRARY",
                        color         = SpotifyGray,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "All Songs",
                        color      = SpotifyWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.MoreVert, "More",
                        tint     = SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── ALBUM ART ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifySurface2),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model              = song.albumArtUri,
                        contentDescription = "Album art",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        null,
                        tint     = SpotifyGreen,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            // ── SONG INFO + HEART ───────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color      = SpotifyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        song.artist,
                        color    = SpotifyGray,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector        = if (isFavorite) Icons.Filled.Favorite
                                             else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Unlike" else "Like",
                        tint               = if (isFavorite) SpotifyGreen else SpotifyWhite,
                        modifier           = Modifier.size(26.dp)
                    )
                }
            }

            // ── PROGRESS BAR (FIXED) ────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {

                var isDragging   by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableStateOf(0f) }

                // Animate bar movement between 200 ms polling ticks (smooth).
                // While dragging: instant response (tween 0 ms).
                val animatedFraction by animateFloatAsState(
                    targetValue   = if (isDragging) dragFraction else rawFraction,
                    animationSpec = tween(durationMillis = if (isDragging) 0 else 100),
                    label         = "progressAnim"
                )

                // Thumb scale: hidden (0f) at rest, full size (1f) while dragging.
                val thumbScale by animateFloatAsState(
                    targetValue   = if (isDragging) 1f else 0f,
                    animationSpec = tween(durationMillis = 150),
                    label         = "thumbScale"
                )

                // BoxWithConstraints gives us the real pixel width of the track
                // so the thumb offset is always perfectly accurate.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)   // Tall touch-target (Spotify style)
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    // Snap to tap position on first touch
                                    // so the bar never jumps on drag-start.
                                    dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    isDragging   = true
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragFraction = (dragFraction + dragAmount / size.width)
                                        .coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    onSeek((dragFraction * duration).toLong())
                                    isDragging = false
                                },
                                onDragCancel = {
                                    isDragging = false
                                }
                            )
                        }
                        .pointerInput(duration) {
                            detectTapGestures { offset ->
                                val tapped = (offset.x / size.width).coerceIn(0f, 1f)
                                dragFraction = tapped
                                onSeek((tapped * duration).toLong())
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val trackWidthDp = maxWidth

                    // Gray background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isDragging) 5.dp else 4.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(50))
                            .background(SpotifyLightGray)
                    )

                    // Green filled portion
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(if (isDragging) 5.dp else 4.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(50))
                            .background(SpotifyGreen)
                    )

                    // Thumb: hidden at rest, visible only on drag (Spotify style)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = (trackWidthDp * animatedFraction) - 7.dp)
                            .scale(thumbScale)
                            .clip(CircleShape)
                            .background(SpotifyWhite)
                            .shadow(elevation = 3.dp, shape = CircleShape)
                    )
                }

                // Time labels
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(
                            if (isDragging) (dragFraction * duration).toLong() else progress
                        ),
                        color    = SpotifyGray,
                        fontSize = 11.sp
                    )
                    Text(
                        formatDuration(duration),
                        color    = SpotifyGray,
                        fontSize = 11.sp
                    )
                }
            }

            // ── PLAYBACK CONTROLS ─────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle, "Shuffle",
                        tint     = if (shuffleMode) SpotifyGreen else SpotifyGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Previous
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious, "Previous",
                        tint     = SpotifyWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SpotifyWhite),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Filled.Pause
                                                 else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint               = SpotifyBlack,
                            modifier           = Modifier.size(38.dp)
                        )
                    }
                }
                // Next
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipNext, "Next",
                        tint     = SpotifyWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector        = if (repeatMode == Player.REPEAT_MODE_ONE)
                                                Icons.Filled.RepeatOne
                                             else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint               = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen
                                             else SpotifyGray,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }

            // ── BOTTOM ROW ───────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.DevicesOther, "Devices",
                        tint     = SpotifyGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                        tint     = SpotifyGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
