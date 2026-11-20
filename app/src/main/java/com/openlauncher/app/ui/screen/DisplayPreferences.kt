package com.openlauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.TemperatureUnit
import kotlin.math.roundToInt

@Composable
internal fun DisplayPreferences(settings: AppSettings, update: (AppSettings.() -> AppSettings) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Keep launcher screen on")
        Switch(checked = settings.keepScreenOn,
            onCheckedChange = { enabled -> update { copy(keepScreenOn = enabled) } })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((is24Hour, label) in listOf(true to "24-hour clock", false to "12-hour clock")) {
            FilterChip(selected = settings.use24HourClock == is24Hour,
                onClick = { update { copy(use24HourClock = is24Hour) } },
                label = { Text(label) })
        }
    }
    Text("Weather temperature")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((unit, label) in listOf(
            TemperatureUnit.FOLLOW_DISTANCE to "Match distance units",
            TemperatureUnit.CELSIUS to "°C",
            TemperatureUnit.FAHRENHEIT to "°F"
        )) {
            FilterChip(selected = settings.temperatureUnit == unit,
                onClick = { update { copy(temperatureUnit = unit) } },
                label = { Text(label) })
        }
    }
    BrightnessPreference("Day brightness", settings.dayBrightness) { level -> update { copy(dayBrightness = level) } }
    BrightnessPreference("Night brightness", settings.nightBrightness) { level -> update { copy(nightBrightness = level) } }
}

@Composable
private fun BrightnessPreference(label: String, value: Int, save: (Int) -> Unit) {
    var draft by remember(value) { mutableIntStateOf(value.coerceAtLeast(5)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (value < 0) "$label · system default" else "$label · $draft%")
        Switch(checked = value >= 0, onCheckedChange = { save(if (it) 50 else -1) })
    }
    if (value >= 0) {
        Slider(value = draft.toFloat(), onValueChange = { draft = it.roundToInt() },
            onValueChangeFinished = { save(draft) }, valueRange = 5f..100f)
    }
}
