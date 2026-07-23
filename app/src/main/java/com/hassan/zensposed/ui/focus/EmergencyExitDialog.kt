package com.hassan.zensposed.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hassan.zensposed.ZenSposedApp
import com.hassan.zensposed.data.settings.FocusSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DISCLAIMER_SECONDS = 20

@Composable
fun EmergencyExitDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(DISCLAIMER_SECONDS) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var exitMethod by remember { mutableStateOf(FocusSettings.EXIT_PASSWORD) }
    val secure = ZenSposedApp.instance.securePrefs
    val hasPassword = remember { secure.hasPassword() }
    val hasQr = remember { secure.hasQrSecret() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        exitMethod = ZenSposedApp.instance.settingsRepository.settings.first().exitMethod
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val unlocked = secondsLeft <= 0
    val useQr = exitMethod == FocusSettings.EXIT_QR && hasQr
    val usePassword = !useQr

    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text("Emergency exit") },
        text = {
            Column {
                Text(
                    "ZenSposed keeps you honest. Only exit if you truly have an emergency. " +
                        "The session and your progress will end."
                )
                Spacer(Modifier.height(12.dp))
                if (!unlocked) {
                    Text(
                        "Please wait $secondsLeft seconds...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (useQr) {
                    Text("Scan your ZenSposed exit QR code:")
                    Spacer(Modifier.height(8.dp))
                    var scanSession by remember { mutableIntStateOf(0) }
                    androidx.compose.runtime.key(scanSession) {
                        QrScanner(
                            onQrScanned = { payload ->
                                verifying = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) {
                                        runCatching { secure.verifyQrPayload(payload) }.getOrDefault(false)
                                    }
                                    verifying = false
                                    if (ok) onVerified()
                                    else {
                                        error = "Wrong QR code. Try again."
                                        scanSession++
                                    }
                                }
                            }
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                } else if (!hasPassword) {
                    Text(
                        "No exit method is configured, so you can exit now.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Enter your emergency password to unlock:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !verifying,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (verifying) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            when {
                !unlocked -> {
                    TextButton(enabled = false, onClick = {}) { Text("Wait $secondsLeft") }
                }
                useQr -> {
                    // Scanning unlocks automatically — no confirm needed.
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                !hasPassword -> {
                    TextButton(onClick = onVerified) { Text("Unlock") }
                }
                else -> {
                    TextButton(
                        enabled = !verifying,
                        onClick = {
                            if (password.isEmpty()) {
                                error = "Wrong password entered. Try again."
                                return@TextButton
                            }
                            verifying = true
                            error = null
                            scope.launch {
                                val ok = withContext(Dispatchers.Default) {
                                    runCatching { secure.verify(password) }.getOrDefault(false)
                                }
                                verifying = false
                                if (ok) onVerified()
                                else {
                                    error = "Wrong password entered. Try again."
                                    password = ""
                                }
                            }
                        }
                    ) { Text("Unlock") }
                }
            }
        },
        dismissButton = {
            if (!useQr || !unlocked) {
                TextButton(enabled = !verifying, onClick = onDismiss) { Text("Stay focused") }
            }
        }
    )
}
