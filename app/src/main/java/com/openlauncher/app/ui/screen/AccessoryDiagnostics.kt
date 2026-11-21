package com.openlauncher.app.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openlauncher.app.accessory.AccessorySessions
import com.openlauncher.app.accessory.Elm327
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale

@Composable
internal fun AccessoryScreen(sessions: AccessorySessions, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Dashboard") }
            Text("Accessories", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        }
        SettingsSection("Adapter connections") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) { AccessoryDiagnostics(sessions) }
            }
        }
    }
}

@Composable
private fun AccessoryDiagnostics(sessions: AccessorySessions) {
    val context = LocalContext.current
    val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    val usbHost = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    var usbReport by remember { mutableStateOf<String?>(null) }

    Text("Android API ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.joinToString()}")
    Text("USB host advertised by firmware: ${if (usbHost) "Yes" else "No"}")
    OutlinedButton(onClick = {
        usbReport = runCatching {
            usb?.deviceList?.values?.sortedBy { it.deviceId }?.joinToString("\n") { device ->
                val interfaces = (0 until device.interfaceCount).joinToString(", ") { index ->
                    val usbInterface = device.getInterface(index)
                    "${usbInterface.interfaceClass}/${usbInterface.interfaceSubclass}/${usbInterface.interfaceProtocol}"
                }
                String.format(Locale.ROOT, "%04X:%04X · interface class/subclass/protocol: %s",
                    device.vendorId, device.productId, interfaces.ifBlank { "none" })
            }?.ifBlank { "No USB accessories detected" } ?: "USB service unavailable"
        }.getOrDefault("Could not enumerate USB accessories")
    }) {
        Text("Scan USB accessories")
    }
    usbReport?.let { Text(it) }

    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("192.168.0.10") }
    var port by remember { mutableStateOf("35000") }
    var reading by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val wifiRunning = sessions.obdRunning && !sessions.obdUsesLocalAdapter

    fun address(): InetSocketAddress? {
        val octets = host.trim().split('.').map { it.toIntOrNull() }
        val tcpPort = port.trim().toIntOrNull()
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 } ||
            tcpPort == null || tcpPort !in 1..65535
        ) {
            reading = "Enter a valid IPv4 address and port."
            return null
        }
        val ip = InetAddress.getByAddress(octets.map { requireNotNull(it).toByte() }.toByteArray())
        return InetSocketAddress(ip, tcpPort)
    }

    Text("Wi-Fi OBD-II")
    Text("Connect Android to the adapter’s Wi-Fi and use its IPv4 address and port. Test while parked.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host, onValueChange = { host = it }, enabled = !sessions.obdRunning && !busy,
            label = { Text("Adapter IPv4") }, singleLine = true,
            modifier = Modifier.widthIn(max = 200.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it }, enabled = !sessions.obdRunning && !busy,
            label = { Text("Port") }, singleLine = true,
            modifier = Modifier.widthIn(max = 110.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && !sessions.obdRunning, onClick = {
            val endpoint = address()
            if (endpoint != null) {
                busy = true
                reading = null
                scope.launch {
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            Elm327.readSnapshot(endpoint)
                        }
                        reading = snapshot.display()
                    } catch (_: IOException) {
                        reading = "Could not read the adapter. Check its power, Wi-Fi, IP and port."
                    } catch (_: IllegalStateException) {
                        reading = "The adapter did not return a complete ELM327 response."
                    } finally {
                        busy = false
                    }
                }
            }
        }) {
            Text(if (busy) "Reading adapter…" else "Read RPM and speed")
        }
        OutlinedButton(enabled = !busy && !sessions.obdUsesLocalAdapter, onClick = {
            if (wifiRunning) sessions.stopObd()
            else address()?.let {
                reading = null
                sessions.startObd(Elm327.monitor(it), "Wi-Fi ELM327")
            }
        }) { Text(if (wifiRunning) "Stop live readings" else "Start live readings") }
    }
    reading?.let { Text(it) }
    if (!sessions.obdUsesLocalAdapter) Text(sessions.obd?.liveDisplay() ?: sessions.obdStatus)
    LocalAccessoryTests(usb, sessions)
}
