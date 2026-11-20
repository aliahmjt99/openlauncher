package com.openlauncher.app.ui.screen

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.openlauncher.app.data.LogCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun LogExport(fileName: String, write: LogCsv.() -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("Unable to open selected file")
                        stream.bufferedWriter(Charsets.UTF_8).use { writer -> LogCsv(writer).write() }
                    }
                    status = "CSV saved."
                } catch (_: IOException) {
                    status = "Could not write the CSV. Choose a different location and try again."
                } catch (_: SecurityException) {
                    status = "Access to the selected file was denied."
                } finally {
                    busy = false
                }
            }
        }
    }
    OutlinedButton(enabled = !busy, onClick = {
        status = null
        try {
            picker.launch(fileName)
        } catch (_: ActivityNotFoundException) {
            status = "No file picker is available on this head unit."
        } catch (_: SecurityException) {
            status = "Android blocked the file picker."
        }
    }) { Text(if (busy) "Saving CSV…" else "Export CSV") }
    status?.let { Text(it) }
}
