package com.openlauncher.app.accessory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket

internal data class ObdSnapshot(val rpm: Int?, val speedKph: Int?, val coolantCelsius: Int? = null)

internal object Elm327 {
    fun readSnapshot(address: InetSocketAddress): ObdSnapshot = Socket().use { socket ->
        socket.connect(address, 2_000)
        socket.soTimeout = 3_000
        val input = socket.getInputStream().buffered()
        val output = socket.getOutputStream()
        readSnapshot(input::read) { output.write(it); output.flush() }
    }

    fun readSnapshot(readByte: () -> Int, write: (ByteArray) -> Unit): ObdSnapshot =
        Session(readByte, write).apply { initialize() }.sample()

    fun monitor(address: InetSocketAddress) = flow {
        Socket().use { socket ->
            socket.connect(address, 2_000)
            socket.soTimeout = 3_000
            val input = socket.getInputStream().buffered()
            val output = socket.getOutputStream()
            val session = Session(input::read) { bytes -> output.write(bytes); output.flush() }
            session.initialize()
            while (true) {
                emit(session.sample(includeCoolant = true))
                delay(1_000)
            }
        }
    }.flowOn(Dispatchers.IO)

    private class Session(val readByte: () -> Int, val write: (ByteArray) -> Unit) {
        private fun query(command: String): String {
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

        fun initialize() {
            query("ATE0")
            query("ATH0")
            query("ATSP0")
        }

        fun sample(includeCoolant: Boolean = false): ObdSnapshot {
            val rpm = parseReading(query("010C"), "0C", 2)?.div(4)
            val speed = parseReading(query("010D"), "0D", 1)
            val coolant = if (includeCoolant) parseReading(query("0105"), "05", 1)?.minus(40) else null
            return ObdSnapshot(rpm, speed, coolant)
        }
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
