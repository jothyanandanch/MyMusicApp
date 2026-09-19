package com.nandu.mymusic.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/** Media output route for the Now Playing output button icon. */
enum class AudioRoute { SPEAKER, BLUETOOTH, WIRED }

/**
 * Best-effort snapshot of where media audio currently plays. Cheap; safe to
 * call on every recomposition. Order: Bluetooth → wired headset/speaker →
 * built-in speaker.
 */
fun currentAudioRoute(context: Context): AudioRoute {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return AudioRoute.SPEAKER
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return AudioRoute.SPEAKER
    return when {
        outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        } -> AudioRoute.BLUETOOTH
        outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_DOCK
        } -> AudioRoute.WIRED
        else -> AudioRoute.SPEAKER
    }
}

/**
 * Opens the system media output picker — same UX as JioSaavn / Spotify /
 * YouTube Music. On Android 13+ (realme P3) this uses the system's built-in
 * media output settings dialog which shows all available output devices
 * (speaker, Bluetooth, wired, Chromecast). On older versions it falls back
 * to Bluetooth settings.
 */
fun showMediaOutputPicker(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val intent = Intent("android.settings.MEDIA_OUTPUT_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.provider.extra.MEDIA_OUTPUT_SETTINGS_PACKAGE", context.packageName)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Fall through to Bluetooth settings fallback
        }
    }

    // Fallback: launch Bluetooth settings (older devices / OEM quirks)
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        // No settings activity available
    }
}
