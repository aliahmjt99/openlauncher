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

internal data class VehicleBrandLogo(val id: String, val label: String, val drawable: Int)

internal val vehicleBrandLogos = listOf(
    VehicleBrandLogo("brand:hyundai", "Hyundai", R.drawable.brand_hyundai),
    VehicleBrandLogo("brand:toyota", "Toyota", R.drawable.brand_toyota),
    VehicleBrandLogo("brand:kia", "Kia", R.drawable.brand_kia),
    VehicleBrandLogo("brand:nissan", "Nissan", R.drawable.brand_nissan),
    VehicleBrandLogo("brand:honda", "Honda", R.drawable.brand_honda),
    VehicleBrandLogo("brand:ford", "Ford", R.drawable.brand_ford),
    VehicleBrandLogo("brand:chevrolet", "Chevrolet", R.drawable.brand_chevrolet),
    VehicleBrandLogo("brand:volkswagen", "Volkswagen", R.drawable.brand_volkswagen),
    VehicleBrandLogo("brand:mazda", "Mazda", R.drawable.brand_mazda),
    VehicleBrandLogo("brand:mitsubishi", "Mitsubishi", R.drawable.brand_mitsubishi),
    VehicleBrandLogo("brand:bmw", "BMW", R.drawable.brand_bmw),
    VehicleBrandLogo("brand:suzuki", "Suzuki", R.drawable.brand_suzuki),
    VehicleBrandLogo("brand:jeep", "Jeep", R.drawable.brand_jeep),
    VehicleBrandLogo("brand:audi", "Audi", R.drawable.brand_audi),
    VehicleBrandLogo("brand:peugeot", "Peugeot", R.drawable.brand_peugeot),
    VehicleBrandLogo("brand:renault", "Renault", R.drawable.brand_renault),
)

@Composable
fun StartupLogo(logo: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val car = painterResource(R.mipmap.ic_launcher)
        val brand = vehicleBrandLogos.firstOrNull { it.id == logo }
        if (brand != null) {
            Image(
                painter = painterResource(brand.drawable),
                contentDescription = brand.label,
                modifier = Modifier.size(128.dp)
            )
            return@Box
        }
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
