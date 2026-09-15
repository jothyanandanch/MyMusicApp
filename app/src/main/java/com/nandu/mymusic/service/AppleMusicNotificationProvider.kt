package com.nandu.mymusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.nandu.mymusic.R

/**
 * Apple Music-style media notification:
 * - Dark surface (#1C1C1E) with red accent (#FC3C44)
 * - Album art as large icon
 * - Custom vector drawable transport icons
 * - Compact view: prev / play-pause / next
 * - Expanded: + favorite (heart, filled when liked) + repeat (cycles
 *   Off → All → One), both via [createCustomAction] and handled in
 *   [handleCustomCommand]
 *
 * NOTE (Android 13+ / ColorOS): SystemUI renders the media notification from
 * the MediaSession itself, so [MusicPlaybackService] publishes these two
 * actions as session custom-layout buttons + platform media-button
 * preferences. The actions added HERE still carry the commands for Android 12
 * and below (and any surface that renders the notification directly).
 */
@UnstableApi
class AppleMusicNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_playback"
        /** Apple Music dark-mode accent red */
        const val APPLE_MUSIC_RED = 0xFC3C44
        /** Apple Music dark background */
        const val APPLE_MUSIC_DARK = 0xFF1C1C1E.toInt()
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Now playing"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Whether the current song's id is in the persisted favorites set. */
    private fun isCurrentSongFavorite(player: Player): Boolean {
        val mediaId = player.currentMediaItem?.mediaId ?: return false
        val favIds = context.getSharedPreferences(
            MusicPlaybackService.PREFS_NAME, Context.MODE_PRIVATE
        ).getStringSet(MusicPlaybackService.KEY_FAV_IDS, emptySet()) ?: emptySet()
        return favIds.contains(mediaId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val isPlaying = player.isPlaying

        // ── Transport actions with custom vector icons ──
        val prevAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, R.drawable.ic_notification_prev),
            "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        val playPauseAction = if (isPlaying) {
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notification_pause),
                "Pause",
                Player.COMMAND_PLAY_PAUSE
            )
        } else {
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notification_play),
                "Play",
                Player.COMMAND_PLAY_PAUSE
            )
        }
        val nextAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, R.drawable.ic_notification_next),
            "Next",
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )

        // ── Favorite (heart): custom action — toggled in handleCustomCommand ──
        val isFavorite = isCurrentSongFavorite(player)
        val favoriteAction = actionFactory.createCustomAction(
            mediaSession,
            IconCompat.createWithResource(
                context,
                if (isFavorite) R.drawable.ic_notification_heart_filled
                else R.drawable.ic_notification_heart_outline
            ),
            if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            MusicPlaybackService.ACTION_TOGGLE_FAVORITE,
            Bundle.EMPTY
        )

        // ── Repeat: custom action — cycles Off → All → One in handleCustomCommand ──
        val repeatMode = player.repeatMode
        val repeatAction = actionFactory.createCustomAction(
            mediaSession,
            IconCompat.createWithResource(
                context,
                if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_notification_repeat_one
                else R.drawable.ic_notification_repeat
            ),
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat One"
                Player.REPEAT_MODE_ALL -> "Repeat All"
                else -> "Repeat"
            },
            MusicPlaybackService.ACTION_CYCLE_REPEAT,
            Bundle.EMPTY
        )

        // ── Load album art bitmap from URI (non-blocking, best-effort) ──
        val albumArtBitmap: Bitmap? = try {
            metadata.artworkUri?.let { uri ->
                if (uri.scheme == "content" || uri.scheme == "file") {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else null
            }
        } catch (_: Exception) { null }

        // ── Build notification (Apple Music layout) ──
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(metadata.title ?: "Unknown Title")
            .setContentText(metadata.artist ?: "Unknown Artist")
            .setSubText(metadata.albumTitle)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentIntent(mediaSession.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setColor(APPLE_MUSIC_RED)
            .setColorized(true)
            .setLargeIcon(albumArtBitmap)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            // Compact view: prev, play/pause, next
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            // Expanded view adds: favorite, repeat — Apple Music layout
            .addAction(favoriteAction)
            .addAction(repeatAction)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    /**
     * Handles the pre-13 notification-action taps. The actual toggle logic is
     * shared with [MusicPlaybackService] (which handles the Android 13+ session
     * command path), so both routes stay identical.
     */
    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        when (action) {
            MusicPlaybackService.ACTION_TOGGLE_FAVORITE -> {
                val player = session.player
                val mediaId = player.currentMediaItem?.mediaId ?: return true
                val prefs = context.getSharedPreferences(
                    MusicPlaybackService.PREFS_NAME, Context.MODE_PRIVATE
                )
                val current = (prefs.getStringSet(
                    MusicPlaybackService.KEY_FAV_IDS, emptySet()
                ) ?: emptySet()).toMutableSet()
                if (current.contains(mediaId)) current.remove(mediaId) else current.add(mediaId)
                prefs.edit().putStringSet(MusicPlaybackService.KEY_FAV_IDS, current).apply()
                MusicPlaybackService.notifyMediaStateChange()
                context.sendBroadcast(android.content.Intent(MusicPlaybackService.ACTION_FAVORITES_CHANGED))
                return true
            }
            MusicPlaybackService.ACTION_CYCLE_REPEAT -> {
                val player = session.player
                // Apple Music cycle: Off → All → One → Off
                val next = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                player.setRepeatMode(next)
                context.getSharedPreferences(
                    MusicPlaybackService.PREFS_NAME, Context.MODE_PRIVATE
                ).edit().putInt(MusicPlaybackService.KEY_REPEAT_MODE, next).apply()
                return true
            }
            else -> return false
        }
    }
}
