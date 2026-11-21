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

internal enum class SerialTpmsProfile(val label: String) {
    CLASSIC("55 AA Classic"),
    DEELIFE("Deelife / MU7J")
}

/** Both profiles share the 55 AA framing, but use distinct pressure and status fields. */
internal class SerialTpms(private val profile: SerialTpmsProfile = SerialTpmsProfile.CLASSIC) {
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

        if (frame.reduce(Int::xor) != 0) {
            // A truncated frame may have consumed the start of the next frame.
            val nextHeader = (1 until frame.size).firstOrNull { index ->
                frame[index] == 0x55 &&
                    (index + 1 == frame.size || frame[index + 1] == 0xAA) &&
                    (index + 2 >= frame.size || frame[index + 2] == 8)
            } ?: frame.size
            frame.subList(0, nextHeader).clear()
            return null
        }
        val wheel = when (frame[3]) {
            0 -> Wheel.FRONT_LEFT
            1 -> Wheel.FRONT_RIGHT
            16 -> Wheel.REAR_LEFT
            17 -> Wheel.REAR_RIGHT
            5 -> Wheel.SPARE
            else -> null
        }
        val raw = frame[4]
        val classicPressure = raw * 344 / 100
        val pressure = when (profile) {
            SerialTpmsProfile.CLASSIC -> classicPressure - if (classicPressure > 50) 20 else 0
            SerialTpmsProfile.DEELIFE -> (raw * 345 + 50) / 100
        }
        val lowBatteryMask = if (profile == SerialTpmsProfile.DEELIFE) 0x01 else 0x10
        val flags = frame[6]
        val result = if (wheel != null) {
            TireReading(
                wheel, pressure, frame[5] - 50,
                profile == SerialTpmsProfile.CLASSIC && flags and 0x20 != 0,
                flags and 0x08 != 0, flags and lowBatteryMask != 0
            )
        } else null
        frame.clear()
        return result
    }
}

internal data class TimedTireReading(val reading: TireReading, val ageSeconds: Long) {
    val stale: Boolean get() = ageSeconds >= 300
}

internal class TireReadings {
    private val latest = mutableMapOf<Wheel, Pair<TireReading, Long>>()

    fun update(reading: TireReading, nowNanos: Long) {
        latest[reading.wheel] = reading to nowNanos
    }

    fun at(nowNanos: Long): List<TimedTireReading> = Wheel.entries.mapNotNull { wheel ->
        latest[wheel]?.let { (reading, lastSeen) ->
            TimedTireReading(reading, (nowNanos - lastSeen).coerceAtLeast(0) / 1_000_000_000L)
        }
    }
}
