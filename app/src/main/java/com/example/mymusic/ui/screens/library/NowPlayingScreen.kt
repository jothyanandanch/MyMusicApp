package com.example.mymusic.ui.screens.library

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.mymusic.model.Song
import com.example.mymusic.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────

/**
 * Loads the album art bitmap from [song] via Coil on an IO thread,
 * extracts a dominant colour using a simple pixel-sampling algorithm,
 * and returns it as a Compose [Color].  Falls back to a dark Spotify
 * green if art is unavailable.
 */
suspend fun extractDominantColor(context: android.content.Context, song: Song): Color {
    val artUri = song.albumArtUri ?: return Color(0xFF1A3A2A)
    return withContext(Dispatchers.IO) {
        try {
            val loader  = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artUri)
                .size(64)          // small size — fast & enough for color sampling
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable
                ?.let { (it as? BitmapDrawable)?.bitmap }
                ?: return@withContext Color(0xFF1A3A2A)

            // Sample centre 20 % of the bitmap for dominant colour
            val w = bitmap.width; val h = bitmap.height
            val x0 = (w * 0.4f).toInt(); val x1 = (w * 0.6f).toInt()
            val y0 = (h * 0.4f).toInt(); val y1 = (h * 0.6f).toInt()
            var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
            for (x in x0 until x1) for (y in y0 until y1) {
                val px = bitmap.getPixel(x, y)
                rSum += android.graphics.Color.red(px)
                gSum += android.graphics.Color.green(px)
                bSum += android.graphics.Color.blue(px)
                count++
            }
            if (count == 0) return@withContext Color(0xFF1A3A2A)
            // Darken the extracted colour so text stays readable
            val raw = android.graphics.Color.rgb(
                (rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt()
            )
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(raw, hsl)
            hsl[2] = hsl[2].coerceAtMost(0.35f)   // lightness cap = 35 %
            Color(ColorUtils.HSLToColor(hsl))
        } catch (e: Exception) {
            Color(0xFF1A3A2A)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// HoldTooltipIconButton  (unchanged from Phase 3)
// ─────────────────────────────────────────────────────────────────────
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
                    onTap       = { onClick() },
                    onLongPress = {
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
        Icon(imageVector = icon, contentDescription = contentDescription,
            tint = tint, modifier = Modifier.size(iconSize))
        if (tooltipVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-36).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xDD1E1E1E))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(contentDescription, color = SpotifyWhite,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// EndOfQueueDialog  (unchanged from Phase 3)
// ─────────────────────────────────────────────────────────────────────
@Composable
fun EndOfQueueDialog(
    onPlayRandomly: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SpotifySurface2,
        icon = { Icon(Icons.Filled.QueueMusic, null, tint = SpotifyGreen, modifier = Modifier.size(32.dp)) },
        title = { Text("End of Queue", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text  = { Text("You've reached the end of your queue. What would you like to do?",
            color = SpotifyGray, fontSize = 14.sp) },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPlayRandomly, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)) {
                    Icon(Icons.Filled.Shuffle, null, tint = SpotifyBlack, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Randomly", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onPlayFromBeginning, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyWhite)) {
                    Icon(Icons.Filled.Replay, null, tint = SpotifyWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play from Beginning", color = SpotifyWhite)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Not now", color = SpotifyGray)
                }
            }
        },
        dismissButton = {}
    )
}

// ─────────────────────────────────────────────────────────────────────
// NowPlayingScreen  — Phase 4
// ─────────────────────────────────────────────────────────────────────
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
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Observe favourites so the heart icon stays in sync ────────────
    val favoriteIds by viewModel.favoriteIds.observeAsState(initial = emptySet())
    val isFavorite   = favoriteIds.contains(song.id)

    // ── Dynamic gradient derived from album art ───────────────────────
    // Default: dark Spotify green until art loads
    var dominantColor by remember(song.id) { mutableStateOf(Color(0xFF1A3A2A)) }
    LaunchedEffect(song.id) {
        val extracted = extractDominantColor(context, song)
        dominantColor = extracted
    }
    // Animate the gradient top colour smoothly when song changes
    val animatedTop by animateColorAsState(
        targetValue  = dominantColor,
        animationSpec = tween(durationMillis = 800),
        label        = "gradientTop"
    )

    val progressFraction = if (duration > 0)
        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

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
                    colors = listOf(animatedTop, SpotifyBlack),
                    startY = 0f,
                    endY   = 1400f
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

            // ── Top bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HoldTooltipIconButton(Icons.Filled.KeyboardArrowDown, "Collapse",
                    SpotifyWhite, onBack, iconSize = 32.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR LIBRARY", color = SpotifyGray,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("All Songs", color = SpotifyWhite,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                HoldTooltipIconButton(Icons.Filled.MoreVert, "More options",
                    SpotifyWhite, {}, iconSize = 24.dp)
            }

            Spacer(Modifier.height(24.dp))

            // ── Album art — Phase 4: real art via Coil ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifySurface2),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art for ${song.title}",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        error = {
                            // Fallback if no embedded art
                            Icon(Icons.Filled.MusicNote, null,
                                tint = SpotifyGreen, modifier = Modifier.size(96.dp))
                        },
                        loading = {
                            Icon(Icons.Filled.MusicNote, null,
                                tint = SpotifyGray, modifier = Modifier.size(96.dp))
                        }
                    )
                } else {
                    Icon(Icons.Filled.MusicNote, null,
                        tint = SpotifyGreen, modifier = Modifier.size(96.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Song info + Favourite heart ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = SpotifyWhite, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = SpotifyGray, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // ── Favourite button (Phase 4) ────────────────────────
                // Filled heart = favourited (green), outline = not favourited (white)
                HoldTooltipIconButton(
                    icon               = if (isFavorite) Icons.Filled.Favorite
                                         else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Liked Songs"
                                         else "Add to Liked Songs",
                    tint               = if (isFavorite) SpotifyGreen else SpotifyWhite,
                    onClick            = { viewModel.toggleFavorite(song) },
                    iconSize           = 26.dp
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Seek bar ──────────────────────────────────────────────
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
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(progress), color = SpotifyGray, fontSize = 11.sp)
                    Text(formatDuration(duration),  color = SpotifyGray, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Playback controls ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HoldTooltipIconButton(
                    Icons.Filled.Shuffle,
                    if (shuffleMode) "Shuffle: On" else "Shuffle: Off",
                    if (shuffleMode) SpotifyGreen else SpotifyGray,
                    onToggleShuffle, iconSize = 22.dp
                )
                HoldTooltipIconButton(
                    Icons.Filled.SkipPrevious, "Previous",
                    SpotifyWhite, onPrevious, iconSize = 38.dp
                )
                // Play / Pause — white circle
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(SpotifyWhite),
                    contentAlignment = Alignment.Center
                ) {
                    HoldTooltipIconButton(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        SpotifyBlack, onTogglePlayPause, iconSize = 38.dp
                    )
                }
                HoldTooltipIconButton(
                    Icons.Filled.SkipNext, "Next",
                    SpotifyWhite, onNext, iconSize = 38.dp
                )
                val repeatLabel = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat: One"
                    Player.REPEAT_MODE_ALL -> "Repeat: All"
                    else                   -> "Repeat: Off"
                }
                HoldTooltipIconButton(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    repeatLabel,
                    if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray,
                    onToggleRepeat, iconSize = 22.dp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Bottom row ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HoldTooltipIconButton(Icons.Filled.DevicesOther, "Devices",
                    SpotifyGray, {}, iconSize = 22.dp)
                HoldTooltipIconButton(Icons.Filled.QueueMusic, "Queue",
                    SpotifyGray, {}, iconSize = 22.dp)
            }
        }
    }
}
