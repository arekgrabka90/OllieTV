package com.ollie.iptv.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ollie.iptv.data.FavoriteGroupEntity
import com.ollie.iptv.ui.ChannelSecurityAction
import com.ollie.iptv.ui.ChannelSecurityRequest

@Composable
fun ChannelSecurityDialog(
    request: ChannelSecurityRequest,
    error: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember(request.stream.id, request.action) { mutableStateOf("") }
    var confirmation by remember(request.stream.id, request.action) { mutableStateOf("") }
    val title = when {
        request.needsPinSetup -> "Create parental PIN"
        request.action == ChannelSecurityAction.PLAY -> "Unlock channel"
        request.action == ChannelSecurityAction.LOCK -> "Lock channel"
        else -> "Unlock channel"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(request.stream.name)
                if (request.needsPinSetup) {
                    Text("Create a 4–8 digit PIN to protect locked channels.")
                } else {
                    Text("Enter your parental PIN to continue.")
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { digit -> digit.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (request.needsPinSetup) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.filter { digit -> digit.isDigit() }.take(8) },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin, confirmation) },
                enabled = if (request.needsPinSetup) pin.length in 4..8 && confirmation.length in 4..8 else pin.isNotBlank()
            ) { Text(if (request.action == ChannelSecurityAction.PLAY) "Unlock and watch" else "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FavoriteGroupPickerDialog(
    groups: List<FavoriteGroupEntity>,
    onSelect: (FavoriteGroupEntity) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to favorite group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (groups.isEmpty()) {
                    Text("You have not created any favorite groups yet.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(groups, key = { it.id }) { group ->
                            TextButton(onClick = { onSelect(group) }, modifier = Modifier.fillMaxWidth()) {
                                Text(group.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("Create group") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
