package com.openlauncher.app.accessory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerialTpmsTest {
    @Test
    fun deelifeUsesItsOwnPressureCalibrationAndBatteryFlag() {
        val frame = intArrayOf(0x55, 0xAA, 8, 1, 0x47, 0x47, 1, 0xF7)
        val deelife = SerialTpms(SerialTpmsProfile.DEELIFE)
        val classic = SerialTpms(SerialTpmsProfile.CLASSIC)
        assertEquals(TireReading(Wheel.FRONT_RIGHT, 245, 21, false, false, true),
            frame.asSequence().mapNotNull(deelife::accept).single())
        assertEquals(TireReading(Wheel.FRONT_RIGHT, 224, 21, false, false, false),
            frame.asSequence().mapNotNull(classic::accept).single())
    }

    @Test
    fun wheelAgesIndependentlyAndFreshPacketsReplaceStaleReadings() {
        val readings = TireReadings()
        val front = TireReading(Wheel.FRONT_LEFT, 225, 20, false, false, false)
        val rear = front.copy(wheel = Wheel.REAR_RIGHT)
        readings.update(front, 0L)
        readings.update(rear, 299_000_000_000L)
        assertEquals(listOf(true, false), readings.at(300_000_000_000L).map { it.stale })
        val updated = front.copy(pressureKpa = 230)
        readings.update(updated, 301_000_000_000L)
        assertEquals(TimedTireReading(updated, 0), readings.at(301_000_000_000L).first())
    }

    @Test
    fun readsFragmentedRearLeftFrameAndFlags() {
        val decoder = SerialTpms()
        val frame = intArrayOf(0x55, 0xAA, 8, 16, 75, 72, 0x18, 0xFC)
        frame.dropLast(1).forEach { assertNull(decoder.accept(it)) }
        assertEquals(
            TireReading(Wheel.REAR_LEFT, 238, 22, false, true, true),
            decoder.accept(frame.last())
        )
    }

    @Test
    fun rejectsBadChecksumAndResynchronizesAfterNoise() {
        val decoder = SerialTpms()
        val candidate = intArrayOf(0x55, 0xAA, 8, 16, 75, 72, 0x18)
        (intArrayOf(0, 0x55, 0x55, 1) + candidate + 0).forEach {
            assertNull(decoder.accept(it))
        }
        candidate.forEach { assertNull(decoder.accept(it)) }
        assertEquals(Wheel.REAR_LEFT, decoder.accept(0xFC)?.wheel)
    }
    @Test
    fun keepsNextFrameWhenATruncatedPacketConsumesItsHeader() {
        val valid = intArrayOf(0x55, 0xAA, 8, 16, 75, 72, 0x18, 0xFC)
        for (length in 3..7) {
            val decoder = SerialTpms()
            // Change the partial packet's wheel so its checksum cannot validate
            // when the next frame's bytes fill the remaining positions.
            val truncated = valid.copyOf(length).apply { if (length > 3) this[3] = 1 }
            val output = (truncated + valid).asSequence().mapNotNull { decoder.accept(it) }
            assertEquals(listOf(Wheel.REAR_LEFT), output.map { it.wheel }.toList())
        }
    }
}
