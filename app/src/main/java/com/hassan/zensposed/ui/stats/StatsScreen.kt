package com.hassan.zensposed.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hassan.zensposed.data.db.CategoryTotal
import com.hassan.zensposed.data.db.SessionEntity
import com.hassan.zensposed.ui.components.RingChart
import com.hassan.zensposed.ui.components.RingSegment
import com.hassan.zensposed.ui.theme.StatCyan
import com.hassan.zensposed.ui.theme.StatOrange
import com.hassan.zensposed.ui.theme.StatPurple
import com.hassan.zensposed.ui.theme.StatYellow
import com.hassan.zensposed.ui.theme.ZenBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val palette = listOf(ZenBlue, StatOrange, StatYellow, StatCyan, StatPurple)

private fun formatHm(ms: Long): Pair<Int, Int> {
    val totalMinutes = (ms / 60000L).toInt()
    return totalMinutes / 60 to totalMinutes % 60
}

private fun formatDuration(ms: Long): String {
    val (h, m) = formatHm(ms)
    val s = ((ms / 1000L) % 60L).toInt()
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
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
                val (dh, dm) = formatHm(state.dayFocusedMs)
                TotalCard("Today", dh, dm, state.daySessions)
                Spacer(Modifier.height(16.dp))
                val (wh, wm) = formatHm(state.weekFocusedMs)
                TotalCard("This week", wh, wm, state.weekSessions)
                Spacer(Modifier.height(16.dp))
                MonthCard(state)
                Spacer(Modifier.height(24.dp))
                Text(
                    "History",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                if (state.history.isEmpty()) {
                    Text(
                        "No focus sessions yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.history, key = { it.id }) { session ->
                HistoryRow(session)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HistoryRow(session: SessionEntity) {
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    session.spaceName,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatDuration(session.focusedMs),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                dateFmt.format(Date(session.startTime)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${timeFmt.format(Date(session.startTime))} – ${timeFmt.format(Date(session.endTime))}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TotalCard(label: String, hours: Int, minutes: Int, sessions: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$hours", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" hours ", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                Text("$minutes", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" minutes", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text("$sessions sessions of Zen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthCard(state: StatsUiState) {
    val (mh, mm) = formatHm(state.monthFocusedMs)
    val segments = state.monthCategories.mapIndexed { i, cat ->
        RingSegment(cat.totalMs.toFloat(), palette[i % palette.size], cat.spaceName)
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("This month", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$mh", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" h ", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                Text("$mm", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" m", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingChart(segments = segments) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.monthSessions}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("sessions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            state.monthCategories.forEachIndexed { i, cat ->
                LegendRow(cat, palette[i % palette.size])
            }
            if (state.monthCategories.isEmpty()) {
                Text(
                    "No focus sessions yet this month.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendRow(cat: CategoryTotal, color: Color) {
    val (h, m) = formatHm(cat.totalMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(cat.spaceName, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            if (h > 0) "${h}h ${m}m" else "${m}m",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}
