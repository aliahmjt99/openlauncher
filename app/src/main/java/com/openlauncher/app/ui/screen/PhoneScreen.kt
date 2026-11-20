package com.openlauncher.app.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.PhoneFavorite
import com.openlauncher.app.data.normalizeDialNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PhoneScreen(
    favorites: List<PhoneFavorite>,
    onSave: (PhoneFavorite) -> Unit,
    onRemove: (PhoneFavorite) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<PhoneFavorite?>(null) }

    BackHandler(onBack = onBack)
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri?.scheme == "content") {
            scope.launch {
                val contact = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                            null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                normalizeDialNumber(cursor.getString(1).orEmpty())?.let { selected ->
                                    PhoneFavorite(cursor.getString(0).orEmpty(), selected)
                                }
                            } else null
                        }
                    }.getOrNull()
                }
                if (contact == null) {
                    status = "Could not read a supported phone number from the selected contact."
                } else {
                    name = contact.name
                    number = contact.number
                    status = null
                }
            }
        }
    }

    fun dial(value: String) {
        status = try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)))
            null
        } catch (_: ActivityNotFoundException) {
            "No dialer is available. Use the head unit's phone app."
        } catch (_: SecurityException) {
            "Android blocked the dialer."
        }
    }

    Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Phone", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(number, { number = it.take(70); status = null }, label = { Text("Number") },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            for (keys in listOf("123", "456", "789", "+0#")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (key in keys) {
                        OutlinedButton(onClick = { if (number.length < 40) number += key },
                            modifier = Modifier.weight(1f)) { Text(key.toString()) }
                    }
                }
            }
            Row {
                TextButton(onClick = { number = number.dropLast(1) }) { Text("Backspace") }
                TextButton(onClick = { if (number.length < 40) number += "*" }) { Text("*") }
                TextButton(onClick = { number = ""; name = ""; status = null }) { Text("Clear") }
            }
            val normalized = normalizeDialNumber(number)
            Button(enabled = normalized != null, onClick = { normalized?.let(::dial) }) { Text("Open dialer") }
            status?.let { Text(it) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Favorites", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = {
                status = try {
                    contactPicker.launch(Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE))
                    null
                } catch (_: ActivityNotFoundException) {
                    "No contact picker is available. Enter the number manually."
                } catch (_: SecurityException) {
                    "Android blocked the contact picker."
                }
            }) { Text("Choose contact") }
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Favorite name") }, singleLine = true)
            val normalized = normalizeDialNumber(number)
            OutlinedButton(enabled = normalized != null && name.isNotBlank(), onClick = {
                normalized?.let { onSave(PhoneFavorite(name.trim(), it)); status = "Favorite saved." }
            }) { Text("Save favorite") }
            if (favorites.isNotEmpty()) {
                OutlinedTextField(search, { search = it.take(80) }, label = { Text("Find a favorite") }, singleLine = true)
            }
            for (favorite in favorites.filter { it.name.contains(search.trim(), true) || it.number.contains(search.trim()) }) {
                Row {
                    TextButton(onClick = { dial(favorite.number) }, modifier = Modifier.weight(1f)) {
                        Text("${favorite.name} · ${favorite.number}")
                    }
                    TextButton(onClick = { removing = favorite }) { Text("Remove") }
                }
            }
            TextButton(onClick = onBack) { Text("Back to dashboard") }
        }
    }

    removing?.let { favorite ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${favorite.name}?") },
            confirmButton = { TextButton(onClick = { onRemove(favorite); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}
