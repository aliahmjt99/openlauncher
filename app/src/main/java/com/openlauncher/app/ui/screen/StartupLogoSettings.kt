package com.openlauncher.app.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openlauncher.app.ui.components.StartupLogo

@Composable
internal fun StartupLogoSettings(logo: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            error = !runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (!error) onChange(uri.toString())
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in listOf("car" to "Car", "launcher" to "Open Launcher", "none" to "Off")) {
            FilterChip(
                selected = value == logo,
                onClick = { error = false; onChange(value) },
                label = { Text(label) }
            )
        }
    }
    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) {
        Text("Choose a car logo image")
    }
    if (error) Text("Android could not keep access to that image. Choose a different file.")
    if (logo != "none") StartupLogo(logo, Modifier.size(160.dp))
}
