package com.openlauncher.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.openlauncher.app.accessory.ObdSnapshot
import com.openlauncher.app.accessory.TireReading
import com.openlauncher.app.accessory.Wheel
import com.openlauncher.app.accessory.monitorBluetoothObd
import com.openlauncher.app.accessory.monitorSerialTpms
import com.openlauncher.app.accessory.monitorUsbObd
import com.openlauncher.app.accessory.readBluetoothObd
import com.openlauncher.app.accessory.readSerialTpms
import com.openlauncher.app.accessory.readUsbObd
import com.openlauncher.app.accessory.withUsbSerial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal fun ObdSnapshot.display(): String {
    if (rpm == null && speedKph == null) return "Adapter replied without RPM or speed data."
    return "Engine: ${rpm?.let { "$it RPM" } ?: "unavailable"} · " +
        "Speed: ${speedKph?.let { "$it km/h" } ?: "unavailable"}"
}

internal fun ObdSnapshot.liveDisplay(): String =
    "${display()} · Coolant: ${coolantCelsius?.let { "$it °C" } ?: "unavailable"}"

private fun TireReading.display(): String {
    val name = wheel.name.lowercase().replace('_', ' ')
    if (signalLost) return "$name · signal lost"
    val flags = buildList {
        if (leak) add("receiver reports leak")
        if (lowBattery) add("low sensor battery")
    }
    return (listOf(name, "$pressureKpa kPa", "$temperatureC °C") + flags).joinToString(" · ")
}

@SuppressLint("MissingPermission")
@Composable
internal fun LocalAccessoryTests(usb: UsbManager?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var liveReadings by remember { mutableStateOf<Flow<String>?>(null) }
    val available = liveReadings == null && !busy
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(liveReadings, lifecycle) {
        val session = liveReadings ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            report = "Waiting for data from the selected adapter…"
            try {
                session.collect { report = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                liveReadings = null
                report = "Live session ended. Check the adapter connection or permissions and retry."
            }
        }
    }
    var baud by remember { mutableIntStateOf(38_400) }
    var drivers by remember { mutableStateOf(emptyList<UsbSerialDriver>()) }
    var paired by remember { mutableStateOf(emptyList<BluetoothDevice>()) }

    fun loadPaired() {
        val bluetooth = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        paired = runCatching { bluetooth?.adapter?.bondedDevices.orEmpty().toList() }
            .getOrDefault(emptyList())
        report = if (paired.isEmpty()) "No paired Android Bluetooth devices. Pair the adapter in Android settings." else null
    }

    val requestBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadPaired() else report = "Bluetooth permission was denied."
    }

    fun runTest(read: () -> String) {
        busy = true
        report = null
        scope.launch {
            try {
                report = withContext(Dispatchers.IO) { read() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                report = "Could not read this adapter. Check connection, protocol, and permission."
            } finally {
                busy = false
            }
        }
    }

    fun openSerial(driver: UsbSerialDriver, tpms: Boolean, live: Boolean = false) {
        val manager = usb ?: return
        if (!manager.hasPermission(driver.device)) {
            val action = "${context.packageName}.USB_ACCESSORY_PERMISSION"
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val request = PendingIntent.getBroadcast(
                context, driver.device.deviceId, Intent(action).setPackage(context.packageName), flags
            )
            report = runCatching {
                manager.requestPermission(driver.device, request)
                "Allow USB access, then tap the test again."
            }.getOrDefault("Could not request USB access. Reconnect the adapter and retry.")
            return
        }
        val speed = if (tpms) 19_200 else baud
        if (live) {
            liveReadings = if (tpms) {
                monitorSerialTpms(manager, driver).map { tires ->
                    val byWheel = tires.associateBy { it.reading.wheel }
                    Wheel.entries.filter { it != Wheel.SPARE || it in byWheel }.joinToString("\n") { wheel ->
                        val name = wheel.name.lowercase().replace('_', ' ')
                        val tire = byWheel[wheel]
                        when {
                            tire == null -> "$name · waiting for sensor"
                            tire.stale -> "$name · reading stale · ${tire.ageSeconds}s ago"
                            else -> "${tire.reading.display()} · ${tire.ageSeconds}s ago"
                        }
                    }
                }
            } else monitorUsbObd(manager, driver, speed).map { it.liveDisplay() }
            return
        }
        runTest {
            withUsbSerial(manager, driver, speed) { port ->
                if (!tpms) readUsbObd(port).display()
                else {
                    val tires = readSerialTpms(port)
                    if (tires.isEmpty()) "No 55 AA tire frames received. The receiver may use a different protocol."
                    else tires.joinToString("\n") { it.display() }
                }
            }
        }
    }

    Text("Bluetooth Classic and USB accessories")
    Text("Use explicit adapter selection. Close the vendor TPMS app before testing its receiver.")
    OutlinedButton(enabled = available, onClick = {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else loadPaired()
    }) { Text("List paired Bluetooth devices") }
    for (device in paired) {
        val label = runCatching { device.name }.getOrNull() ?: "Bluetooth device"
        Row {
            OutlinedButton(enabled = available, onClick = {
                runTest { readBluetoothObd(device).display() }
            }) { Text("Test · $label") }
            OutlinedButton(enabled = available, onClick = {
                liveReadings = monitorBluetoothObd(device).map { it.liveDisplay() }
            }) { Text("Live ELM327 · $label") }
        }
    }

    OutlinedButton(enabled = available && usb != null, onClick = {
        drivers = runCatching { UsbSerialProber.getDefaultProber().findAllDrivers(usb!!) }
            .getOrDefault(emptyList())
        report = if (drivers.isEmpty()) "No supported USB serial adapter detected." else null
    }) { Text("Find USB serial adapters") }
    if (drivers.isNotEmpty()) {
        Text("USB OBD-II baud rate")
        Row {
            for (rate in listOf(9_600, 38_400, 115_200)) {
                FilterChip(selected = baud == rate, enabled = available, onClick = { baud = rate },
                    label = { Text(rate.toString()) })
            }
        }
    }
    for (driver in drivers) {
        val device = driver.device
        Text(String.format(Locale.ROOT, "USB serial %04X:%04X", device.vendorId, device.productId))
        Row {
            OutlinedButton(enabled = available, onClick = { openSerial(driver, tpms = false) }) {
                Text("Test USB ELM327")
            }
            OutlinedButton(enabled = available, onClick = { openSerial(driver, tpms = true) }) {
                Text("Sample 55 AA TPMS")
            }
        }
        Row {
            OutlinedButton(enabled = available, onClick = { openSerial(driver, tpms = false, live = true) }) {
                Text("Live USB ELM327")
            }
            OutlinedButton(enabled = available, onClick = { openSerial(driver, tpms = true, live = true) }) {
                Text("Live 55 AA TPMS")
            }
        }
    }
    if (liveReadings != null) {
        OutlinedButton(onClick = {
            liveReadings = null
            report = "Live session stopped."
        }) { Text("Stop accessory session") }
    }
    if (busy) Text("Reading adapter…")
    report?.let { Text(it) }
}
