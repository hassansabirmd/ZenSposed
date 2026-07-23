package com.hassan.zensposed.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hassan.zensposed.data.model.Profile
import com.hassan.zensposed.ui.components.WheelPicker
import com.hassan.zensposed.ui.theme.ZenBlue
import com.hassan.zensposed.ui.theme.ZenBlueDark

@Composable
fun HomeScreen(
    profiles: List<Profile>,
    deepZenWhitelist: Set<String>,
    deepZenMinutes: Int,
    quickTimersMinutes: List<Int>,
    onDeepZenMinutesChange: (Int) -> Unit,
    onStartFocus: (minutes: Int, noLimit: Boolean, spaceName: String, whitelist: Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: (Profile) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.Filled.BarChart, "Stats", onOpenStats)
                Spacer(Modifier.size(12.dp))
                CircleIconButton(Icons.Filled.Settings, "Settings", onOpenSettings)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "ZenSposed",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(20.dp))
            DeepZenCard(
                initialMinutes = deepZenMinutes,
                quickTimers = quickTimersMinutes,
                onMinutesChange = onDeepZenMinutesChange,
                onStart = { minutes -> onStartFocus(minutes, false, "Deep Zen", deepZenWhitelist) }
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onNewProfile) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("New Profile")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        items(profiles, key = { it.id }) { profile ->
            ProfileCard(
                profile = profile,
                onStart = {
                    onStartFocus(profile.durationMinutes, profile.noLimit, profile.name, profile.whitelist)
                },
                onEdit = { onEditProfile(profile) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DeepZenCard(
    initialMinutes: Int,
    quickTimers: List<Int>,
    onMinutesChange: (Int) -> Unit,
    onStart: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf((initialMinutes / 60).coerceIn(0, 24)) }
    var minutes by remember {
        mutableIntStateOf(if (initialMinutes / 60 >= 24) 0 else initialMinutes % 60)
    }
    var hoursScrolling by remember { mutableStateOf(false) }
    var minutesScrolling by remember { mutableStateOf(false) }

    // Keep local wheel state in sync if persisted value changes externally (e.g. quick timer).
    LaunchedEffect(initialMinutes) {
        if (hoursScrolling || minutesScrolling) return@LaunchedEffect
        hours = (initialMinutes / 60).coerceIn(0, 24)
        minutes = if (hours >= 24) 0 else initialMinutes % 60
    }

    fun commit(h: Int, m: Int) {
        val total = if (h >= 24) 24 * 60 else h * 60 + m
        if (total > 0) onMinutesChange(total.coerceAtMost(24 * 60))
    }

    val hourItems = remember { (0..24).toList() }
    val minuteItems = remember(hours) { if (hours >= 24) listOf(0) else (0..59).toList() }
    val minuteIndex = if (hours >= 24) 0 else minutes.coerceIn(0, 59)
    val wheelSettling = hoursScrolling || minutesScrolling

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(ZenBlueDark, ZenBlue, Color(0xFF4A5CFF)))
                )
                .padding(24.dp)
        ) {
            Column {
                Text("Deep Zen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelPicker(
                        items = hourItems,
                        selectedIndex = hours,
                        onSelected = { h ->
                            hours = h.coerceIn(0, 24)
                            if (hours == 24) minutes = 0
                        },
                        onScrollInProgressChange = { scrolling ->
                            val wasScrolling = hoursScrolling
                            hoursScrolling = scrolling
                            // Only persist after a real scroll settles — not the initial false emit
                            // (that was overwriting the saved timer with the 2h default on launch).
                            if (wasScrolling && !scrolling) commit(hours, minutes)
                        },
                        textColor = Color.White
                    )
                    Text("h", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                    androidx.compose.runtime.key(hours >= 24) {
                        WheelPicker(
                            items = minuteItems,
                            selectedIndex = minuteIndex,
                            onSelected = { m ->
                                minutes = if (hours >= 24) 0 else m.coerceIn(0, 59)
                            },
                            onScrollInProgressChange = { scrolling ->
                                val wasScrolling = minutesScrolling
                                minutesScrolling = scrolling
                                if (wasScrolling && !scrolling) commit(hours, minutes)
                            },
                            textColor = Color.White
                        )
                    }
                    Text("m", color = Color.White, fontSize = 18.sp)
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickTimers.forEach { mins ->
                        val selected = (hours * 60 + if (hours >= 24) 0 else minutes) == mins
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.35f)
                                    else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    hours = (mins / 60).coerceIn(0, 24)
                                    minutes = if (hours >= 24) 0 else mins % 60
                                    commit(hours, minutes)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(formatQuickLabel(mins), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val total = hours * 60 + if (hours >= 24) 0 else minutes
                    Button(
                        onClick = {
                            // Re-read local wheel state at click time (kept live while scrolling).
                            val live = hours * 60 + if (hours >= 24) 0 else minutes
                            if (live <= 0) return@Button
                            commit(hours, minutes)
                            onStart(live.coerceAtMost(24 * 60))
                        },
                        enabled = total > 0 && !wheelSettling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Start", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatQuickLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun ProfileCard(profile: Profile, onStart: () -> Unit, onEdit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = if (profile.noLimit) "No limit" else formatDuration(profile.durationMinutes)
                val apps = if (profile.whitelist.isEmpty()) "" else "  ·  ${profile.whitelist.size} app(s)"
                Text(
                    sub + apps,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, "Edit profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(4.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Start", color = Color.White)
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}
