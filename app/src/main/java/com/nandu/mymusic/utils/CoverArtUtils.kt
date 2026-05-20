package com.nandu.mymusic.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CoverArtUtils {
    /**
     * Fetches the Album Art URL from the free iTunes Search API safely.
     */
    suspend fun fetchAlbumArtUrl(title: String, artist: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Clean the title (e.g., remove "(Official Video)", "[Lyric Video]")
                val cleanTitle = title.replace(Regex("(?i)\\(.*?video.*?\\)|\\(.*?lyrics.*?\\)|\\[.*?]"), "").trim()
                val cleanArtist = if (artist.contains("Unknown", ignoreCase = true)) "" else artist.trim()

                // Combine title and artist for a highly accurate search
                val queryTerm = "$cleanTitle $cleanArtist".trim()
                if (queryTerm.isEmpty()) return@withContext null

                val query = URLEncoder.encode(queryTerm, "UTF-8")
                val urlString = "https://itunes.apple.com/search?term=$query&entity=song&limit=1"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000

                // 2. Only parse JSON if the request was actually successful
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val results = json.getJSONArray("results")

                    if (results.length() > 0) {
                        val track = results.getJSONObject(0)
                        val artworkUrl = track.optString("artworkUrl100", "")

                        if (artworkUrl.isNotEmpty()) {
                            // Convert 100x100 to beautiful 500x500 image
                            return@withContext artworkUrl.replace("100x100bb", "500x500bb")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}