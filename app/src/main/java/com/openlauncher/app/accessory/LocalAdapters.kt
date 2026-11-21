package com.openlauncher.app.accessory

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
internal fun readBluetoothObd(device: BluetoothDevice): ObdSnapshot {
    val spp = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    return device.createRfcommSocketToServiceRecord(spp).use { socket ->
        val timer = Executors.newSingleThreadScheduledExecutor()
        timer.schedule({ runCatching { socket.close() } }, 18, TimeUnit.SECONDS)
        try {
            socket.connect()
            val input = socket.inputStream.buffered()
            Elm327.readSnapshot(input::read, socket.outputStream::write)
        } finally {
            timer.shutdownNow()
        }
    }
}

internal inline fun <T> withUsbSerial(
    manager: UsbManager,
    driver: UsbSerialDriver,
    baud: Int,
    read: (UsbSerialPort) -> T
): T {
    val connection = manager.openDevice(driver.device) ?: throw IOException("Could not open USB device")
    try {
        return driver.ports.first().use { port ->
            port.open(connection)
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            read(port)
        }
    } finally {
        connection.close()
    }
}

private class UsbSerialBytes(private val port: UsbSerialPort) {
    private val buffer = ByteArray(256)
    private var index = 0
    private var size = 0

    fun read(): Int {
        if (index == size) {
            size = port.read(buffer, 3_000)
            index = 0
            if (size <= 0) throw IOException("USB adapter read timed out")
        }
        return buffer[index++].toInt() and 0xff
    }

    fun write(bytes: ByteArray) = port.write(bytes, 2_000)
}

internal fun readUsbObd(port: UsbSerialPort): ObdSnapshot =
    UsbSerialBytes(port).let { Elm327.readSnapshot(it::read, it::write) }

internal fun monitorUsbObd(manager: UsbManager, driver: UsbSerialDriver, baud: Int) = flow {
    withUsbSerial(manager, driver, baud) { port ->
        val bytes = UsbSerialBytes(port)
        Elm327.poll(bytes::read, bytes::write) { emit(it) }
    }
}.flowOn(Dispatchers.IO)

@SuppressLint("MissingPermission")
internal fun monitorBluetoothObd(device: BluetoothDevice) = flow {
    val spp = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    device.createRfcommSocketToServiceRecord(spp).use { socket ->
        val timer = Executors.newSingleThreadScheduledExecutor()
        fun closeAfterTimeout() = timer.schedule({ runCatching { socket.close() } }, 5, TimeUnit.SECONDS)
        var watchdog = closeAfterTimeout()
        try {
            socket.connect()
            watchdog.cancel(false)
            val input = socket.inputStream.buffered()
            val output = socket.outputStream
            Elm327.poll(
                readByte = input::read,
                write = {
                    watchdog.cancel(false)
                    watchdog = closeAfterTimeout()
                    output.write(it)
                },
                onSample = { sample -> watchdog.cancel(false); emit(sample) }
            )
        } finally {
            watchdog.cancel(false)
            timer.shutdownNow()
        }
    }
}.flowOn(Dispatchers.IO)

internal fun readSerialTpms(port: UsbSerialPort, profile: SerialTpmsProfile): List<TireReading> {
    // Start the selected 55 AA receiver session with its documented heartbeat.
    if (profile == SerialTpmsProfile.CLASSIC) {
        port.write(byteArrayOf(0x55, 0xAA.toByte(), 0x06, 0x19, 0, 0xE0.toByte()), 1_000)
    }
    val decoder = SerialTpms(profile)
    val buffer = ByteArray(128)
    val wheels = linkedMapOf<Wheel, TireReading>()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
    while (System.nanoTime() < deadline && wheels.size < 5) {
        val count = port.read(buffer, 500)
        for (index in 0 until count) {
            decoder.accept(buffer[index].toInt() and 0xff)?.let { wheels[it.wheel] = it }
        }
    }
    return wheels.values.toList()
}

internal fun monitorSerialTpms(manager: UsbManager, driver: UsbSerialDriver, profile: SerialTpmsProfile) = flow {
    withUsbSerial(manager, driver, 19_200) { port ->
        if (profile == SerialTpmsProfile.CLASSIC) {
            port.write(byteArrayOf(0x55, 0xAA.toByte(), 0x06, 0x19, 0, 0xE0.toByte()), 1_000)
        }
        val decoder = SerialTpms(profile)
        val buffer = ByteArray(128)
        val latest = TireReadings()
        var lastEmission = System.nanoTime()
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = port.read(buffer, 500)
            val now = System.nanoTime()
            for (index in 0 until count) {
                decoder.accept(buffer[index].toInt() and 0xff)?.let {
                    latest.update(it, now)
                }
            }
            if (now - lastEmission >= TimeUnit.SECONDS.toNanos(1)) {
                emit(latest.at(now))
                lastEmission = now
            }
        }
    }
}.flowOn(Dispatchers.IO)
