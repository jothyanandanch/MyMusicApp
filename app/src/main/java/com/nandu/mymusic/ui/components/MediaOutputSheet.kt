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
                // Disconnect any active Bluetooth A2DP to route back to speaker
                if (btActive && hasBtPermission(ctx)) {
                    val bm = ctx.getSystemService(BluetoothManager::class.java)
                    val adapter = bm.adapter
                    @Suppress("MissingPermission")
                    val connected = try { bm.getConnectedDevices(BluetoothProfile.A2DP) }
                    catch (_: Exception) { emptyList() }
                    connected.forEach { toggleA2dp(ctx, it, false) }
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
                @Suppress("MissingPermission")
                val bonded = adapter.bondedDevices.orEmpty().toList()
                val audioDevices = bonded.filter { isAudioDevice(it) }

                if (audioDevices.isEmpty()) {
                    rows.add(OutputRowData(
                        icon = Icons.Filled.BluetoothDisabled,
                        title = "No Bluetooth devices",
                        subtitle = "Pair a device to play through it",
                        selected = false,
                        onClick = { openBtSettings(ctx) }
                    ))
                } else {
                    audioDevices.forEach { dev ->
                        @Suppress("MissingPermission")
                        val name = dev.name ?: dev.address
                        val conn = try {
                            @Suppress("MissingPermission")
                            val c = bm.getConnectedDevices(BluetoothProfile.A2DP)
                            @Suppress("MissingPermission")
                            c.any { it.address == dev.address }
                        } catch (_: Exception) { false }
                        val active = btActive && conn
                        rows.add(OutputRowData(
                            icon = if (conn) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                            title = name,
                            subtitle = when {
                                active -> "Playing here"
                                conn -> "Connected"
                                else -> "Not connected"
                            },
                            selected = active,
                            onClick = {
                                if (!conn) {
                                    toggleA2dp(ctx, dev, true)
                                } else {
                                    onDismiss()
                                }
                            },
                            connectHint = conn && !active
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
