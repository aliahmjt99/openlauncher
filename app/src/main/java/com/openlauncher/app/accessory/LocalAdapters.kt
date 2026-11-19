package com.openlauncher.app.accessory

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
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

internal fun <T> withUsbSerial(
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

internal fun readUsbObd(port: UsbSerialPort): ObdSnapshot {
    val buffer = ByteArray(256)
    var index = 0
    var size = 0
    return Elm327.readSnapshot(
        readByte = {
            if (index == size) {
                size = port.read(buffer, 3_000)
                index = 0
                if (size <= 0) throw IOException("USB adapter read timed out")
            }
            buffer[index++].toInt() and 0xff
        },
        write = { port.write(it, 2_000) }
    )
}

internal fun readSerialTpms(port: UsbSerialPort): List<TireReading> {
    // Start the selected 55 AA receiver session with its documented heartbeat.
    port.write(byteArrayOf(0x55, 0xAA.toByte(), 0x06, 0x19, 0, 0xE0.toByte()), 1_000)
    val decoder = SerialTpms()
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
