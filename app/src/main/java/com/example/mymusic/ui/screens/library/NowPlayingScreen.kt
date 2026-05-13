package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.mymusic.model.Song
import com.example.mymusic.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Long-press tooltip wrapper.
 * - Hold for 3 s  → shows a dark floating label for 3 s, then auto-hides.
 * - Tap           → fires [onClick] immediately (no tooltip).
 */
@Composable
fun HoldTooltipIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp
) {
    val scope = rememberCoroutineScope()
    var tooltipVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap        = { onClick() },
                    onLongPress  = {
                        scope.launch {
                            tooltipVisible = true
                            delay(3_000)
                            tooltipVisible = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(iconSize)
        )
        // Floating tooltip label
        if (tooltipVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-36).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xDD1E1E1E))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = contentDescription,
                    color      = SpotifyWhite,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─── End-of-Queue Dialog ───────────────────────────────────────────
@Composable
fun EndOfQueueDialog(
    onPlayRandomly: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SpotifySurface2,
        icon             = {
            Icon(Icons.Filled.QueueMusic, contentDescription = null,
                tint = SpotifyGreen, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                "End of Queue",
                color      = SpotifyWhite,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp
            )
        },
        text = {
            Text(
                "You've reached the end of your queue. What would you like to do?",
                color    = SpotifyGray,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play Randomly — entire library, shuffle ON, no repeat within cycle
                Button(
                    onClick = onPlayRandomly,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null,
                        tint = SpotifyBlack, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Randomly", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
                // Play from Beginning — current playlist, linear order
                OutlinedButton(
                    onClick = onPlayFromBeginning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyWhite)
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null,
                        tint = SpotifyWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play from Beginning", color = SpotifyWhite)
                }
                // Not now
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not now", color = SpotifyGray)
                }
            }
        },
        dismissButton = {}
    )
}

// ─── Now Playing Screen ────────────────────────────────────────────
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    song: Song,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    repeatMode: Int,
    shuffleMode: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val progressFraction = if (duration > 0)
        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    // Observe end-of-queue dialog signal
    val showEndDialog by viewModel.showEndDialog.observeAsState(false)

    if (showEndDialog) {
        EndOfQueueDialog(
            onPlayRandomly      = { viewModel.playAllShuffled() },
            onPlayFromBeginning = { viewModel.playFromBeginning() },
            onDismiss           = { viewModel.dismissEndOfQueue() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3D6B4F), SpotifyBlack),
                    startY = 0f,
                    endY   = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HoldTooltipIconButton(
                    icon               = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint               = SpotifyWhite,
                    onClick            = onBack,
                    iconSize           = 32.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM YOUR LIBRARY",
                        color      = SpotifyGray,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "All Songs",
                        color      = SpotifyWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp
                    )
                }
                HoldTooltipIconButton(
                    icon               = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint               = SpotifyWhite,
                    onClick            = {},
                    iconSize           = 24.dp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Album Art ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifySurface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint     = SpotifyGreen,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Song info + Like ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = song.title,
                        color      = SpotifyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = song.artist,
                        color    = SpotifyGray,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HoldTooltipIconButton(
                    icon               = Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint               = SpotifyWhite,
                    onClick            = {},
                    iconSize           = 26.dp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Seek bar ──────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value         = progressFraction,
                    onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = SliderDefaults.colors(
                        thumbColor         = SpotifyWhite,
                        activeTrackColor   = SpotifyWhite,
                        inactiveTrackColor = SpotifyLightGray
                    )
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(progress), color = SpotifyGray, fontSize = 11.sp)
                    Text(formatDuration(duration),  color = SpotifyGray, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Playback controls ─────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Shuffle — tooltip shows new state after tap
                HoldTooltipIconButton(
                    icon               = Icons.Filled.Shuffle,
                    contentDescription = if (shuffleMode) "Shuffle: On" else "Shuffle: Off",
                    tint               = if (shuffleMode) SpotifyGreen else SpotifyGray,
                    onClick            = onToggleShuffle,
                    iconSize           = 22.dp
                )
                // Previous
                HoldTooltipIconButton(
                    icon               = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint               = SpotifyWhite,
                    onClick            = onPrevious,
                    iconSize           = 38.dp
                )
                // Play / Pause — big white circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SpotifyWhite),
                    contentAlignment = Alignment.Center
                ) {
                    HoldTooltipIconButton(
                        icon               = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint               = SpotifyBlack,
                        onClick            = onTogglePlayPause,
                        iconSize           = 38.dp
                    )
                }
                // Next
                HoldTooltipIconButton(
                    icon               = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint               = SpotifyWhite,
                    onClick            = onNext,
                    iconSize           = 38.dp
                )
                // Repeat — tooltip shows resulting mode
                val repeatLabel = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat: One"
                    Player.REPEAT_MODE_ALL -> "Repeat: All"
                    else                   -> "Repeat: Off"
                }
                HoldTooltipIconButton(
                    icon               = if (repeatMode == Player.REPEAT_MODE_ONE)
                                            Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = repeatLabel,
                    tint               = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray,
                    onClick            = onToggleRepeat,
                    iconSize           = 22.dp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Bottom row ────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HoldTooltipIconButton(
                    icon               = Icons.Filled.DevicesOther,
                    contentDescription = "Devices",
                    tint               = SpotifyGray,
                    onClick            = {},
                    iconSize           = 22.dp
                )
                HoldTooltipIconButton(
                    icon               = Icons.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint               = SpotifyGray,
                    onClick            = {},
                    iconSize           = 22.dp
                )
            }
        }
    }
}
