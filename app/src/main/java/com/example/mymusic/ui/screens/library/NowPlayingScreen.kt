package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mymusic.model.Song
import com.example.mymusic.ui.theme.*
import com.example.mymusic.utils.formatDuration
import com.example.mymusic.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
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
    onToggleShuffle: () -> Unit,
    viewModel: MusicViewModel
) {
    val context          = LocalContext.current
    val progressFraction = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f

    // ── Snackbar for button name tooltips ────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope    = rememberCoroutineScope()

    // ── End-of-Queue dialog ──────────────────────────────────────────
    val endOfQueue by viewModel.endOfQueue.observeAsState(false)
    if (endOfQueue) {
        EndOfQueueDialog(
            onPlayShuffled   = { viewModel.playAllShuffled() },
            onPlayFromStart  = { viewModel.playFromBeginning() },
            onDismiss        = { viewModel.dismissEndOfQueue() }
        )
    }

    // Helper: show a brief snackbar with the button label
    fun showTooltip(label: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message  = label,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost   = {
            // Anchored at the bottom, above the controls
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar  = { data ->
                    Snackbar(
                        snackbarData    = data,
                        containerColor  = SpotifyElevated,
                        contentColor    = SpotifyWhite,
                        shape           = RoundedCornerShape(8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(SpotifyGradientTop, SpotifyBlack),
                        startY = 0f,
                        endY   = 1200f
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                // ── Top Bar ──────────────────────────────────────────
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
                            tint = SpotifyWhite, modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYING FROM YOUR LIBRARY",
                            color = SpotifyGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        Text(
                            "All Songs", color = SpotifyWhite,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = { showTooltip("More options") }) {
                        Icon(Icons.Filled.MoreVert, "More options", tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                    }
                }

                // ── Album Art ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifySurface2),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        var loadFailed by remember { mutableStateOf(false) }
                        if (!loadFailed) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(song.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album Art",
                                modifier      = Modifier.fillMaxSize(),
                                contentScale  = ContentScale.Crop,
                                onError       = { loadFailed = true }
                            )
                        }
                        if (loadFailed) {
                            Icon(Icons.Filled.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(96.dp))
                        }
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(96.dp))
                    }
                }

                // ── Song Info + Like ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title, color = SpotifyWhite,
                            fontWeight = FontWeight.Bold, fontSize = 22.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist, color = SpotifyGray,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showTooltip("Add to Liked Songs") }) {
                        Icon(Icons.Filled.FavoriteBorder, "Add to Liked Songs", tint = SpotifyWhite, modifier = Modifier.size(26.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Seek Bar ──────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressFraction.coerceAtLeast(0.001f),
                        onValueChange = { onSeek((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor         = SpotifyWhite,
                            activeTrackColor   = SpotifyWhite,
                            inactiveTrackColor = SpotifyLightGray
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(progress), color = SpotifyGray, fontSize = 11.sp)
                        Text(formatDuration(duration), color = SpotifyGray, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Playback Controls ────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Shuffle
                    TooltipIconButton(
                        icon        = Icons.Filled.Shuffle,
                        label       = if (shuffleMode) "Shuffle: On" else "Shuffle: Off",
                        tint        = if (shuffleMode) SpotifyGreen else SpotifyGray,
                        size        = 22,
                        onLongPress = { showTooltip(if (shuffleMode) "Shuffle: On" else "Shuffle: Off") },
                        onClick     = {
                            showTooltip(if (!shuffleMode) "Shuffle: On" else "Shuffle: Off")
                            onToggleShuffle()
                        }
                    )
                    // Previous
                    TooltipIconButton(
                        icon        = Icons.Filled.SkipPrevious,
                        label       = "Previous",
                        tint        = SpotifyWhite,
                        size        = 38,
                        onLongPress = { showTooltip("Previous") },
                        onClick     = {
                            showTooltip("Previous")
                            onPrevious()
                        }
                    )
                    // Play / Pause (big white circle)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SpotifyWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            showTooltip(if (isPlaying) "Pause" else "Play")
                            onTogglePlayPause()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = SpotifyBlack, modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    // Next
                    TooltipIconButton(
                        icon        = Icons.Filled.SkipNext,
                        label       = "Next",
                        tint        = SpotifyWhite,
                        size        = 38,
                        onLongPress = { showTooltip("Next") },
                        onClick     = {
                            showTooltip("Next")
                            onNext()
                        }
                    )
                    // Repeat
                    val repeatLabel = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat: One"
                        Player.REPEAT_MODE_ALL -> "Repeat: All"
                        else                   -> "Repeat: Off"
                    }
                    TooltipIconButton(
                        icon        = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        label       = repeatLabel,
                        tint        = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray,
                        size        = 22,
                        onLongPress = { showTooltip(repeatLabel) },
                        onClick     = {
                            onToggleRepeat()
                            // Show the NEW state after toggle
                            val nextLabel = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> "Repeat: All"
                                Player.REPEAT_MODE_ALL -> "Repeat: One"
                                else                   -> "Repeat: Off"
                            }
                            showTooltip(nextLabel)
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Devices + Queue Row ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTooltip("Connect to a device") }) {
                        Icon(Icons.Filled.DevicesOther, "Connect to a device",
                            tint = SpotifyGray, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { showTooltip("Queue") }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                            tint = SpotifyGray, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ─── Reusable icon button with long-press tooltip support ─────────────
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    size: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector    = icon,
            contentDescription = label,
            tint           = tint,
            modifier       = Modifier.size(size.dp)
        )
    }
}

// ─── End-of-Queue Dialog ──────────────────────────────────────────────
@Composable
fun EndOfQueueDialog(
    onPlayShuffled:  () -> Unit,
    onPlayFromStart: () -> Unit,
    onDismiss:       () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SpotifySurface2,
        iconContentColor = SpotifyGreen,
        titleContentColor = SpotifyWhite,
        textContentColor  = SpotifyGray,
        icon  = { Icon(Icons.Filled.QueueMusic, null, modifier = Modifier.size(36.dp)) },
        title = { Text("End of Queue", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text  = {
            Text(
                "You've reached the end of the playlist.\nWhat would you like to do?",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Option 1 — Play Randomly
                Button(
                    onClick = onPlayShuffled,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor   = SpotifyBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Randomly", fontWeight = FontWeight.Bold)
                }
                // Option 2 — Play from Beginning
                OutlinedButton(
                    onClick = onPlayFromStart,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Replay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play from Beginning")
                }
                // Option 3 — Dismiss
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not now", color = SpotifyGray)
                }
            }
        },
        dismissButton = null
    )
}
