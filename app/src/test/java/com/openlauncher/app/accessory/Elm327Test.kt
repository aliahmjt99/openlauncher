package com.openlauncher.app.accessory

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327Test {
    @Test
    fun parsesRpmAndSpeedWithEchoAndSearchText() {
        assertEquals(3000, Elm327.parseReading("010C\rSEARCHING...\r41 0C 2E E0\r", "0C", 2)!! / 4)
        assertEquals(88, Elm327.parseReading("410d58", "0D", 1))
        assertEquals(0, Elm327.parseReading("41 0D 00", "0D", 1))
    }

    @Test
    fun readsSnapshotOverTcpAndSendsOnlyExpectedCommands() {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 3_000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val transport = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream()
                        val replies = linkedMapOf(
                            "ATE0" to "OK", "ATH0" to "OK", "ATSP0" to "OK",
                            "010C" to "41 0C 2E E0", "010D" to "41 0D 58"
                        )
                        for ((command, reply) in replies) {
                            assertEquals(command, input.readLine())
                            output.write((reply + "\r>").toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                    }
                }
                val snapshot = Elm327.readSnapshot(InetSocketAddress(loopback, server.localPort))
                transport.get(3, TimeUnit.SECONDS)
                assertEquals(ObdSnapshot(3000, 88), snapshot)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun monitorsTwoSamplesOnOneConnectionAndClosesIt() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 3_000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val transport = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 4_000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream()
                        val responses = listOf(
                            "ATE0" to "OK", "ATH0" to "OK", "ATSP0" to "OK",
                            "010C" to "41 0C 2E E0", "010D" to "41 0D 58", "0105" to "41 05 7D",
                            "010C" to "41 0C 00 00", "010D" to "41 0D 00", "0105" to "NO DATA"
                        )
                        for ((command, reply) in responses) {
                            assertEquals(command, input.readLine())
                            output.write("$reply\r>".toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                        assertNull(input.readLine())
                    }
                }
                val endpoint = InetSocketAddress(loopback, server.localPort)
                val readings = runBlocking { withTimeout(6_000) { Elm327.monitor(endpoint).take(2).toList() } }
                transport.get(5, TimeUnit.SECONDS)
                assertEquals(listOf(ObdSnapshot(3000, 88, 85), ObdSnapshot(0, 0)), readings)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun cancelledMonitorDoesNotFinishInitializationOrEmitSamples() {
        val commands = mutableListOf<String>()
        var reads = 0
        var samples = 0
        runBlocking {
            launch {
                val context = currentCoroutineContext()
                Elm327.poll(
                    readByte = { reads++; '>'.code },
                    write = {
                        commands += String(it, Charsets.US_ASCII)
                        context.cancel()
                    },
                    onSample = { samples++ }
                )
            }.join()
        }
        assertEquals(listOf("ATE0\r"), commands)
        assertEquals(0, reads)
        assertEquals(0, samples)
    }

    @Test
    fun rejectsMissingMalformedAndWrongPidReplies() {
        assertNull(Elm327.parseReading("NO DATA\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0C 2E\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0C ZZ 00\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0D 58\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0D 58 F", "0D", 1))
    }
}
