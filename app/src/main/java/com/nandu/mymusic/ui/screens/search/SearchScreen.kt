package com.nandu.mymusic.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.screens.library.LargeTitleHeader
import com.nandu.mymusic.ui.screens.library.LibraryGroupItem
import com.nandu.mymusic.ui.screens.library.SongItem
import com.nandu.mymusic.ui.screens.library.downloadSong
import com.nandu.mymusic.ui.screens.library.splitArtists
import com.nandu.mymusic.ui.theme.AppleText
import com.nandu.mymusic.ui.theme.CategoryGradients
import com.nandu.mymusic.ui.theme.appleColors
import com.nandu.mymusic.utils.SearchUtils
import java.util.LinkedHashMap

/**
 * Apple Music "Search" tab — in-body iOS search field, Browse Categories grid
 * when idle, and result sections while typing.
 */
data class SearchData(
    val localSongs: List<Song>,
    val onlineSongs: List<Song>,
    val onlineSearchSongs: List<Song>,
    val libraryMode: Int,
    val hybridLocalOnly: Boolean,
    val hybridOnlineOnly: Boolean
)

@Composable
fun SearchScreen(
    searchQuery      : String?, // null = state managed internally (Apple-style per-tab)
    onQueryChange    : (String) -> Unit,
    libraryData      : SearchData,
    currentSong      : Song?,
    favoriteIds      : Set<Long>,
    showSnackbar: (String) -> Unit,
    onSongClick      : (Song) -> Unit,
    onToggleFavorite : (Song) -> Unit,
    onPlayNext       : (Song) -> Unit,
    onAddToQueue     : (Song) -> Unit,
    onAddToPlaylist  : (Song) -> Unit,
    onAddSongsToPlaylist: (List<Song>) -> Unit = {},
    onAddListToQueue: (List<Song>) -> Unit = {},
    onAddShuffledToQueue: (List<Song>) -> Unit = {},
    onDelete         : (Song) -> Unit,
    onViewAlbum      : (String) -> Unit,
    onViewArtist     : (String) -> Unit,
    onDownload: (Song) -> Unit
) {
    val C = appleColors()
    val context = LocalContext.current

    var internalQuery by remember { mutableStateOf("") }
    val query = searchQuery ?: internalQuery
    val setQuery: (String) -> Unit = {
        internalQuery = it
        onQueryChange(it)
    }

    // ── Filter pipeline (fuzzy + token matching: "sunflowr" finds "Sunflower",
    //    "sun flower" finds "Sun Flower", diacritics stripped so "résumé" matches "resume") ──
    val filteredSongs = if (query.isBlank()) emptyList() else {
        val matches: (Song) -> Boolean = { SearchUtils.matches(query, it.title, it.artist, it.album) }
        when (libraryData.libraryMode) {
            1 -> libraryData.onlineSearchSongs.filter(matches).distinctBy { it.id }
            2 -> {
                if (libraryData.hybridLocalOnly) {
                    libraryData.localSongs.filter(matches)
                }
                else if (libraryData.hybridOnlineOnly) {
                    libraryData.onlineSongs.filter(matches)
                }
                else {
                    val localMatches = libraryData.localSongs.filter(matches)
                    val onlineMatches = libraryData.onlineSearchSongs.filter(matches)
                    (localMatches + onlineMatches).distinctBy { it.id }
                }
            }
            else -> libraryData.localSongs.filter(matches)
        }
    }

    // Dynamically extract matching artists based on the search query
    val matchingArtists = remember(filteredSongs, query) {
        if (query.isBlank()) emptyList()
        else {
            val q = SearchUtils.normalizeQuery(query)
            val artistMap = LinkedHashMap<String, MutableList<Song>>()
            for (song in filteredSongs) {
                val artistName = song.artist ?: "Unknown Artist"
                for (artist in splitArtists(artistName)) {
                    if (SearchUtils.normalizeText(artist).contains(q)) {
                        artistMap.getOrPut(artist) { mutableListOf() }.add(song)
                    }
                }
            }
            artistMap.entries.toList()
        }
    }

    // Dynamically extract matching albums based on the search query
    val matchingAlbums = remember(filteredSongs, query) {
        if (query.isBlank()) emptyList()
        else filteredSongs
            .filter { it.album != null && SearchUtils.normalizeText(it.album).contains(SearchUtils.normalizeQuery(query)) }
            .groupBy { it.album!! }
            .entries.toList()
    }

    val languages = remember(libraryData.onlineSongs) {
        // Songs-bearing languages first, then the full supported set — so the
        // Browse grid is always full like Apple's.
        val songLanguages = libraryData.onlineSongs
            .mapNotNull { it.language?.takeIf { l -> l.isNotBlank() && !l.equals("Unknown", true) } }
            .distinct()
        (songLanguages + listOf("Telugu", "English", "Hindi", "Tamil", "Malayalam")).distinct()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(C.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { LargeTitleHeader("Search") }

        // ── iOS-style search field ──
        item {
            TextField(
                value = query,
                onValueChange = setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Artists, Songs, Lyrics, and More", color = C.secondaryLabel, fontSize = 16.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.secondaryLabel) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { setQuery("") }) {
                            Icon(Icons.Filled.Close, "Clear", tint = C.secondaryLabel, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.fill,
                    unfocusedContainerColor = C.fill,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = C.label,
                    unfocusedTextColor = C.label,
                    cursorColor = C.accent
                )
            )
        }

        if (query.isBlank()) {
            // ── Browse Categories grid ──
            if (languages.isNotEmpty()) {
                item {
                    SectionHeaderText("Browse Categories")
                    val rows = (languages.size + 1) / 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height((110.dp * rows) + (12.dp * (rows - 1))),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(languages) { index, language ->
                            val gradient = CategoryGradients[index % CategoryGradients.size]
                            Box(
                                modifier = Modifier
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(gradient))
                                    .clickable { setQuery(language) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    language,
                                    color = Color.White,
                                    style = AppleText.Title3.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Search, null, tint = C.secondaryLabel, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Search your library", color = C.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        } else {
            // 1. SHOW MATCHING ALBUMS AT THE TOP
            if (matchingAlbums.isNotEmpty()) {
                item {
                    SectionHeaderText("Albums")
                }
                items(matchingAlbums) { (albumName, albumSongs) ->
                    val firstSong = albumSongs.firstOrNull()
                    LibraryGroupItem(
                        title = albumName,
                        subtitle = "Album • ${firstSong?.artist ?: "Unknown Artist"}",
                        audioUri = firstSong?.uri,
                        song = firstSong,
                        onClick = { onViewAlbum(albumName) },
                        isCircular = false,
                        songs = albumSongs,
                        onAddToPlaylist = onAddSongsToPlaylist,
                        onAddToQueue = onAddListToQueue,
                        onAddShuffledToQueue = onAddShuffledToQueue
                    )
                }
            }

            // 2. SHOW MATCHING ARTISTS
            if (matchingArtists.isNotEmpty()) {
                item {
                    SectionHeaderText("Artists")
                }
                items(matchingArtists) { (artistName, artistSongs) ->
                    val firstSong = artistSongs.firstOrNull()
                    LibraryGroupItem(
                        title = artistName,
                        subtitle = "Artist • ${artistSongs.size} songs",
                        audioUri = firstSong?.uri,
                        song = firstSong,
                        onClick = { onViewArtist(artistName) },
                        isCircular = true,
                        songs = artistSongs,
                        onAddToPlaylist = onAddSongsToPlaylist,
                        onAddToQueue = onAddListToQueue,
                        onAddShuffledToQueue = onAddShuffledToQueue
                    )
                }
            }

            // 3. SHOW MATCHING SONGS BELOW
            if (filteredSongs.isNotEmpty()) {
                item {
                    SectionHeaderText("Songs")
                }
                items(filteredSongs, key = { it.id }) { song ->
                    SongItem(
                        song             = song,
                        currentSong      = currentSong,
                        isFavorite       = favoriteIds.contains(song.id),
                        onSongClick      = { onSongClick(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext       = { onPlayNext(song) },
                        onAddToQueue     = { onAddToQueue(song) },
                        onAddToPlaylist  = { onAddToPlaylist(song) },
                        onDelete         = { onDelete(song) },
                        onEdit           = {showSnackbar("Edit functionality coming soon!")},
                        onDownload = { onDownload(song) }  ,
                        onViewAlbum      = { onViewAlbum(song.album ?: "Unknown Album") },
                        onViewArtist     = { onViewArtist(song.artist ?: "Unknown Artist") }
                    )
                }
            }

            if (filteredSongs.isEmpty() && matchingAlbums.isEmpty() && matchingArtists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Results", color = C.label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Try a different spelling or keyword", color = C.secondaryLabel, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderText(title: String) {
    val C = appleColors()
    Text(
        text = title,
        color = C.label,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
