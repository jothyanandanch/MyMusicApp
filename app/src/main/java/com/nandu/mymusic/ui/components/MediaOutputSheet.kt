package com.nandu.mymusic.ui.components

import android.Manifest
import android.bluetooth.BluetoothA2dp
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nandu.mymusic.ui.theme.appleColors
import kotlinx.coroutines.delay

/** A Bluetooth audio device shown in the sheet. */
private data class BluetoothOutputUi(
    val device: BluetoothDevice,
    val name: String,
    val connected: Boolean
)

private val BLUETOOTH_CONNECT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

private fun hasBtConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Audio outputs currently known to the system. Returns the paired Bluetooth
 * A2DP devices (with connection state) plus whether the active media output
 * is currently one of them. Safe to call repeatedly; it is cheap.
 */
private data class OutputSnapshot(
    val bluetoothOutputs: List<BluetoothOutputUi>,
    val audioPlayingThroughBluetooth: Boolean,
    val bluetoothAvailable: Boolean
)

private fun readOutputSnapshot(context: Context): OutputSnapshot {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Is media audio currently routed to a Bluetooth sink? We infer it from the
    // outputs the audio stack exposes: a Bluetooth A2DP output in the list means
    // media is (or will be) played there, matching the OS volume panel behaviour.
    val playingThroughBt = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        ?.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } == true

    if (!hasBtConnectPermission(context)) {
        return OutputSnapshot(emptyList(), playingThroughBt, bluetoothAvailable = true)
    }

    return try {
        @Suppress("MissingPermission")
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        // Some devices/stacks throw IllegalArgumentException when a profile
        // (e.g. A2DP) isn't supported — treat that as "no Bluetooth audio".
        @Suppress("MissingPermission")
        val connected = try {
            bluetoothManager?.getConnectedDevices(BluetoothProfile.A2DP).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val adapter = try { bluetoothManager?.adapter } catch (_: Exception) { null }

        val devices = if (adapter == null) {
            emptyList()
        } else {
            @Suppress("MissingPermission")
            val bonded = try { adapter.bondedDevices.orEmpty() } catch (_: Exception) { emptySet() }
            val connectedAddresses = connected.map { it.address }.toSet()
            val relevant = connected + bonded.filter { d ->
                d.address !in connectedAddresses && isAudioCapable(d)
            }
            relevant
                .distinctBy { it.address }
                .sortedWith(compareByDescending<BluetoothDevice> { it.address in connectedAddresses }
                    .thenBy { it.name ?: it.address })
                .map { d ->
                    BluetoothOutputUi(
                        device = d,
                        name = d.name ?: d.address,
                        connected = d.address in connectedAddresses
                    )
                }
        }
        OutputSnapshot(devices, playingThroughBt, bluetoothAvailable = adapter != null)
    } catch (_: Exception) {
        // Bluetooth permission revoked mid-use or stack unavailable.
        OutputSnapshot(emptyList(), playingThroughBt, bluetoothAvailable = true)
    }
}

private fun isAudioCapable(device: BluetoothDevice): Boolean {
    val btClass = device.bluetoothClass ?: return true // unknown → show it
    return btClass.hasService(BluetoothClass.Service.AUDIO) ||
        btClass.deviceClass in intArrayOf(
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
        )
}

/** The current media output route, for the Now Playing output button icon. */
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

/** Best-effort connect/disconnect of the A2DP profile (hidden API via reflection). */
private fun setA2dpConnection(context: Context, device: BluetoothDevice, connect: Boolean) {
    try {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java) ?: return
        val adapter = bluetoothManager.adapter ?: return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) return
                try {
                    val method = proxy.javaClass.getMethod(
                        if (connect) "connect" else "disconnect",
                        BluetoothDevice::class.java
                    )
                    method.invoke(proxy, device)
                } catch (_: Exception) {
                    // Fall back to the system Bluetooth settings if the call is blocked.
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (_: Exception) {
                        // Ignored: no settings activity available.
                    }
                } finally {
                    try { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) } catch (_: Exception) { }
                }
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        @Suppress("MissingPermission")
        adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    } catch (_: Exception) {
        // Profile not supported / adapter unavailable — send the user to Bluetooth settings.
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {
            // Ignored: no settings activity available.
        }
    }
}

/**
 * Apple Music-style output picker. Shows where audio is playing, the paired
 * Bluetooth speakers/headphones (tap to connect), and a shortcut to Bluetooth
 * settings for pairing new devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val C = appleColors()

    var snapshot by remember { mutableStateOf(readOutputSnapshot(context)) }
    var connectingAddress by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> snapshot = readOutputSnapshot(context) }

    // Poll while open: Bluetooth connection state + audio routing change live.
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readOutputSnapshot(context)
            connectingAddress?.let { pending ->
                if (snapshot.bluetoothOutputs.any { it.device.address == pending && it.connected }) {
                    connectingAddress = null
                }
            }
            delay(1000)
        }
    }

    fun onDeviceTap(output: BluetoothOutputUi) {
        if (output.connected) return // already the active Bluetooth output
        if (!hasBtConnectPermission(context)) {
            permissionLauncher.launch(BLUETOOTH_CONNECT_PERMISSION)
            return
        }
        connectingAddress = output.device.address
        setA2dpConnection(context, output.device, connect = true)
    }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        containerColor = Color(0xFF2C2C2E),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
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
                // ── This device ──
                item(key = "this_device") {
                    OutputRow(
                        icon = { tint ->
                            Icon(
                                Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        title = "This Device",
                        subtitle = "Phone speaker",
                        selected = !snapshot.audioPlayingThroughBluetooth,
                        onClick = {
                            // Media is on a Bluetooth device → pull it back to the
                            // speaker by disconnecting the active A2DP output.
                            if (snapshot.audioPlayingThroughBluetooth &&
                                hasBtConnectPermission(context)
                            ) {
                                snapshot.bluetoothOutputs
                                    .filter { it.connected }
                                    .forEach { setA2dpConnection(context, it.device, connect = false) }
                            }
                        }
                    )
                }

                // ── Bluetooth devices ──
                item(key = "bluetooth_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "BLUETOOTH",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                if (!hasBtConnectPermission(context)) {
                    item(key = "bt_permission") {
                        OutputRow(
                            icon = { tint ->
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            title = "Allow Bluetooth access",
                            subtitle = "Needed to list and connect devices",
                            selected = false,
                            onClick = { permissionLauncher.launch(BLUETOOTH_CONNECT_PERMISSION) }
                        )
                    }
                } else if (snapshot.bluetoothOutputs.isEmpty()) {
                    item(key = "bt_empty") {
                        OutputRow(
                            icon = { tint ->
                                Icon(
                                    Icons.Filled.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            title = if (snapshot.bluetoothAvailable) {
                                "No Bluetooth devices"
                            } else {
                                "Bluetooth is off"
                            },
                            subtitle = if (snapshot.bluetoothAvailable) {
                                "Pair a device to play through it"
                            } else {
                                "Turn on Bluetooth to connect a device"
                            },
                            selected = false,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (_: Exception) { /* ignore */ }
                            }
                        )
                    }
                } else {
                    items(count = snapshot.bluetoothOutputs.size) { index ->
                        val output = snapshot.bluetoothOutputs[index]
                        val isActive = output.connected && snapshot.audioPlayingThroughBluetooth
                        OutputRow(
                            icon = { tint ->
                                Icon(
                                    if (output.connected) Icons.Filled.BluetoothConnected
                                    else Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            title = output.name,
                            subtitle = when {
                                isActive -> "Playing here"
                                output.connected -> "Connected"
                                connectingAddress == output.device.address -> "Connecting…"
                                else -> "Not connected"
                            },
                            selected = isActive,
                            showConnectHint = !output.connected,
                            onClick = { onDeviceTap(output) }
                        )
                    }
                }

                // ── Bluetooth settings shortcut ──
                item(key = "bt_settings") {
                    OutputRow(
                        icon = { tint ->
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        title = "Bluetooth Settings",
                        subtitle = "Pair a new device",
                        selected = false,
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) { /* ignore */ }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    showConnectHint: Boolean = false
) {
    val C = appleColors()
    val tint = if (selected) C.accent else Color.White.copy(alpha = 0.75f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = if (selected) 0.9f else 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected output",
                tint = C.accent,
                modifier = Modifier.size(20.dp)
            )
        } else if (showConnectHint) {
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
