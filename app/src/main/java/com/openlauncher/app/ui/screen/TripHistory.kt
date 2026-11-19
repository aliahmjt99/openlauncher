package com.openlauncher.app.ui.screen

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.openlauncher.app.data.TripEntry
import com.openlauncher.app.data.UnitSystem
import java.text.DateFormat
import java.util.Date

@Composable
internal fun TripHistory(trips: List<TripEntry>, units: UnitSystem) {
    var visibleCount by remember { mutableIntStateOf(10) }
    Text("Pause the trip meter, then tap SAVE to archive its distance and time.")
    if (trips.isEmpty()) {
        Text("No saved trips.")
        return
    }

    val metric = units == UnitSystem.METRIC
    val distanceDivisor = if (metric) 1_000.0 else 1_609.344
    val distanceUnit = if (metric) "km" else "mi"
    val speedMultiplier = if (metric) 3.6 else 2.23693629
    val speedUnit = if (metric) "km/h" else "mph"
    val totalDistance = trips.sumOf { it.distanceMeters } / distanceDivisor
    Text("${trips.size} saved trips · ${"%.1f".format(totalDistance)} $distanceUnit")

    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    for (trip in trips.take(visibleCount)) {
        val distance = "%.2f".format(trip.distanceMeters / distanceDivisor)
        val speed = "%.1f".format(trip.averageMovingSpeedMps * speedMultiplier)
        Text(dateFormat.format(Date(trip.savedAt)))
        Text("$distance $distanceUnit · Avg $speed $speedUnit · " +
            "${trip.movingSeconds.durationLabel()} moving · ${trip.idleSeconds.durationLabel()} idle")
    }
    if (trips.size > visibleCount) {
        OutlinedButton(onClick = { visibleCount += 10 }) { Text("Show more trips") }
    }
}

private fun Long.durationLabel(): String = "%d:%02d:%02d".format(this / 3600, this / 60 % 60, this % 60)
