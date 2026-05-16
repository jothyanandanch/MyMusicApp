package com.example.mymusic.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymusic.model.Song
import com.example.mymusic.ui.screens.library.SpotifyBlack
import com.example.mymusic.ui.screens.library.SpotifyGray
import com.example.mymusic.ui.screens.library.SpotifyGreen
import com.example.mymusic.ui.screens.library.SpotifySurface
import com.example.mymusic.ui.screens.library.SpotifySurface2
import com.example.mymusic.ui.screens.library.SpotifyWhite
import com.example.mymusic.ui.screens.playlist.SortMenu
import com.example.mymusic.ui.screens.playlist.SortType

@Composable
fun FavoritesScreen(
    favorites        : List<Song>,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    modifier         : Modifier = Modifier,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit
) {
    var sortType by remember { mutableStateOf(SortType.DEFAULT) }

    val displayFavorites = remember(favorites, sortType) {
        when (sortType) {
            SortType.DEFAULT -> favorites
            SortType.TITLE_AZ -> favorites.sortedBy { it.title.lowercase() }
            SortType.TITLE_ZA -> favorites.sortedByDescending { it.title.lowercase() }
            SortType.ARTIST -> favorites.sortedBy { it.artist.lowercase() }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // ── Spotify "Liked Songs" gradient header ──
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF4B2D8C), Color(0xFF1A1A2E), SpotifyBlack)
                        )
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    // Purple heart icon box (Spotify's exact Liked Songs artwork style)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF7B5EA7), Color(0xFF4B2D8C))),
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint     = SpotifyWhite,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Liked Songs", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text(
                        "${favorites.size} song${if (favorites.size != 1) "s" else ""}",
                        color = SpotifyGray, fontSize = 13.sp
                    )
                }
            }
        }

        // ── Action row: Shuffle play button ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SortMenu(sortType =sortType,onSortChange={sortType = it})
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, null, tint = SpotifyGray)
                }
                // Big green shuffle/play circle
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SpotifyGreen)
                        .clickable {
                            if (favorites.isNotEmpty()) onSongClick(favorites.first())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play Liked Songs",
                        tint = SpotifyBlack, modifier = Modifier.size(32.dp))
                }
            }
        }

        if (displayFavorites.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null,
                        tint = SpotifyGray, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Songs you like will appear here",
                        color = SpotifyWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Save songs by tapping the heart icon.",
                        color = SpotifyGray, fontSize = 13.sp)
                }
            }
        } else {
            itemsIndexed(displayFavorites) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .background(if (song.id == currentSong?.id) SpotifySurface else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track number
                    Text(
                        text     = "${index + 1}",
                        color    = if (song.id == currentSong?.id) SpotifyGreen else SpotifyGray,
                        fontSize = 13.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpotifySurface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, null,
                            tint     = if (song.id == currentSong?.id) SpotifyGreen else SpotifyGray,
                            modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text       = song.title,
                            color      = if (song.id == currentSong?.id) SpotifyGreen else SpotifyWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(song.artist, color = SpotifyGray, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Heart — always green/filled here since this IS the favorites list
                    IconButton(
                        onClick  = { onToggleFavorite(song) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Favorite,
                            contentDescription = "Remove from Liked Songs",
                            tint               = SpotifyGreen,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Icon(Icons.Filled.MoreVert, null,
                        tint = SpotifyGray, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(color = SpotifySurface, thickness = 0.5.dp)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}