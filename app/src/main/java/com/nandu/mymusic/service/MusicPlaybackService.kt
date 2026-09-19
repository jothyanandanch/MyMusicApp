package com.nandu.mymusic.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nandu.mymusic.ui.MainActivity
import java.io.File

/**
 * Thin MediaSessionService around ExoPlayer, matching Apple Music's system
 * integration: the notification, Bluetooth, Assistant and Auto controllers all
 * act on the real player, external queue requests are resolved through
 * [MediaSession.Callback.onAddMediaItems], and playback survives process death
 * via the app's own session restore.
 *
 * Android 13+ (incl. ColorOS): SystemUI renders media controls from the
 * MediaSession custom layout — not from notification actions. The session
 * therefore publishes Favorite + Repeat buttons via [MediaSession.setCustomLayout]
 * and handles them in [MediaSession.Callback.onCustomCommand]. The app process
 * refreshes the icons via [notifyMediaStateChange] when it changes favorites
 * locally, and re-syncs its own heart state from the broadcast in
 * [ACTION_FAVORITES_CHANGED].
 */
@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        // We make the cache static/companion so it survives service restarts
        private var downloadCache: SimpleCache? = null
        // Max cache size: 100 MB (holds about 15-20 high quality songs)
        private const val MAX_CACHE_SIZE = 100 * 1024 * 1024L

        /** Must match MusicViewModel's keys. */
        const val PREFS_NAME = "MyMusicPrefs"
        const val KEY_FAV_IDS = "favorite_song_ids"
        const val KEY_REPEAT_MODE = "repeat_mode_state"

        /** Custom session commands sent by SystemUI media controls (Android 13+). */
        const val ACTION_TOGGLE_FAVORITE = "com.nandu.mymusic.action.TOGGLE_FAVORITE"
        const val ACTION_CYCLE_REPEAT = "com.nandu.mymusic.action.CYCLE_REPEAT"

        /** Broadcast: favorites were toggled from SystemUI — app should re-sync its state. */
        const val ACTION_FAVORITES_CHANGED = "com.nandu.mymusic.action.FAVORITES_CHANGED"

        /** Live reference to the running session so the app can refresh the layout. */
        private var activeSession: MediaSession? = null

        /**
         * Rebuild and push the custom layout (heart + repeat icons) to the
         * session. Called on connect and whenever the state changes. Safe to
         * call from any thread: every Player read below is marshalled onto the
         * app main thread, which is the only thread ExoPlayer may be touched
         * from. Callers include background executor threads (favorites
         * persistence), so reading the player inline here would throw
         * IllegalStateException: Player is accessed on the wrong thread.
         */
        fun refreshCustomLayout() {
            if (Looper.myLooper() == Looper.getMainLooper()) applyCustomLayout()
            else Handler(Looper.getMainLooper()).post { applyCustomLayout() }
        }

        /** Must run on the main thread — reads [Player] state directly. */
        private fun applyCustomLayout() {
            val session = activeSession ?: return
            val appContext = sessionServiceContext ?: return
            val player = session.player
            val favIds = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_FAV_IDS, emptySet()) ?: emptySet()
            session.setCustomLayout(buildCustomLayout(favIds, player.repeatMode, player))
            updatePlatformMediaButtonPreferences(session, appContext)
        }

        private fun buildCustomLayout(
            favIds: Set<String>,
            repeatMode: Int,
            player: Player
        ): List<CommandButton> {
            val isFavorite = player.currentMediaItem?.mediaId?.let { favIds.contains(it) } ?: false
            val heartButton = CommandButton.Builder()
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .setIconResId(
                    if (isFavorite) com.nandu.mymusic.R.drawable.ic_notification_heart_filled
                    else com.nandu.mymusic.R.drawable.ic_notification_heart_outline
                )
                .setDisplayName(
                    if (isFavorite) "Remove from Favorites" else "Add to Favorites"
                )
                .setEnabled(true)
                .build()
            val repeatButton = CommandButton.Builder()
                .setSessionCommand(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                .setIconResId(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> com.nandu.mymusic.R.drawable.ic_notification_repeat_one
                        Player.REPEAT_MODE_ALL -> com.nandu.mymusic.R.drawable.ic_notification_repeat
                        else -> com.nandu.mymusic.R.drawable.ic_notification_repeat_off
                    }
                )
                .setDisplayName(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat One"
                        Player.REPEAT_MODE_ALL -> "Repeat All"
                        else -> "Repeat Off"
                    }
                )
                .setEnabled(true)
                .build()
            return listOf(heartButton, repeatButton)
        }

        /** Context handle registered by onCreate; cleared in onDestroy. */
        @Volatile private var sessionServiceContext: Context? = null

        /** Called by the app (MusicViewModel) when favorites/repeat change. */
        @JvmStatic
        fun notifyMediaStateChange() {
            refreshCustomLayout()
        }

        /**
         * Android 13+ system surfaces (AOSP media controls, ColorOS/realme dynamic
         * capsule) render the **platform** session's media button preferences —
         * a channel Media3 1.x never fills (it only exposes custom layout to Media3
         * controllers + compat PlaybackState). Mirror the heart/repeat buttons onto
         * the platform `android.media.session.MediaSession` Media3 owns internally,
         * found via reflection. Presses come back as custom commands handled in
         * MediaSession.Callback.onCustomCommand with the matching action strings.
         *
         * Invoked reflectively because AGP's core-for-system-modules compile
         * classpath omits Icon.createWithResource / setMediaButtonPreferences;
         * the methods exist on every real API 33+ device at runtime.
         *
         * Strictly best-effort: every failure path is silent and leaves the
         * notification buttons (provider actions + custom layout) working.
         */
        private fun updatePlatformMediaButtonPreferences(
            session: MediaSession,
            appContext: Context
        ) {
            if (android.os.Build.VERSION.SDK_INT < 33) return
            try {
                val platform = resolvePlatformSession(session) ?: return
                val player = session.player
                val favIds = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getStringSet(KEY_FAV_IDS, emptySet()) ?: emptySet()
                val isFavorite = player.currentMediaItem?.mediaId?.let { favIds.contains(it) } ?: false
                val repeatMode = player.repeatMode
                val heartRes =
                    if (isFavorite) com.nandu.mymusic.R.drawable.ic_notification_heart_filled
                    else com.nandu.mymusic.R.drawable.ic_notification_heart_outline
                val repeatRes =
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> com.nandu.mymusic.R.drawable.ic_notification_repeat_one
                        Player.REPEAT_MODE_ALL -> com.nandu.mymusic.R.drawable.ic_notification_repeat
                        else -> com.nandu.mymusic.R.drawable.ic_notification_repeat_off
                    }
                val heartTitle =
                    if (isFavorite) "Remove from Favorites" else "Add to Favorites"
                val repeatTitle = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat One"
                    Player.REPEAT_MODE_ALL -> "Repeat All"
                    else -> "Repeat Off"
                }
                val buttons = listOf(
                    buildMediaButton(appContext, heartRes, ACTION_TOGGLE_FAVORITE, heartTitle),
                    buildMediaButton(appContext, repeatRes, ACTION_CYCLE_REPEAT, repeatTitle)
                ) ?: return
                platform.javaClass
                    .getMethod("setMediaButtonPreferences", List::class.java)
                    .invoke(platform, buttons)
            } catch (_: Throwable) {
                // Older/newer Media3 internals or OEM restriction — keep other paths working.
            }
        }

        /** android.media.MediaButton.Builder(Icon, String, CharSequence).build() via reflection. */
        private fun buildMediaButton(
            context: Context,
            iconResId: Int,
            action: String,
            title: String
        ): Any? {
            val iconClass = Class.forName("android.graphics.Icon")
            val icon = iconClass
                .getMethod("createWithResource", Context::class.java, Int::class.javaPrimitiveType)
                .invoke(null, context, iconResId)
            val builderClass = Class.forName("android.media.MediaButton\$Builder")
            val builder = builderClass
                .getConstructor(iconClass, String::class.java, CharSequence::class.java)
                .newInstance(icon, action, title)
            return builderClass.getMethod("build").invoke(builder)
        }

        @Volatile private var cachedPlatformSession: Any? = null

        /**
         * Walk MediaSession → MediaSessionImpl → MediaSessionLegacyStub →
         * legacy MediaSessionCompat → its Impl → platform android.media.session.MediaSession.
         * Field lookups walk the class hierarchy by name (and the last hop by type)
         * so minor media3 rename drift still resolves.
         */
        private fun resolvePlatformSession(session: MediaSession): Any? {
            cachedPlatformSession?.let { return it }
            return try {
                val impl = getFieldValue(session, "impl") ?: return null
                val legacyStub = getFieldValue(impl, "sessionLegacyStub") ?: return null
                val compat = getFieldValue(legacyStub, "sessionCompat") ?: return null
                val compatImpl = getFieldValue(compat, "mImpl") ?: compat
                val platform = findFieldByTypeName(compatImpl, "android.media.session.MediaSession")
                    ?: return null
                cachedPlatformSession = platform
                platform
            } catch (_: Throwable) {
                null
            }
        }

        private fun getFieldValue(target: Any, name: String): Any? {
            var c: Class<*>? = target.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField(name)
                    f.isAccessible = true
                    val v = f.get(target)
                    if (v != null) return v
                } catch (_: NoSuchFieldException) {
                } catch (_: Throwable) {
                    return null
                }
                c = c.superclass
            }
            return null
        }

        private fun findFieldByTypeName(target: Any, typeName: String): Any? {
            var c: Class<*>? = target.javaClass
            while (c != null) {
                for (f in c.declaredFields) {
                    if (f.type.name == typeName) {
                        try {
                            f.isAccessible = true
                            val v = f.get(target)
                            if (v != null) return v
                        } catch (_: Throwable) {
                        }
                    }
                }
                c = c.superclass
            }
            return null
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionServiceContext = this

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

        // ✨ Wrap ExoPlayer in a ForwardingPlayer to intercept hardware commands
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isBlocked = prefs.getBoolean("is_online_blocked", false)

                // If playback is blocked, drop the command (do nothing)
                if (isBlocked) {
                    return
                }

                // Otherwise, let the player start playing
                super.play()
            }
        }

        // PendingIntent reopens MainActivity when user taps the notification / capsule
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Build MediaSession with the ForwardingPlayer. The callback lets
        // external controllers (Assistant, Auto, wearables) enqueue playable
        // requests instead of having them silently fail, receives the
        // favorite/repeat button commands, and hides nothing from SystemUI.
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                // Grant the two custom commands (favorite + repeat) on top of the
                // defaults, so SystemUI / the media notification controller can
                // dispatch them. Without this the custom-layout buttons render
                // but their taps never reach the session. The layout itself comes
                // from the session (set at build + live updates).
                val sessionCommands = SessionCommands.Builder()
                    .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                    .addSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.commands)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                // Items that already carry a playable URI (all app-initiated
                // playback) pass through untouched. Metadata-only requests from
                // external controllers cannot be resolved by the service and are
                // rejected cleanly instead of failing mid-playback.
                val resolved = mediaItems.filter { it.localConfiguration != null }
                return if (resolved.isEmpty()) {
                    Futures.immediateFailedFuture(
                        IllegalArgumentException("Cannot resolve media items without a URI")
                    )
                } else {
                    Futures.immediateFuture(resolved.toMutableList())
                }
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_TOGGLE_FAVORITE -> {
                        toggleFavoriteForCurrentItem(session)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_CYCLE_REPEAT -> {
                        cycleRepeat(session)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .build()

        activeSession = mediaSession
        refreshCustomLayout()

        // Keep the custom layout in sync with repeat-mode changes made from
        // anywhere (notification, app UI, headset buttons).
        mediaSession?.player?.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                refreshCustomLayout()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Heart state depends on the current song — refresh on song change.
                refreshCustomLayout()
            }
        })

        // Apple Music-style notification: red accent, compact transport controls
        setMediaNotificationProvider(AppleMusicNotificationProvider(this))
    }

    private fun toggleFavoriteForCurrentItem(session: MediaSession) {
        val player = session.player
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_FAV_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (current.contains(mediaId)) current.remove(mediaId) else current.add(mediaId)
        prefs.edit().putStringSet(KEY_FAV_IDS, current).apply()
        refreshCustomLayout()
        // Tell the running app (if any) so its heart state stays in sync.
        sendBroadcast(Intent(ACTION_FAVORITES_CHANGED))
        onUpdateNotificationInternal()
    }

    private fun cycleRepeat(session: MediaSession) {
        val player = session.player
        // Apple Music cycle: Off → All → One → Off
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.setRepeatMode(next)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_REPEAT_MODE, next).apply()
        refreshCustomLayout()
        onUpdateNotificationInternal()
    }

    private fun onUpdateNotificationInternal() {
        mediaSession?.let { session ->
            // Rebuild + re-post the media notification via the provider so icon
            // state updates immediately without foreground-state changes.
            onUpdateNotification(session, /* startInForegroundRequired= */ false)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        activeSession = null
        sessionServiceContext = null
        cachedPlatformSession = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
