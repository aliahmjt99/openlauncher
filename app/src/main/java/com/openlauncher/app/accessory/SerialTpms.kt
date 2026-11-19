package com.openlauncher.app.accessory

internal enum class Wheel { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, SPARE }

internal data class TireReading(
    val wheel: Wheel,
    val pressureKpa: Int,
    val temperatureC: Int,
    val signalLost: Boolean,
    val leak: Boolean,
    val lowBattery: Boolean
)

/** Serial 55 AA state frames, documented by SamSkjord/TPMS. */
internal class SerialTpms {
    private val frame = ArrayList<Int>(8)

    fun accept(byte: Int): TireReading? {
        if (frame.isEmpty() && byte != 0x55) return null
        if (frame.size == 1 && byte != 0xAA || frame.size == 2 && byte != 8) {
            frame.clear()
            if (byte == 0x55) frame.add(byte)
            return null
        }
        frame.add(byte)
        if (frame.size < 8) return null

        val valid = frame.reduce(Int::xor) == 0
        val wheel = when (frame[3]) {
            0 -> Wheel.FRONT_LEFT
            1 -> Wheel.FRONT_RIGHT
            16 -> Wheel.REAR_LEFT
            17 -> Wheel.REAR_RIGHT
            5 -> Wheel.SPARE
            else -> null
        }
        val pressure = frame[4] * 344 / 100
        val flags = frame[6]
        val result = if (valid && wheel != null) {
            TireReading(
                wheel, pressure - if (pressure > 50) 20 else 0,
                frame[5] - 50, flags and 0x20 != 0, flags and 0x08 != 0, flags and 0x10 != 0
            )
        } else null
        frame.clear()
        if (!valid && byte == 0x55) frame.add(byte)
        return result
    }
}
