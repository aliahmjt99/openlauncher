package com.openlauncher.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.openlauncher.app.model.AppInfo
import com.openlauncher.app.model.NowPlayingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class BluetoothStatus(
    val message: String,
    val paired: List<String> = emptyList(),
    val headsetConnected: Boolean = false,
    val audioConnected: Boolean = false
)

@SuppressLint("MissingPermission")
private fun readBluetoothStatus(context: Context): BluetoothStatus {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        ?: return BluetoothStatus("Android Bluetooth is unavailable on this head unit.")
    return try {
        if (!adapter.isEnabled) return BluetoothStatus("Android Bluetooth is off.")
        val paired = adapter.bondedDevices.map { it.name?.takeIf(String::isNotBlank) ?: it.address }.sorted()
        BluetoothStatus(
            "Android Bluetooth is on.", paired,
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED,
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
        )
    } catch (_: SecurityException) {
        BluetoothStatus("Bluetooth access is unavailable. Check Android permissions.")
    } catch (_: IllegalStateException) {
        BluetoothStatus("Android Bluetooth is starting or unavailable.")
    }
}

@Composable
internal fun BluetoothPhonePanel(
    apps: List<AppInfo>,
    selectedPackage: String,
    onSelect: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onReloadApps: () -> Unit,
    media: NowPlayingState?
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    var allowed by remember { mutableStateOf(hasBluetoothPermission()) }
    var status by remember { mutableStateOf<BluetoothStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var choosingApp by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = hasBluetoothPermission()
        error = if (it) null else "Bluetooth permission was declined."
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val granted = hasBluetoothPermission()
                allowed = granted
                status = if (granted) withContext(Dispatchers.IO) { readBluetoothStatus(context) } else null
                delay(3_000)
            }
        }
    }

    fun openSettings(action: String) {
        error = try {
            context.startActivity(Intent(action))
            null
        } catch (_: ActivityNotFoundException) {
            "This Android settings screen is unavailable on this head unit."
        } catch (_: SecurityException) {
            "Android blocked this settings screen."
        }
    }

    Text("Phone connection", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }) {
        Text("Open Android pairing settings")
    }
    if (!allowed && Build.VERSION.SDK_INT >= 31) {
        OutlinedButton(onClick = { requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
            Text("Allow Bluetooth status")
        }
    }
    status?.let { bt ->
        Text(bt.message)
        if (bt.paired.isNotEmpty()) Text("Android paired: ${bt.paired.joinToString()}")
        if (bt.headsetConnected) Text("Android headset profile connected")
        if (bt.audioConnected) Text("Android audio profile connected")
    }

    val label = apps.firstOrNull { it.packageName == selectedPackage }?.appName ?: selectedPackage
    Text(if (selectedPackage.isEmpty()) "Head unit phone app not selected" else "Head unit phone app · $label")
    OutlinedButton(onClick = { query = ""; onReloadApps(); choosingApp = true }) { Text("Choose head unit phone app") }
    if (selectedPackage.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onLaunch(selectedPackage) }) { Text("Open phone app") }
            TextButton(onClick = { onSelect("") }) { Text("Clear app") }
        }
        Text("Use the head unit phone app for call management, synced contacts, and call history.")
    }

    val controller = media?.controller
    val playback = controller?.playbackState
    if (media != null && controller != null && playback != null) {
        val source = apps.firstOrNull { it.packageName == controller.packageName }?.appName ?: controller.packageName
        val actions = playback.actions
        val toggle = if (media.isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        Text("Media · $source", style = MaterialTheme.typography.titleMedium)
        if (media.title.isNotBlank()) Text(media.title)
        if (media.artist.isNotBlank()) Text(media.artist)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val controls = controller.transportControls
            OutlinedButton(enabled = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                onClick = { controls.skipToPrevious() }) { Text("Previous") }
            OutlinedButton(enabled = actions and (toggle or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
                onClick = { if (media.isPlaying) controls.pause() else controls.play() }) {
                Text(if (media.isPlaying) "Pause" else "Play")
            }
            OutlinedButton(enabled = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                onClick = { controls.skipToNext() }) { Text("Next") }
        }
    } else {
        Text("Media controls appear when Android can see an active media session.")
        TextButton(onClick = { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) {
            Text("Open media access settings")
        }
    }
    error?.let { Text(it) }

    if (choosingApp) {
        AlertDialog(
            onDismissRequest = { choosingApp = false },
            title = { Text("Choose head unit phone app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(80) }, label = { Text("Find an app") }, singleLine = true)
                    val options = apps.filter { it.packageName != context.packageName &&
                        (it.appName.contains(query.trim(), true) || it.packageName.contains(query.trim(), true)) }
                    if (options.isEmpty()) Text("No matching apps.")
                    LazyColumn(Modifier.heightIn(max = 230.dp)) {
                        items(options, key = { it.packageName }) { app ->
                            TextButton(onClick = { onSelect(app.packageName); choosingApp = false }) {
                                Text("${app.appName}\n${app.packageName}")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingApp = false }) { Text("Cancel") } }
        )
    }
}
