package com.openlauncher.app.accessory

import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket

internal data class ObdSnapshot(val rpm: Int?, val speedKph: Int?)

internal object Elm327 {
    fun readSnapshot(address: InetSocketAddress): ObdSnapshot = Socket().use { socket ->
        socket.connect(address, 2_000)
        socket.soTimeout = 3_000
        val input = socket.getInputStream().buffered()
        val output = socket.getOutputStream()
        readSnapshot(input::read) { output.write(it); output.flush() }
    }

    fun readSnapshot(readByte: () -> Int, write: (ByteArray) -> Unit): ObdSnapshot {
        fun query(command: String): String {
            write((command + "\r").toByteArray(Charsets.US_ASCII))
            val response = StringBuilder()
            val deadline = System.nanoTime() + 4_000_000_000L
            while (response.length < 2048 && System.nanoTime() < deadline) {
                val next = readByte()
                if (next == -1) throw EOFException("Adapter disconnected")
                if (next == '>'.code) return response.toString()
                response.append(next.toChar())
            }
            error("Adapter response exceeded the time or size limit")
        }

        query("ATE0")
        query("ATH0")
        query("ATSP0")
        val rpm = parseReading(query("010C"), "0C", 2)?.div(4)
        val speed = parseReading(query("010D"), "0D", 1)
        return ObdSnapshot(rpm, speed)
    }

    internal fun parseReading(response: String, pid: String, bytes: Int): Int? {
        val prefix = "41" + pid
        return response.lineSequence()
            .map { it.filterNot(Char::isWhitespace).uppercase() }
            .firstNotNullOfOrNull { line ->
                if (line.startsWith(prefix) && line.length >= prefix.length + bytes * 2 &&
                    line.all { it.isDigit() || it in 'A'..'F' }
                ) {
                    line.substring(prefix.length, prefix.length + bytes * 2).toIntOrNull(16)
                } else null
            }
    }
}
