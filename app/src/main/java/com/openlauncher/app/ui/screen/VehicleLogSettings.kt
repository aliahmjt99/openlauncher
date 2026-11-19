package com.openlauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.VehicleLogEntry
import com.openlauncher.app.data.VehicleLogType
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
internal fun VehicleLogSettings(
    entries: List<VehicleLogEntry>,
    update: (AppSettings.() -> AppSettings) -> Unit
) {
    var type by remember { mutableStateOf(VehicleLogType.FUEL) }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val monthly = entries.filter { it.createdAt >= monthStart && it.createdAt <= now }
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }

    Text("Monthly fuel: ${format.format(monthly.total(VehicleLogType.FUEL))} · " +
        "EV charging: ${format.format(monthly.total(VehicleLogType.CHARGE))}")
    Text("Enter costs in the same currency for all entries.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in VehicleLogType.entries) {
            FilterChip(
                selected = type == choice,
                onClick = { type = choice; error = null },
                label = { Text(choice.label) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(description, { description = it.take(80) }, singleLine = true,
            label = { Text("Description") }, modifier = Modifier.widthIn(max = 220.dp))
        OutlinedTextField(cost, { cost = it.take(16) }, singleLine = true,
            label = { Text("Cost") }, modifier = Modifier.widthIn(max = 130.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (type == VehicleLogType.SERVICE) {
            OutlinedTextField(interval, { interval = it.take(4) }, singleLine = true,
                label = { Text("Repeat in days") }, modifier = Modifier.widthIn(max = 160.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    OutlinedButton(onClick = {
        val amount = cost.trim().replace(',', '.').toBigDecimalOrNull()
        val days = interval.toIntOrNull()
        error = when {
            type == VehicleLogType.SERVICE && description.isBlank() -> "Enter a service description."
            cost.isNotBlank() && (amount == null || amount.signum() < 0 || amount.scale() !in 0..2) ->
                "Enter a non-negative cost with up to two decimal places."
            type != VehicleLogType.SERVICE && amount == null -> "Enter a cost."
            type == VehicleLogType.SERVICE && interval.isNotBlank() && (days == null || days !in 1..3650) ->
                "Enter a service interval from 1 to 3650 days."
            else -> null
        }
        if (error == null) {
            val entry = VehicleLogEntry(
                type, description.trim().ifBlank { type.label }, amount?.toPlainString() ?: "0",
                serviceIntervalDays = days.takeIf { type == VehicleLogType.SERVICE }
            )
            update { copy(vehicleLog = listOf(entry) + vehicleLog) }
            description = ""
            cost = ""
            interval = ""
        }
    }) { Text("Save entry") }
    error?.let { Text(it) }

    val scheduledServices = entries
        .filter { it.type == VehicleLogType.SERVICE }
        .distinctBy { it.label.trim().lowercase() }
        .filter { it.serviceIntervalDays != null }
    for (entry in scheduledServices) {
        val due = entry.createdAt + TimeUnit.DAYS.toMillis(requireNotNull(entry.serviceIntervalDays).toLong())
        val days = kotlin.math.ceil((due - now).toDouble() / TimeUnit.DAYS.toMillis(1)).toLong()
        val status = when {
            due <= now -> "due"
            days == 1L -> "due in 1 day"
            else -> "due in $days days"
        }
        Text("${entry.label} · $status")
    }
    if (entries.isNotEmpty()) {
        OutlinedButton(onClick = { showHistory = !showHistory }) {
            Text(if (showHistory) "Hide history" else "Show recent history")
        }
    }
    if (showHistory) {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
        for (entry in entries.take(20)) {
            Text("${date.format(Date(entry.createdAt))} · ${entry.type.label} · ${entry.label} · ${entry.cost}")
        }
    }
}

private fun List<VehicleLogEntry>.total(type: VehicleLogType): BigDecimal =
    filter { it.type == type }.fold(BigDecimal.ZERO) { sum, entry ->
        sum + (entry.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    }
