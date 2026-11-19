package com.openlauncher.app.accessory

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
    fun rejectsMissingMalformedAndWrongPidReplies() {
        assertNull(Elm327.parseReading("NO DATA\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0C 2E\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0C ZZ 00\r", "0C", 2))
        assertNull(Elm327.parseReading("41 0D 58\r", "0C", 2))
    }
}
