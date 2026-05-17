package com.example.mymusic.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import com.example.mymusic.model.Song


@Composable
fun MiniPlayer(
    musicItem: Song,
    progress: Float,
    onProgressCallback: (Float) -> Unit,
    isMusicPlaying: Boolean,
    onStartCallback: () -> Unit,
    onNextCallback: () -> Unit,
) {
    BottomAppBar(
        content = {
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtistInfoTab(
                        audioItem = musicItem,
                        modifier = Modifier.weight(1f)
                    )
                    MiniPlayerControls(
                        isMusicPlaying = isMusicPlaying,
                        onStartCallback = onStartCallback,
                        onNextCallback = onNextCallback
                    )
                }
                Slider(
                    value = progress,
                    valueRange = 0f..100f,
                    onValueChange = {
                        onProgressCallback(it)
                    }
                )
            }
        }
    )
}

@Composable
private fun MiniPlayerControls(
    modifier: Modifier = Modifier,
    isMusicPlaying: Boolean,
    onStartCallback: () -> Unit,
    onNextCallback: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniPlayerIcon(
            icon = if (isMusicPlaying) Icons.Default.Pause
            else Icons.Default.PlayArrow
        ) {
            onStartCallback()
        }
        Icon(
            modifier = Modifier
                .clickable {
                    onNextCallback()
                },
            imageVector = Icons.Default.SkipNext,
            contentDescription = "next"
        )
    }
}
@Composable
private fun ArtistInfoTab(
    audioItem: Song,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ Replace Box + Icon fallback with AlbumArt
        AlbumArt(
            audioUri = audioItem.albumArtUri,   // or audioItem.albumArtUri depending on your model
            isActive = true,
            size = 42.dp
        )

        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = audioItem.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                overflow = TextOverflow.Clip,
                maxLines = 1
            )
            Text(
                text = audioItem.artist,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Clip,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun MiniPlayerIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    onClickCallback: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clickable {
                onClickCallback()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = "player icon")
    }
}

