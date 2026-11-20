package com.openlauncher.app.data


enum class VehicleLogType(val label: String) {
    FUEL("Fuel"), CHARGE("EV charge"), SERVICE("Service")
}

data class VehicleLogEntry(
    val type: VehicleLogType,
    val label: String,
    val cost: String,
    val createdAt: Long = System.currentTimeMillis(),
    val serviceIntervalDays: Int? = null
)
