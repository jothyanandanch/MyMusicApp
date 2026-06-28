package com.nandu.mymusic.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nandu.mymusic.ui.MainActivity
import java.io.File

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        // We make the cache static/companion so it survives service restarts
        private var downloadCache: SimpleCache? = null
        // Max cache size: 100 MB (holds about 15-20 high quality songs)
        private const val MAX_CACHE_SIZE = 100 * 1024 * 1024L
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize the Cache Engine (Only once!)
        if (downloadCache == null) {
            val cacheDir = File(cacheDir, "media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(this)
            downloadCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }

        // 2. Build the Caching Data Source
        val upstreamFactory = DefaultDataSource.Factory(this)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache!!)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 3. Build ExoPlayer
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true) // pauses on headphone unplug
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .build()

        // ✨ NEW: Wrap ExoPlayer in a ForwardingPlayer to intercept hardware commands
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                val prefs = getSharedPreferences("MyMusicPrefs", Context.MODE_PRIVATE)
                val isBlocked = prefs.getBoolean("is_online_blocked", false)

                // If playback is blocked, drop the command (do nothing)
                if (isBlocked) {
                    return
                }

                // Otherwise, let the player start playing
                super.play()
            }
        }

        // PendingIntent reopens MainActivity when user taps the notification
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Build MediaSession and pass the ForwardingPlayer instead of the raw ExoPlayer
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}