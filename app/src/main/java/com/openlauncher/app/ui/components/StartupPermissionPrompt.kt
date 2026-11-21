package com.openlauncher.app.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun StartupPermissionPrompt(onLocationGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var dismissed by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val locationMissing = remember(refresh) { Build.VERSION.SDK_INT >= 23 &&
        !granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val bluetoothMissing = remember(refresh) { Build.VERSION.SDK_INT >= 31 &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
        !granted(Manifest.permission.BLUETOOTH_CONNECT) }
    val mediaMissing = remember(refresh) {
        context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true) onLocationGranted()
        message = if (result.isNotEmpty() && result.values.none { it }) {
            "Access was declined. You can also manage permissions in Android App info."
        } else null
    }

    if (!dismissed && (locationMissing || bluetoothMissing || mediaMissing)) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("Set up launcher permissions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (locationMissing) {
                        Text("Location enables Live Map and GPS widgets.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { runtime.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                        )) }) { Text("Allow location") }
                    }
                    if (bluetoothMissing) {
                        Text("Nearby devices enables Bluetooth phone and OBD accessories.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { runtime.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }) {
                            Text("Allow nearby devices")
                        }
                    }
                    if (mediaMissing) {
                        Text("Notification access lets the launcher read media sessions for playback controls.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            message = runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }.exceptionOrNull()?.let { "Notification access settings are unavailable on this unit." }
                        }) { Text("Open notification access") }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { dismissed = true }) { Text("Later") } }
        )
    }
}
