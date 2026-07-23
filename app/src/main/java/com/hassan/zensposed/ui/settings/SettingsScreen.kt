package com.hassan.zensposed.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.data.settings.FocusSettings
import com.hassan.zensposed.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* grant result handled when scanner opens; requesting early avoids focus-exit lockout */ }

    fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            item {
                SectionTitle("Security")
                Text(
                    "Choose one emergency exit method. Only the selected method works during a focus session.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ExitMethodSelector(
                    method = settings.exitMethod,
                    hasPassword = settings.hasPassword,
                    onSelectPassword = { viewModel.setExitMethod(FocusSettings.EXIT_PASSWORD) },
                    onSelectQr = {
                        // Request camera now so emergency exit scan works later in a session.
                        ensureCameraPermission()
                        viewModel.setExitMethod(FocusSettings.EXIT_QR)
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (settings.exitMethod == FocusSettings.EXIT_PASSWORD) {
                    SettingRow(
                        title = if (viewModel.hasPassword()) "Change emergency password" else "Set emergency password",
                        subtitle = "Minimum ${Constants.MIN_PASSWORD_LENGTH} characters. Required to exit early.",
                        onClick = { showPasswordDialog = true }
                    )
                } else {
                    SettingRow(
                        title = if (viewModel.hasQrSecret()) {
                            if (viewModel.isCustomQr()) "Manage exit QR (custom)" else "Manage exit QR code"
                        } else {
                            "Set up exit QR code"
                        },
                        subtitle = "Scan your own QR with the camera, or generate one to save/print.",
                        onClick = {
                            ensureCameraPermission()
                            showQrDialog = true
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
                SectionTitle("App appearance")
                AppearanceSelector(
                    selected = settings.appearanceMode,
                    onSelect = viewModel::setAppearanceMode
                )
                Spacer(Modifier.height(20.dp))
                SectionTitle("Focus screen appearance")
                ThemeSelector(selected = settings.themeId, onSelect = viewModel::setTheme)
                Spacer(Modifier.height(20.dp))
                SectionTitle("Focus behavior")
                ToggleRow(
                    title = "Block swipe down from status bar",
                    subtitle = "When on, notification shade and quick settings stay locked during a session.",
                    checked = !settings.allowNotificationPanel,
                    onChange = { block -> viewModel.setAllowNotificationPanel(!block) }
                )
                ToggleRow(
                    title = "Block Home button / gesture during session",
                    subtitle = "When on, Home is disabled via StatusBarManager while focusing.",
                    checked = settings.blockHomeButton,
                    onChange = viewModel::setBlockHomeButton
                )
                ToggleRow(
                    title = "Block Recents / Overview during session",
                    subtitle = "When on, the Recents button and gesture are disabled while focusing.",
                    checked = settings.blockRecentApps,
                    onChange = viewModel::setBlockRecentApps
                )
                ToggleRow(
                    title = "Allow quick toggles (Wi-Fi, Data, Hotspot, Torch, DND)",
                    subtitle = "Permit toggling connectivity and Do Not Disturb while focusing.",
                    checked = settings.allowQuickToggles,
                    onChange = viewModel::setAllowQuickToggles
                )
                ToggleRow(
                    title = "Always-On Display timer",
                    subtitle = "Keep a dim timer on screen during the session (pseudo-AOD).",
                    checked = settings.aodEnabled,
                    onChange = viewModel::setAod
                )
                Spacer(Modifier.height(20.dp))
                SectionTitle("Quick timers")
                Text(
                    "These shortcuts appear on the Deep Zen card. Tap a row to edit hours and minutes.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                QuickTimersEditor(
                    timers = settings.quickTimersMinutes,
                    onChange = viewModel::setQuickTimers
                )
                Spacer(Modifier.height(20.dp))
                SectionTitle("Whitelisted apps")
                Text(
                    "Calls, SMS, keyboards (Gboard), share sheets, and file pickers are always allowed. Whitelisted apps appear on the focus screen. Use “Show system apps” in the picker for other system panels.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingRow(
                    title = "Select apps to whitelist",
                    subtitle = if (settings.whitelist.isEmpty()) {
                        "0 extra apps · Dialer, Messages, Contacts, Calculator always allowed"
                    } else {
                        "${settings.whitelist.size} extra app(s) · plus Dialer, Messages, Contacts, Calculator"
                    },
                    onClick = { showWhitelistDialog = true }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            hasExisting = viewModel.hasPassword(),
            viewModel = viewModel,
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (showWhitelistDialog) {
        com.hassan.zensposed.ui.components.WhitelistPickerDialog(
            apps = apps,
            alwaysAllowed = viewModel.alwaysAllowed,
            initiallySelected = settings.whitelist,
            onConfirm = { viewModel.setWhitelist(it); showWhitelistDialog = false },
            onDismiss = { showWhitelistDialog = false }
        )
    }

    if (showQrDialog) {
        ExitQrDialog(
            initialPayload = viewModel.qrPayloadOrNull(),
            initiallyCustom = viewModel.isCustomQr(),
            onRegenerate = {
                ensureCameraPermission()
                viewModel.regenerateQr()
            },
            onScanCustom = { viewModel.setCustomQr(it) },
            onRequestCamera = { ensureCameraPermission() },
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
private fun AppearanceSelector(selected: String, onSelect: (String) -> Unit) {
    Text(
        "Theme for home and settings screens.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilterChip(
            selected = selected == FocusSettings.APPEARANCE_SYSTEM,
            onClick = { onSelect(FocusSettings.APPEARANCE_SYSTEM) },
            label = { Text("System") }
        )
        FilterChip(
            selected = selected == FocusSettings.APPEARANCE_LIGHT,
            onClick = { onSelect(FocusSettings.APPEARANCE_LIGHT) },
            label = { Text("Light") }
        )
        FilterChip(
            selected = selected == FocusSettings.APPEARANCE_DARK,
            onClick = { onSelect(FocusSettings.APPEARANCE_DARK) },
            label = { Text("Dark") }
        )
    }
}

@Composable
private fun ThemeSelector(selected: String, onSelect: (String) -> Unit) {
    Text(
        "Background",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(com.hassan.zensposed.data.model.NatureTheme.entries.toList()) { theme ->
            val isSelected = theme.id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isSelected) Modifier.border(
                                width = 3.dp,
                                color = androidx.compose.ui.graphics.Color(0xFF2536F5),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) else Modifier
                        )
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(theme.gradient),
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { onSelect(theme.id) }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    theme.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ExitMethodSelector(
    method: String,
    hasPassword: Boolean,
    onSelectPassword: () -> Unit,
    onSelectQr: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = method == FocusSettings.EXIT_PASSWORD,
            onClick = onSelectPassword,
            label = { Text(if (hasPassword) "Password" else "Password (set next)") }
        )
        FilterChip(
            selected = method == FocusSettings.EXIT_QR,
            onClick = onSelectQr,
            label = { Text("QR code") }
        )
    }
}

@Composable
private fun ExitQrDialog(
    initialPayload: String?,
    initiallyCustom: Boolean,
    onRegenerate: () -> String,
    onScanCustom: (String) -> Boolean,
    onRequestCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentPayload by remember { mutableStateOf(initialPayload) }
    var isCustom by remember { mutableStateOf(initiallyCustom) }
    var scanning by remember { mutableStateOf(false) }
    var scanSession by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    // Ask for camera as soon as the manage/generate dialog opens so exit scan works later.
    LaunchedEffect(Unit) { onRequestCamera() }

    val bitmap = remember(currentPayload, isCustom) {
        if (isCustom || currentPayload.isNullOrBlank()) null
        else runCatching { com.hassan.zensposed.core.QrEncoder.encode(currentPayload!!, 640) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scanning) "Scan your QR code" else "Exit QR code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (scanning) {
                    Text(
                        "Point the camera at the QR code you want to use for emergency exit.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.runtime.key(scanSession) {
                        com.hassan.zensposed.ui.focus.QrScanner(
                            onQrScanned = { raw ->
                                if (onScanCustom(raw)) {
                                    currentPayload = raw.trim()
                                    isCustom = true
                                    scanning = false
                                    status = "Your QR code was saved. Scanning this same code will exit focus."
                                } else {
                                    status = "Could not save that QR code. Try again."
                                    scanSession++
                                }
                            }
                        )
                    }
                } else if (isCustom) {
                    Text(
                        "A custom QR code is registered. During a focus session, scan that same code to exit. " +
                            "You can scan a different code or generate a new app QR instead.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Custom QR registered",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Screenshot or print this code and keep it somewhere you can reach in an emergency. " +
                            "Or tap “Scan my QR” to use a code you already have. " +
                            "Camera permission is requested here so you can scan to exit during a session.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Exit QR",
                            modifier = Modifier.size(220.dp)
                        )
                    } else {
                        Text(
                            "No QR yet. Scan your own or generate one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (scanning) {
                TextButton(onClick = { scanning = false }) { Text("Cancel scan") }
            } else {
                TextButton(onClick = {
                    status = null
                    onRequestCamera()
                    scanning = true
                }) { Text("Scan my QR") }
            }
        },
        dismissButton = {
            if (!scanning) {
                Row {
                    TextButton(onClick = {
                        onRequestCamera()
                        currentPayload = onRegenerate()
                        isCustom = false
                        status = "Generated a new ZenSposed QR code. Camera permission was requested for exit scanning."
                    }) { Text("Generate") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Spacer(Modifier.size(8.dp))
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun QuickTimersEditor(timers: List<Int>, onChange: (List<Int>) -> Unit) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    timers.forEachIndexed { index, minutes ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clickable { editingIndex = index }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quick timer ${index + 1}", fontWeight = FontWeight.Medium)
                Text(
                    formatQuickDuration(minutes),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    val idx = editingIndex
    if (idx != null) {
        QuickTimerEditDialog(
            initialMinutes = timers.getOrElse(idx) { 120 },
            onDismiss = { editingIndex = null },
            onSave = { newMinutes ->
                val updated = timers.toMutableList()
                if (idx in updated.indices) updated[idx] = newMinutes
                onChange(updated)
                editingIndex = null
            }
        )
    }
}

@Composable
private fun QuickTimerEditDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var hours by remember {
        androidx.compose.runtime.mutableIntStateOf((initialMinutes / 60).coerceIn(0, 24))
    }
    var minutes by remember {
        androidx.compose.runtime.mutableIntStateOf(
            if (initialMinutes / 60 >= 24) 0 else initialMinutes % 60
        )
    }
    val hourItems = remember { (0..24).toList() }
    val minuteItems = remember(hours) { if (hours >= 24) listOf(0) else (0..59).toList() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Edit quick timer", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.hassan.zensposed.ui.components.WheelPicker(
                        items = hourItems,
                        selectedIndex = hours,
                        onSelected = { h ->
                            hours = h.coerceIn(0, 24)
                            if (hours == 24) minutes = 0
                        }
                    )
                    Text("h", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                    androidx.compose.runtime.key(hours >= 24) {
                        com.hassan.zensposed.ui.components.WheelPicker(
                            items = minuteItems,
                            selectedIndex = if (hours >= 24) 0 else minutes,
                            onSelected = { m ->
                                minutes = if (hours >= 24) 0 else m.coerceIn(0, 59)
                            }
                        )
                    }
                    Text("m", fontSize = 18.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        val total = if (hours >= 24) 24 * 60 else hours * 60 + minutes
                        if (total > 0) onSave(total)
                    }) { Text("Save") }
                }
            }
        }
    }
}

private fun formatQuickDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h} hours"
        else -> "${m} minutes"
    }
}
