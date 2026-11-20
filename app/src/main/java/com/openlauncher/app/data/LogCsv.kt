package com.openlauncher.app.data

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class LogCsv(private val writer: Writer) {
    private val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun trips(entries: List<TripEntry>) {
        row("saved_at_utc", "distance_m", "moving_seconds", "idle_seconds", "average_moving_speed_mps")
        for (entry in entries) {
            row(utc.format(Date(entry.savedAt)), entry.distanceMeters, entry.movingSeconds,
                entry.idleSeconds, entry.averageMovingSpeedMps)
        }
    }

    fun vehicle(entries: List<VehicleLogEntry>) {
        row("created_at_utc", "type", "description", "cost", "service_interval_days")
        for (entry in entries) {
            row(utc.format(Date(entry.createdAt)), entry.type.name, safeText(entry.label),
                entry.cost.toBigDecimalOrNull()?.toPlainString().orEmpty(), entry.serviceIntervalDays ?: "")
        }
    }

    private fun row(vararg cells: Any) {
        writer.write(cells.joinToString(",") { cell ->
            "\"" + cell.toString().replace("\"", "\"\"") + "\""
        })
        writer.write("\r\n")
    }

    private fun safeText(value: String): String {
        val first = value.trimStart().firstOrNull()
        return if (first != null && first in "=+-@" || value.startsWith('\t') ||
            value.startsWith('\r') || value.startsWith('\n')) "'$value" else value
    }
}
