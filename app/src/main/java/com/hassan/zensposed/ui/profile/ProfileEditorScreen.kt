package com.hassan.zensposed.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hassan.zensposed.data.model.Profile
import com.hassan.zensposed.ui.MainViewModel
import com.hassan.zensposed.ui.components.WheelPicker
import com.hassan.zensposed.ui.components.WhitelistPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val editing by viewModel.editingProfile.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val base = editing ?: return

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    var name by remember { mutableStateOf(base.name) }
    var noLimit by remember { mutableStateOf(base.noLimit) }
    var hours by remember { mutableIntStateOf(base.durationMinutes / 60) }
    var minutes by remember { mutableIntStateOf(base.durationMinutes % 60) }
    var whitelist by remember { mutableStateOf(base.whitelist) }
    var showWhitelist by remember { mutableStateOf(false) }

    val hourItems = remember { (0..24).toList() }
    val minuteItems = remember { (0..59).toList() }
    val isNew = base.name.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New profile" else "Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = {
                            viewModel.deleteProfile(base.id)
                            onDone()
                        }) { Icon(Icons.Filled.Delete, "Delete profile") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("No time limit", fontWeight = FontWeight.Medium)
                        Switch(checked = noLimit, onCheckedChange = { noLimit = it })
                    }
                    if (!noLimit) {
                        Spacer(Modifier.height(8.dp))
                        val minuteItemsLocked = if (hours >= 24) listOf(0) else minuteItems
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WheelPicker(
                                items = hourItems,
                                selectedIndex = hours,
                                onSelected = { h -> hours = h.coerceIn(0, 24); if (hours == 24) minutes = 0 }
                            )
                            Text("h", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                            androidx.compose.runtime.key(hours >= 24) {
                                WheelPicker(
                                    items = minuteItemsLocked,
                                    selectedIndex = if (hours >= 24) 0 else minutes,
                                    onSelected = { m -> minutes = if (hours >= 24) 0 else m.coerceIn(0, 59) }
                                )
                            }
                            Text("m", fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWhitelist = true }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Whitelisted apps", fontWeight = FontWeight.Medium)
                    Text(
                        if (whitelist.isEmpty()) "None selected (calls & SMS always allowed)"
                        else "${whitelist.size} app(s) selected",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val total = if (hours >= 24) 24 * 60 else hours * 60 + minutes
                    if (!noLimit && total <= 0) return@Button
                    viewModel.upsertProfile(
                        Profile(
                            id = base.id,
                            name = name.ifBlank { "Profile" },
                            durationMinutes = total.coerceAtMost(24 * 60),
                            noLimit = noLimit,
                            whitelist = whitelist
                        )
                    )
                    onDone()
                },
                enabled = noLimit || (hours * 60 + minutes) > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Save profile", fontSize = 16.sp) }
        }
    }

    if (showWhitelist) {
        WhitelistPickerDialog(
            apps = apps,
            alwaysAllowed = viewModel.alwaysAllowed,
            initiallySelected = whitelist,
            onConfirm = { whitelist = it; showWhitelist = false },
            onDismiss = { showWhitelist = false }
        )
    }
}
