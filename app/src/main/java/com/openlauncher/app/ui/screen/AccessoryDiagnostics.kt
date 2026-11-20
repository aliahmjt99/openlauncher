package com.openlauncher.app.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openlauncher.app.accessory.Elm327
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale

@Composable
internal fun AccessoryDiagnostics() {
    val context = LocalContext.current
    val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    val usbHost = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    var usbReport by remember { mutableStateOf<String?>(null) }

    Text("Android API ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.joinToString()}")
    Text("USB host advertised by firmware: ${if (usbHost) "Yes" else "No"}")
    OutlinedButton(onClick = {
        usbReport = runCatching {
            usb?.deviceList?.values?.sortedBy { it.deviceId }?.joinToString("\n") { device ->
                String.format(Locale.ROOT, "%04X:%04X · %d interfaces",
                    device.vendorId, device.productId, device.interfaceCount)
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
    var monitorAddress by remember { mutableStateOf<InetSocketAddress?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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

    LaunchedEffect(monitorAddress, lifecycle) {
        val endpoint = monitorAddress ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reading = "Connecting to the OBD-II adapter…"
            try {
                Elm327.monitor(endpoint).collect { sample ->
                    reading = sample.liveDisplay()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                reading = "Live connection ended. Check the adapter, then start again."
                monitorAddress = null
            } catch (_: IllegalStateException) {
                reading = "The adapter did not return a complete ELM327 response."
                monitorAddress = null
            }
        }
    }

    Text("Wi-Fi OBD-II")
    Text("Connect Android to the adapter’s Wi-Fi and use its IPv4 address and port. Test while parked.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host, onValueChange = { host = it }, enabled = monitorAddress == null && !busy,
            label = { Text("Adapter IPv4") }, singleLine = true,
            modifier = Modifier.widthIn(max = 200.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it }, enabled = monitorAddress == null && !busy,
            label = { Text("Port") }, singleLine = true,
            modifier = Modifier.widthIn(max = 110.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && monitorAddress == null, onClick = {
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
        OutlinedButton(enabled = !busy, onClick = {
            if (monitorAddress == null) {
                monitorAddress = address()
                if (monitorAddress != null) reading = "Connecting to the OBD-II adapter…"
            } else {
                monitorAddress = null
                reading = "Live session stopped."
            }
        }) { Text(if (monitorAddress == null) "Start live readings" else "Stop live readings") }
    }
    reading?.let { Text(it) }
    LocalAccessoryTests(usb)
}
