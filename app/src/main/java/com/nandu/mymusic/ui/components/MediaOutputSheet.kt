package com.nandu.mymusic.ui.components

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nandu.mymusic.ui.theme.appleColors
import kotlinx.coroutines.delay

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
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        } -> AudioRoute.WIRED
        else -> AudioRoute.SPEAKER
    }
}

// ── Private helpers ──────────────────────────────────────────────────────

private val BT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

private fun hasBtPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, BT_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

private fun isAudioDevice(device: BluetoothDevice): Boolean {
    val cls = device.bluetoothClass ?: return true
    return cls.hasService(BluetoothClass.Service.AUDIO) ||
        cls.deviceClass in intArrayOf(
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
        )
}

/**
 * Pulls Bluetooth MAC addresses from [AudioDeviceInfo] output entries.
 * [AudioManager.getDevices] exposes a device even when the A2DP profile
 * query doesn't return it (BLE speakers, HFP-only, mid-connection, etc.),
 * so this catches the currently-active device that's otherwise invisible.
 */
private fun btMacsFromAudioDevices(context: Context): Set<String> {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptySet()
    val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return emptySet()
    val macs = mutableSetOf<String>()
    outputs.forEach { dev ->
        val type = dev.type
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        ) {
            dev.address?.takeIf { it.isNotBlank() }?.let { macs.add(canonicalizeMac(it)) }
        }
    }
    return macs
}

/** Normalizes a MAC address for case/separator-insensitive comparison. */
private fun canonicalizeMac(mac: String): String =
    mac.lowercase().replace(":", "").replace("-", "").replace(" ", "")

/** Finds a [BluetoothDevice] in [allDevices] whose MAC matches [targetMac]. */
private fun findBtDeviceByMac(allDevices: Collection<BluetoothDevice>, targetMac: String): BluetoothDevice? {
    val norm = canonicalizeMac(targetMac)
    return allDevices.firstOrNull { dev ->
        @Suppress("MissingPermission")
        canonicalizeMac(dev.address) == norm
    }
}

/** Best-effort A2DP connect/disconnect via the profile proxy. */
private fun toggleA2dp(context: Context, device: BluetoothDevice, connect: Boolean) {
    try {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return
        val adapter = manager.adapter ?: return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) return
                try {
                    val m = proxy.javaClass.getMethod(
                        if (connect) "connect" else "disconnect",
                        BluetoothDevice::class.java
                    )
                    m.invoke(proxy, device)
                } catch (_: Exception) {
                    openBtSettings(context)
                } finally {
                    try { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) } catch (_: Exception) { }
                }
            }
            override fun onServiceDisconnected(profile: Int) = Unit
        }
        @Suppress("MissingPermission")
        adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    } catch (_: Exception) {
        openBtSettings(context)
    }
}

/**
 * Disconnects a device from a specific Bluetooth profile.
 * Used when switching from a Bluetooth device (that may not be in the A2DP
 * profile list) back to the phone speaker.
 */
private fun disconnectFromProfile(context: Context, profile: Int, device: BluetoothDevice) {
    try {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return
        val adapter = manager.adapter ?: return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                if (p != profile) return
                try {
                    val m = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    m.invoke(proxy, device)
                } catch (_: Exception) {
                    // fall through
                } finally {
                    try { adapter.closeProfileProxy(profile, proxy) } catch (_: Exception) { }
                }
            }
            override fun onServiceDisconnected(profile: Int) = Unit
        }
        @Suppress("MissingPermission")
        adapter.getProfileProxy(context, listener, profile)
    } catch (_: Exception) {
        // ignore — best effort
    }
}

private fun openBtSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        // No settings activity available
    }
}

/** A single row in the output picker sheet. */
private data class OutputRowData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val isHeader: Boolean = false,
    val needsPermission: Boolean = false,
    val connectHint: Boolean = false
) {
    companion object {
        fun header(title: String) = OutputRowData(
            icon = Icons.Filled.Bluetooth,
            title = title,
            subtitle = "",
            selected = false,
            onClick = {},
            isHeader = true
        )

        fun permissionRequest(title: String, subtitle: String) = OutputRowData(
            icon = Icons.Filled.Bluetooth,
            title = title,
            subtitle = subtitle,
            selected = false,
            onClick = {},
            needsPermission = true
        )
    }
}

// ── Composable ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val C = appleColors()

    fun buildRows(): List<OutputRowData> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val route = currentAudioRoute(ctx)
        val btActive = route == AudioRoute.BLUETOOTH
        val wiredActive = route == AudioRoute.WIRED

        val rows = mutableListOf<OutputRowData>()

        // ── This Phone (built-in speaker) ──
        rows.add(OutputRowData(
            icon = Icons.Filled.PhoneAndroid,
            title = "This Phone",
            subtitle = if (route == AudioRoute.SPEAKER) "Playing here" else "Phone speaker",
            selected = route == AudioRoute.SPEAKER,
            onClick = {
                                // Disconnect all active Bluetooth devices to route back to speaker.
                // The active device may not appear in A2DP getConnectedDevices (BLE speakers,
                // HFP-only headsets), so we cross-reference with AudioDeviceInfo MACs and try
                // disconnecting from both A2DP and HEADSET profiles.
                // After disconnecting, force AudioManager to route back to the speaker.
                if (btActive && hasBtPermission(ctx)) {
                    val bm = ctx.getSystemService(BluetoothManager::class.java)
                    val adapter = bm.adapter
                    val activeMacs = btMacsFromAudioDevices(ctx)
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (activeMacs.isNotEmpty()) {
                        @Suppress("MissingPermission")
                        val paired = try { adapter.getBondedDevices().orEmpty() } catch (_: Exception) { emptySet() }
                        val activeDevices = paired.filter { dev ->
                            activeMacs.any { mac -> canonicalizeMac(dev.address) == canonicalizeMac(mac) }
                        }
                        // Disconnect from A2DP first, then HEADSET (HFP) profile
                        val a2dpConnected = try { bm.getConnectedDevices(BluetoothProfile.A2DP).orEmpty() } catch (_: Exception) { emptyList() }
                        val heConnected = try { bm.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty() } catch (_: Exception) { emptyList() }
                        activeDevices.forEach { dev ->
                            if (dev in a2dpConnected) disconnectFromProfile(ctx, BluetoothProfile.A2DP, dev)
                            else if (dev in heConnected) disconnectFromProfile(ctx, BluetoothProfile.HEADSET, dev)
                            else {
                                // Device is active per AudioDeviceInfo but not in any profile list
                                // — try both profiles
                                disconnectFromProfile(ctx, BluetoothProfile.A2DP, dev)
                                disconnectFromProfile(ctx, BluetoothProfile.HEADSET, dev)
                            }
                        }
                    }
                    // Force audio routing back to the phone speaker.
                    // setBluetoothA2dpOn(false) + setBluetoothScoOn(false) signals the audio
                    // policy to stop routing media to Bluetooth. Starting/stopping SCO
                    // forces a route re-evaluation on the current thread.
                    @Suppress("MissingPermission")
                    am.isBluetoothScoOn = false
                    am.isBluetoothA2dpOn = false
                    am.startBluetoothSco()
                    am.stopBluetoothSco()
                    // Force speaker mode for the media stream
                    am.isSpeakerphoneOn = true
                    am.setBluetoothScoOn(false)
                    am.setBluetoothA2dpOn(false)
                }
                onDismiss()
            }
        ))

        // ── Wired (if connected) ──
        val wiredDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (wiredDevice != null) {
            val label = when (wiredDevice.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
                else -> "Wired Output"
            }
            rows.add(OutputRowData(
                icon = Icons.Filled.Headphones,
                title = label,
                subtitle = "Connected",
                selected = wiredActive,
                onClick = { onDismiss() }
            ))
        }

        // ── Bluetooth section ──
        rows.add(OutputRowData.header("BLUETOOTH"))

        if (!hasBtPermission(ctx)) {
            rows.add(OutputRowData.permissionRequest(
                title = "Allow Bluetooth access",
                subtitle = "Needed to list and connect devices"
            ))
        } else {
            val bm = ctx.getSystemService(BluetoothManager::class.java)
            val adapter = bm.adapter
            if (adapter == null || !adapter.isEnabled) {
                rows.add(OutputRowData(
                    icon = Icons.Filled.BluetoothDisabled,
                    title = "Bluetooth is off",
                    subtitle = "Turn on Bluetooth to connect a device",
                    selected = false,
                    onClick = { openBtSettings(ctx) }
                ))
            } else {
                // Primary source: A2DP profile connected devices.
                val a2dpConnected = try {
                    @Suppress("MissingPermission")
                    bm.getConnectedDevices(BluetoothProfile.A2DP).orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                val a2dpAudio = a2dpConnected.filter { isAudioDevice(it) }

                // Secondary source: Bluetooth MACs from AudioDeviceInfo outputs.
                // AudioManager exposes a device even when the A2DP profile query
                // doesn't return it (BLE speakers, HFP-only, mid-connection),
                // so this catches the currently-active device that's otherwise invisible.
                val audioDeviceMacs = btMacsFromAudioDevices(ctx)

                // Also pull HE (headset) profile devices as a fallback, so wired-style
                // Bluetooth headsets that only advertise HFP are still listed.
                val heConnected = try {
                    @Suppress("MissingPermission")
                    bm.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }

                // Merge everything by MAC, preferring A2DP entries, then HE.
                val allByMac = LinkedHashMap<String, BluetoothDevice>()
                a2dpAudio.forEach { dev ->
                    @Suppress("MissingPermission")
                    allByMac[canonicalizeMac(dev.address)] = dev
                }
                heConnected.forEach { dev ->
                    @Suppress("MissingPermission")
                    val key = canonicalizeMac(dev.address)
                    if (!allByMac.containsKey(key)) allByMac[key] = dev
                }

                // Devices visible in AudioDeviceInfo but missing from BT profiles —
                // these are typically the active device we need to show.
                val missingFromAudio = audioDeviceMacs.filter { mac ->
                    !allByMac.keys.contains(canonicalizeMac(mac))
                }

                val connectedAudio = allByMac.values.toMutableList()
                val paired = try {
                    @Suppress("MissingPermission")
                    adapter.getBondedDevices().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                missingFromAudio.forEach { mac ->
                    val dev = findBtDeviceByMac(paired, mac)
                    if (dev != null && isAudioDevice(dev)) connectedAudio.add(dev)
                }

                if (connectedAudio.isEmpty()) {
                    rows.add(OutputRowData(
                        icon = Icons.Filled.BluetoothDisabled,
                        title = "No connected devices",
                        subtitle = "Pair a device via Bluetooth settings",
                        selected = false,
                        onClick = { openBtSettings(ctx) }
                    ))
                } else {
                    connectedAudio.forEach { dev ->
                        @Suppress("MissingPermission")
                        val name = dev.name ?: dev.address
                        val devMac = canonicalizeMac(dev.address)
                        val active = btActive && (audioDeviceMacs.any { canonicalizeMac(it) == devMac })
                        rows.add(OutputRowData(
                            icon = Icons.Filled.BluetoothConnected,
                            title = name,
                            subtitle = if (active) "Playing here" else "Connected",
                            selected = active,
                            onClick = {
                                if (!active) {
                                    toggleA2dp(ctx, dev, true)
                                } else {
                                    onDismiss()
                                }
                            }
                        ))
                    }
                }
            }
        }

        // ── Pair new device shortcut (JioSaavn-style) ──
        rows.add(OutputRowData(
            icon = Icons.Filled.Bluetooth,
            title = "Pair a new device",
            subtitle = "Connect via Bluetooth settings",
            selected = false,
            onClick = { openBtSettings(ctx); onDismiss() }
        ))

        return rows
    }

    var rows by remember { mutableStateOf(buildRows()) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> rows = buildRows() }

    LaunchedEffect(Unit) {
        while (true) {
            rows = buildRows()
            delay(1000)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF2C2C2E),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Output",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )

            LazyColumn {
                items(rows.size, key = { rows[it].hashCode() }) { i ->
                    val row = rows[i]
                    if (row.isHeader) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.title,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        val tint = if (row.selected) C.accent else Color.White.copy(alpha = 0.75f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = {
                                    if (row.needsPermission) {
                                        permLauncher.launch(BT_PERMISSION)
                                    } else {
                                        row.onClick()
                                    }
                                })
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(34.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = row.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = if (row.selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        row.subtitle,
                                        color = Color.White.copy(alpha = if (row.selected) 0.9f else 0.5f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (row.selected) {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected output", tint = C.accent, modifier = Modifier.size(20.dp))
                                } else if (row.connectHint) {
                                    Text(
                                        "Connect",
                                        color = C.accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
