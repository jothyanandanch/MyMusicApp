package com.nandu.mymusic.ui.screens.library

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.nandu.mymusic.model.Song
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Small shared helpers used across the library screens (artist parsing,
 * the pushed CollectionView types, downloads, and duration formatting).
 */

fun splitArtists(artistString: String?): List<String> {
    if (artistString.isNullOrBlank()) return listOf("Unknown Artist")
    val parts = artistString.split(
        Regex(",\\s*|\\s*&\\s*|\\s+and\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+x\\s+", RegexOption.IGNORE_CASE)
    )
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(artistString.trim()) }
}

sealed class CollectionView {
    data class Album(val name: String) : CollectionView()
    data class Artist(val name: String) : CollectionView()
    data class Playlist(val name: String, val isMain: Boolean = false) : CollectionView()
    data object LikedSongs : CollectionView()
}

fun downloadSong(context: Context, song: Song, isHighQuality: Boolean, showSnackbar: (String) -> Unit) {
    try {
        var downloadUrl = song.uri.toString()

        if (isHighQuality && downloadUrl.contains("/upload/q_auto,f_auto/")) {
            downloadUrl = downloadUrl.replace("/upload/q_auto,f_auto/", "/upload/")
        } else if (!isHighQuality && !downloadUrl.contains("q_auto,f_auto") && downloadUrl.contains("/upload/")) {
            downloadUrl = downloadUrl.replace("/upload/", "/upload/q_auto,f_auto/")
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(song.title)
            .setDescription("Downloading ${song.title}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "${song.title}.mp3")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        val qualityText = if (isHighQuality) "High Quality" else "Data Saver"
        showSnackbar("Downloading $qualityText...")
    } catch (e: Exception) {
        showSnackbar("Failed to start download")
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
