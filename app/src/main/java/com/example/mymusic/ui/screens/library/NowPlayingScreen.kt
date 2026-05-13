package com.example.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.mymusic.model.Song

@Composable
fun NowPlayingScreen(
    song             : Song,
    isPlaying        : Boolean,
    progress         : Long,
    duration         : Long,
    repeatMode       : Int,
    shuffleMode      : Boolean,
    isFavorite       : Boolean,          // ← Phase 4
    onBack           : () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext           : () -> Unit,
    onPrevious       : () -> Unit,
    onSeek           : (Long) -> Unit,
    onToggleRepeat   : () -> Unit,
    onToggleShuffle  : () -> Unit,
    onToggleFavorite : () -> Unit        // ← Phase 4
) {
    val progressFraction =
        if (duration > 0) (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3D6B4F), SpotifyBlack),
                    startY = 0f, endY = 1200f
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
            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Collapse",
                        tint = SpotifyWhite, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR LIBRARY", color = SpotifyGray,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("All Songs", color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, "More", tint = SpotifyWhite, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Album art ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifySurface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null,
                    tint = SpotifyGreen, modifier = Modifier.size(96.dp))
            }

            Spacer(Modifier.height(32.dp))

            // ── Song info + HEART ──────────────────────────────────────
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
                // Heart toggles green when liked
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

            Spacer(Modifier.height(24.dp))

            // ── Seek bar ──
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value         = progressFraction,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier      = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor        = SpotifyWhite,
                        activeTrackColor  = SpotifyWhite,
                        inactiveTrackColor = SpotifyLightGray
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(progress), color = SpotifyGray, fontSize = 11.sp)
                    Text(formatDuration(duration), color = SpotifyGray, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Controls ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Filled.Shuffle, "Shuffle",
                        tint     = if (shuffleMode) SpotifyGreen else SpotifyGray,
                        modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, "Previous",
                        tint = SpotifyWhite, modifier = Modifier.size(38.dp))
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SpotifyWhite),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint               = SpotifyBlack,
                            modifier           = Modifier.size(38.dp)
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, "Next",
                        tint = SpotifyWhite, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint     = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.DevicesOther, "Devices",
                        tint = SpotifyGray, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                        tint = SpotifyGray, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}