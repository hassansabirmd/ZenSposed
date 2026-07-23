package com.hassan.zensposed.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun PasswordDialog(
    hasExisting: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExisting) "Change password" else "Set password") },
        text = {
            Column {
                if (hasExisting) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New password (min ${Constants.MIN_PASSWORD_LENGTH})") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${newPw.length}/${Constants.MIN_PASSWORD_LENGTH} characters",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                if (newPw != confirm) {
                    error = "Passwords do not match"
                    return@TextButton
                }
                scope.launch {
                    val result = viewModel.setPassword(newPw, if (hasExisting) current else null)
                    result.fold(
                        onSuccess = { onDismiss() },
                        onFailure = { error = it.message }
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
