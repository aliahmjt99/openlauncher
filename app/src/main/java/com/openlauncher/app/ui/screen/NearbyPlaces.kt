package com.openlauncher.app.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun NearbyPlaces() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    Text("Search in your installed maps app.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, term) in listOf(
            "Fuel" to "gas station", "Food" to "restaurant", "Hotels" to "hotel", "ATMs" to "ATM",
            "EV charging" to "EV charging station"
        )) {
            OutlinedButton(onClick = {
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(term)}")
                message = try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    null
                } catch (_: ActivityNotFoundException) {
                    "Install a maps app that handles nearby search."
                } catch (_: SecurityException) {
                    "Android blocked the maps app."
                }
            }) { Text(label) }
        }
    }
    message?.let { Text(it) }
}
