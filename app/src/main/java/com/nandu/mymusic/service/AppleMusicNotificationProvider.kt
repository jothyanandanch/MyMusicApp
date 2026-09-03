package com.nandu.mymusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
 * Apple Music-style media notification: dark surface, red accent (#FC3C44),
 * compact play/pause/previous/next actions, album art as large icon.
 *
 * Media3 calls [createNotification] on every player state change; we rebuild
 * the notification each time so the play/pause icon and metadata stay in sync.
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
                "Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Now playing"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
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

        // ── Build the three transport actions via ActionFactory so their
        //    PendingIntents route correctly through the MediaSession. ──
        val prevAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, android.R.drawable.ic_media_previous),
            "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        val playPauseAction = if (isPlaying) {
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, android.R.drawable.ic_media_pause),
                "Pause",
                Player.COMMAND_PLAY_PAUSE
            )
        } else {
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, android.R.drawable.ic_media_play),
                "Play",
                Player.COMMAND_PLAY_PAUSE
            )
        }
        val nextAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, android.R.drawable.ic_media_next),
            "Next",
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )

        // ── NotificationCompat.Builder + MediaStyle for proper media integration ──
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(metadata.title ?: "Unknown Title")
            .setContentText(metadata.artist ?: "Unknown Artist")
            .setSubText(metadata.albumTitle)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentIntent(mediaSession.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setColor(APPLE_MUSIC_RED)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false
}
