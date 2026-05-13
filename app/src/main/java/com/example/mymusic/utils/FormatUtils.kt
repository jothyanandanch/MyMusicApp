package com.example.mymusic.utils

import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
