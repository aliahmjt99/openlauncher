package com.openlauncher.app.ui.widget

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.openlauncher.app.util.LocationData
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
internal fun LiveMapWidget(location: LocationData?, controlsEnabled: Boolean = true) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var zoom by remember { mutableIntStateOf(16) }
    var launchError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            now = SystemClock.elapsedRealtime()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        val tileSize = 256.dp
        val hasLocation = location != null && location.latitude.isFinite() && location.longitude.isFinite()
        if (location != null && hasLocation) {
            val count = 1 shl zoom
            val latitude = Math.toRadians(location.latitude.coerceIn(-85.05112878, 85.05112878))
            val centerX = (location.longitude + 180.0) / 360.0 * count
            val centerY = ((1.0 - asinh(tan(latitude)) / Math.PI) / 2.0 * count)
                .coerceIn(0.0, count.toDouble() - 0.000001)
            val halfX = maxWidth / tileSize / 2f
            val halfY = maxHeight / tileSize / 2f
            for (y in floor(centerY - halfY).toInt()..floor(centerY + halfY).toInt()) {
                if (y !in 0 until count) continue
                for (x in floor(centerX - halfX).toInt()..floor(centerX + halfX).toInt()) {
                    val wrappedX = ((x % count) + count) % count
                    val url = "https://tile.openstreetmap.org/$zoom/$wrappedX/$y.png"
                    key(url) {
                        val request = remember(url, context) {
                            ImageRequest.Builder(context).data(url)
                                .addHeader("User-Agent", "OpenLauncher/0.0.5 (+https://github.com/aliahmjt99/openlauncher)")
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.absoluteOffset(
                                x = maxWidth / 2 - tileSize * (centerX - x).toFloat(),
                                y = maxHeight / 2 - tileSize * (centerY - y).toFloat()
                            ).size(tileSize)
                        )
                    }
                }
            }
            val stale = (now - location.fixElapsedMs).coerceAtLeast(0L) > 15_000L
            Box(Modifier.align(Alignment.Center).size(16.dp).background(Color.White, CircleShape)) {
                Box(Modifier.align(Alignment.Center).size(10.dp)
                    .background(if (stale) Color.Gray else Color(0xFF1769CC), CircleShape))
            }
            if (stale) {
                Text("Last known location", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center).offset(y = 20.dp)
                        .background(MaterialTheme.colorScheme.surface).padding(3.dp))
            }
            if (controlsEnabled) {
                Row(Modifier.align(Alignment.TopEnd).padding(top = 19.dp)
                    .background(MaterialTheme.colorScheme.surface)) {
                    TextButton(onClick = { zoom-- }, enabled = zoom > 13, contentPadding = PaddingValues(3.dp)) { Text("−") }
                    TextButton(onClick = { zoom++ }, enabled = zoom < 18, contentPadding = PaddingValues(3.dp)) { Text("+") }
                    TextButton(onClick = {
                        launchError = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("geo:${location.latitude},${location.longitude}?z=$zoom")))
                        }.isFailure
                    }, contentPadding = PaddingValues(3.dp)) { Text("Maps") }
                }
            }
        } else {
            Text("Waiting for GPS", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center))
        }
        if (launchError) Text("No maps app available", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.surface).padding(3.dp))
        if (hasLocation) {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface).padding(3.dp))
        }
    }
}
