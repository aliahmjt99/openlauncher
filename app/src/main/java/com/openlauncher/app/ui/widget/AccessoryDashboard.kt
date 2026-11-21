package com.openlauncher.app.ui.widget

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlauncher.app.accessory.AccessorySessions
import com.openlauncher.app.accessory.Wheel
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.TemperatureUnit
import com.openlauncher.app.data.UnitSystem
import kotlin.math.roundToInt

@Composable
internal fun TireDashboard(sessions: AccessorySessions, openSettings: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 24.dp)) {
        Text(sessions.tireStatus, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        for (pair in listOf(listOf(Wheel.FRONT_LEFT, Wheel.FRONT_RIGHT), listOf(Wheel.REAR_LEFT, Wheel.REAR_RIGHT))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (wheel in pair) {
                    val tire = sessions.tires.firstOrNull { it.reading.wheel == wheel }
                    val pressure = when {
                        tire == null -> "—"
                        tire.stale -> "Stale"
                        tire.reading.signalLost -> "Signal lost"
                        else -> "${tire.reading.pressureKpa} kPa"
                    }
                    val alert = tire?.let { !it.stale && (it.reading.leak || it.reading.lowBattery) } == true
                    Text(
                        "${wheel.name.split('_').joinToString("") { it.take(1) }} $pressure",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
            }
        }
        if (openSettings != null && !sessions.tiresRunning) {
            TextButton(onClick = openSettings) { Text("Connect TPMS") }
        }
    }
}

@Composable
internal fun ObdDashboard(sessions: AccessorySessions, settings: AppSettings, openSettings: (() -> Unit)?) {
    val metric = settings.unitSystem == UnitSystem.METRIC
    val sample = sessions.obd
    val speed = sample?.speedKph?.let { if (metric) "$it km/h" else "${(it * 0.621371).roundToInt()} mph" }
    val celsius = when (settings.temperatureUnit) {
        TemperatureUnit.CELSIUS -> true
        TemperatureUnit.FAHRENHEIT -> false
        TemperatureUnit.FOLLOW_DISTANCE -> metric
    }
    val coolant = sample?.coolantCelsius?.let { if (celsius) "$it °C" else "${(it * 1.8 + 32).roundToInt()} °F" }
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 24.dp)) {
        Text(sessions.obdStatus, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text("RPM   ${sample?.rpm ?: "—"}", style = MaterialTheme.typography.bodySmall)
        Text("Speed   ${speed ?: "—"}", style = MaterialTheme.typography.bodySmall)
        Text("Coolant   ${coolant ?: "—"}", style = MaterialTheme.typography.bodySmall)
        if (openSettings != null && !sessions.obdRunning) {
            TextButton(onClick = openSettings) { Text("Connect OBD-II") }
        }
    }
}

@Composable
internal fun QuickToolsWidget(onPhone: (() -> Unit)?, onAccessories: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 22.dp)) {
        TextButton(enabled = onPhone != null, onClick = { onPhone?.invoke() }) { Text("Phone") }
        TextButton(enabled = onAccessories != null, onClick = { onAccessories?.invoke() }) { Text("Accessories") }
    }
}
