package com.openlauncher.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.openlauncher.app.R

@Composable
fun StartupLogo(logo: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val car = painterResource(R.mipmap.ic_launcher)
        when (logo) {
            "none" -> Unit
            "car" -> Image(
                Icons.Default.DirectionsCar,
                contentDescription = "Car",
                modifier = Modifier.size(128.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
            "launcher" -> Image(car, "Open Launcher", Modifier.size(128.dp))
            else -> AsyncImage(
                model = logo,
                contentDescription = "Custom startup logo",
                modifier = Modifier.size(128.dp),
                placeholder = car,
                error = car
            )
        }
    }
}
