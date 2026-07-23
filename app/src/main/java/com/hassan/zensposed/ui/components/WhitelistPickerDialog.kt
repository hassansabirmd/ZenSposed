package com.hassan.zensposed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hassan.zensposed.ui.InstalledApp

/**
 * Full whitelist picker with a search field and a running count. The always-allowed
 * default apps (dialer/messaging/contacts/calculator) are shown checked and disabled.
 * Toggle "Show system apps" to include system packages (share sheets, file pickers, etc.).
 */
@Composable
fun WhitelistPickerDialog(
    apps: List<InstalledApp>,
    alwaysAllowed: Set<String>,
    initiallySelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            initiallySelected.forEach { put(it, true) }
        }
    }

    // Pin already-saved whitelist apps (and defaults) to the top. Live checkbox
    // toggles must not re-order the list — that only updates after Done + reopen.
    val filtered = remember(query, apps, initiallySelected, alwaysAllowed, showSystem) {
        val visible = apps.filter { app ->
            showSystem || !app.isSystem || app.packageName in initiallySelected ||
                app.packageName in alwaysAllowed
        }
        val base = if (query.isBlank()) visible
        else visible.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        base.sortedWith(
            compareByDescending<InstalledApp> {
                it.packageName in alwaysAllowed || it.packageName in initiallySelected
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }
    val selectedCount = selected.count { it.value && it.key !in alwaysAllowed }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Select apps to whitelist", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Show system apps",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Checkbox(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it }
                    )
                }
                Text(
                    "Share sheets and file pickers are always allowed. Enable system apps to whitelist extras.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (selectedCount == 0) "0 extra apps (4 defaults always allowed)"
                    else "$selectedCount extra app(s) · 4 defaults always allowed",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isDefault = app.packageName in alwaysAllowed
                        val checked = isDefault || (selected[app.packageName] == true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    color = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                when {
                                    isDefault -> Text(
                                        "Always allowed",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    app.isSystem -> Text(
                                        "System · ${app.packageName}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Checkbox(
                                checked = checked,
                                enabled = !isDefault,
                                onCheckedChange = { selected[app.packageName] = it }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = {
                        val result = selected.filter { it.value }.keys.toSet() - alwaysAllowed
                        onConfirm(result)
                    }) { Text("Done") }
                }
            }
        }
    }
}
