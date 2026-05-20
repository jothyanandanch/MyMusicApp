package com.nandu.mymusic.utils

data class LyricLine(
    val startTimeMs: Long,
    val text: String
)

fun parseLrc(lyricsText: String?): List<LyricLine> {
    if (lyricsText.isNullOrBlank()) return emptyList()

    val parsedLyrics = mutableListOf<LyricLine>()
    // Matches LRC format: [01:23.45] or [01:23.456]
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    lyricsText.lines().forEach { line ->
        val matchResult = regex.find(line)
        if (matchResult != null) {
            val (min, sec, ms, text) = matchResult.destructured

            // Handle 2-digit vs 3-digit milliseconds
            val msVal = if (ms.length == 2) ms.toLong() * 10 else ms.toLong()
            val timeMs = (min.toLong() * 60 * 1000) + (sec.toLong() * 1000) + msVal

            parsedLyrics.add(LyricLine(timeMs, text.trim()))
        }
    }
    return parsedLyrics
}