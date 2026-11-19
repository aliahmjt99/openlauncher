package com.openlauncher.app.accessory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerialTpmsTest {
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
}
