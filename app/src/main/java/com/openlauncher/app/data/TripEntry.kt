package com.openlauncher.app.data

data class TripEntry(
    val distanceMeters: Double,
    val movingSeconds: Long,
    val idleSeconds: Long,
    val averageMovingSpeedMps: Double,
    val savedAt: Long = System.currentTimeMillis()
)
