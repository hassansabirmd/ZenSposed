package com.hassan.zensposed.ui.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.hassan.zensposed.ZenSposedApp
import com.hassan.zensposed.core.AppResolver
import com.hassan.zensposed.data.model.NatureTheme
import com.hassan.zensposed.focus.DndController
import com.hassan.zensposed.focus.FocusController
import com.hassan.zensposed.focus.TorchController
import com.hassan.zensposed.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

private data class FocusApp(val pkg: String, val label: String, val icon: Drawable?)

private const val APPS_PER_ROW = 5
private const val MAX_VISIBLE_ROWS = 3
private const val MAX_VISIBLE_APPS = APPS_PER_ROW * MAX_VISIBLE_ROWS

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun FocusScreen(
    ui: FocusController.UiState,
    aodEnabled: Boolean,
    aodDimmed: Boolean,
    onUserInteraction: () -> Unit,
    onExitConfirmed: () -> Unit,
    onLaunchAllowedApp: (String) -> Unit,
    onOpenWifiPicker: () -> Unit
) {
    val context = LocalContext.current
    val theme = NatureTheme.fromId(ui.themeId)
    var showExit by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<FocusApp>>(emptyList()) }
    var quickToggles by remember { mutableStateOf(false) }
    var showMoreApps by remember { mutableStateOf(false) }
    var batteryPct by remember { mutableIntStateOf(readBatteryPercent(context)) }
    var batterySaver by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        batterySaver = withContext(Dispatchers.IO) { RootManager.isBatterySaverOn() }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryPct = readBatteryPercent(context)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerSystemReceiver(context, receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(ui.whitelist) {
        val settings = ZenSposedApp.instance.settingsRepository.settings.first()
        quickToggles = settings.allowQuickToggles
        val pm = context.packageManager
        val defaults = AppResolver.alwaysAllowedLaunchable(context)
        val ordered = LinkedHashSet<String>().apply {
            addAll(defaults)
            addAll(ui.whitelist)
        }
        apps = ordered.mapNotNull { pkg ->
            runCatching {
                if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                val info = pm.getApplicationInfo(pkg, 0)
                FocusApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
            }.getOrNull()
        }
    }

    val dateStr = remember {
        SimpleDateFormat("MMM d, EEEE", java.util.Locale.getDefault()).format(Date())
    }
    val visible = apps.take(MAX_VISIBLE_APPS)
    val overflow = apps.drop(MAX_VISIBLE_APPS)
    val dimOverlay = aodEnabled && aodDimmed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(theme.gradient))
            .clickable(enabled = dimOverlay, indication = null, interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            }) { onUserInteraction() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!dimOverlay) {
                Spacer(Modifier.height(48.dp))
                Text(dateStr, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(ui.spaceName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Spacer(Modifier.height(72.dp))
                Text("ZenSposed", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
            }

            Spacer(Modifier.weight(1f))
            CountdownRing(ui = ui, batteryPercent = batteryPct)
            Spacer(Modifier.weight(1f))

            if (!dimOverlay) {
                if (apps.isNotEmpty()) {
                    Text("Allowed apps", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    AllowedAppsGrid(
                        apps = visible,
                        onLaunch = onLaunchAllowedApp,
                        showMore = overflow.isNotEmpty(),
                        onMore = { showMoreApps = true }
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (quickToggles) {
                    QuickTogglesRow(onOpenWifiPicker = onOpenWifiPicker)
                    Spacer(Modifier.height(20.dp))
                }

                BottomActionRow(
                    batterySaverOn = batterySaver,
                    onClearRecents = {
                        onUserInteraction()
                        FocusActivity.suppressReassertFor(8_000L)
                        val ok = RootManager.clearRecentApps()
                        Toast.makeText(
                            context,
                            if (ok) "Recent apps cleared" else "Could not clear recents",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleBatterySaver = {
                        onUserInteraction()
                        val next = !batterySaver
                        if (RootManager.setBatterySaver(next)) {
                            batterySaver = next
                        }
                    },
                    onExit = {
                        onUserInteraction()
                        showExit = true
                    }
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    "Tap to wake",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showExit) {
        EmergencyExitDialog(
            onDismiss = { showExit = false },
            onVerified = { onExitConfirmed() }
        )
    }

    if (showMoreApps) {
        MoreAppsDialog(
            apps = overflow,
            onLaunch = {
                showMoreApps = false
                onLaunchAllowedApp(it)
            },
            onDismiss = { showMoreApps = false }
        )
    }
}

@Composable
private fun BottomActionRow(
    batterySaverOn: Boolean,
    onClearRecents: () -> Unit,
    onToggleBatterySaver: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        CircleActionButton(
            icon = Icons.Filled.LayersClear,
            label = "Clear",
            active = false,
            size = 56.dp,
            onClick = onClearRecents
        )
        CircleActionButton(
            icon = Icons.AutoMirrored.Filled.Logout,
            label = "Exit",
            active = false,
            size = 56.dp,
            onClick = onExit
        )
        CircleActionButton(
            icon = Icons.Filled.BatterySaver,
            label = "Saver",
            active = batterySaverOn,
            size = 56.dp,
            onClick = onToggleBatterySaver
        )
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (active) {
                        Modifier
                            .background(Color.White.copy(alpha = 0.55f), CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (active) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowedAppsGrid(
    apps: List<FocusApp>,
    onLaunch: (String) -> Unit,
    showMore: Boolean,
    onMore: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = APPS_PER_ROW
        ) {
            apps.forEach { app ->
                AppIcon(app) { onLaunch(app.pkg) }
            }
        }
        if (showMore) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, "More apps", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MoreAppsDialog(
    apps: List<FocusApp>,
    onLaunch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("More apps", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(APPS_PER_ROW),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(apps) { app ->
                        AppIcon(app) { onLaunch(app.pkg) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(ui: FocusController.UiState, batteryPercent: Int) {
    val progress = if (ui.noLimit || ui.plannedMs <= 0L) {
        0f
    } else {
        (ui.remainingMs.toFloat() / ui.plannedMs.toFloat()).coerceIn(0f, 1f)
    }
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(260.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )
            if (!ui.noLimit) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ui.noLimit) "Elapsed" else "Time remaining",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                formatTime(if (ui.noLimit) ui.elapsedMs else ui.remainingMs),
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$batteryPercent%",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuickTogglesRow(onOpenWifiPicker: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var wifi by remember { mutableStateOf(isWifiEnabled(context)) }
    var data by remember { mutableStateOf(isMobileDataEnabled(context)) }
    var hotspot by remember { mutableStateOf(isHotspotEnabled(context)) }
    var torch by remember {
        TorchController.ensureObserving(context)
        mutableStateOf(TorchController.isOn())
    }
    var dnd by remember { mutableStateOf(DndController.isOn(context)) }

    fun refreshAll() {
        wifi = isWifiEnabled(context)
        data = isMobileDataEnabled(context)
        hotspot = isHotspotEnabled(context)
        torch = TorchController.isOn()
        dnd = DndController.isOn(context)
    }

    // Keep icons in sync when QS / status bar / other apps flip radio or torch state.
    DisposableEffect(Unit) {
        TorchController.ensureObserving(context)
        val unregTorch = TorchController.addListener { on -> torch = on }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        // Prefer the intent extra — more reliable than WifiManager on Pixel.
                        val state = intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN
                        )
                        wifi = when (state) {
                            WifiManager.WIFI_STATE_ENABLED,
                            WifiManager.WIFI_STATE_ENABLING -> true
                            WifiManager.WIFI_STATE_DISABLED,
                            WifiManager.WIFI_STATE_DISABLING -> false
                            else -> isWifiEnabled(context)
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    ConnectivityManager.CONNECTIVITY_ACTION -> {
                        wifi = isWifiEnabled(context)
                        data = isMobileDataEnabled(context)
                    }
                    WIFI_AP_STATE_CHANGED_ACTION -> {
                        hotspot = isHotspotEnabled(context)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        dnd = DndController.isOn(context)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WIFI_AP_STATE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        // System radio broadcasts must be receivable from outside this app.
        registerSystemReceiver(context, receiver, filter)

        // Settings.Global.WIFI_ON updates even when WifiManager.isWifiEnabled is stale.
        val wifiObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                wifi = isWifiEnabled(context)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                android.provider.Settings.Global.getUriFor("wifi_on"),
                false,
                wifiObserver
            )
        }

        refreshAll()
        onDispose {
            unregTorch()
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.contentResolver.unregisterContentObserver(wifiObserver) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Shade toggles often leave Focus resumed; poll so icons catch up quickly.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_200L)
            refreshAll()
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ToggleTile(
            icon = Icons.Filled.Wifi,
            label = "Wi-Fi",
            active = wifi,
            onClick = {
                val next = !isWifiEnabled(context)
                wifi = next
                RootManager.setWifi(next)
            },
            onLongClick = onOpenWifiPicker
        )
        ToggleTile(Icons.Filled.SignalCellularAlt, "Data", data) {
            data = !data
            RootManager.setMobileData(data)
        }
        ToggleTile(Icons.Filled.WifiTethering, "Hotspot", hotspot) {
            hotspot = !hotspot
            RootManager.setHotspot(hotspot)
        }
        ToggleTile(Icons.Filled.FlashlightOn, "Torch", torch) {
            torch = TorchController.toggle(context)
        }
        ToggleTile(Icons.Filled.DoNotDisturbOn, "DND", dnd) {
            val next = !dnd
            if (DndController.set(context, next)) {
                dnd = next
            } else {
                DndController.openPolicySettings(context)
            }
        }
    }
}

private const val WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"

/** Register for system-originated broadcasts (Wi‑Fi / DND / tether). */
private fun registerSystemReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    filter: IntentFilter
) {
    try {
        // EXPORTED: these actions are sent by the system / other packages, not this app.
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } catch (_: Throwable) {
        try {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            runCatching { context.registerReceiver(receiver, filter) }
        }
    }
}

/**
 * Wi‑Fi radio on/off. Prefer Settings.Global.WIFI_ON — on recent Pixel builds
 * [WifiManager.isWifiEnabled] can lag or fail while the global setting stays accurate.
 */
private fun isWifiEnabled(context: Context): Boolean {
    try {
        // 0 = off, 1 = on, 2 = on (airplane override still stores enabled desire on some builds)
        val v = android.provider.Settings.Global.getInt(context.contentResolver, "wifi_on", -1)
        if (v >= 0) return v != 0
    } catch (_: Throwable) {
    }
    return try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wm.isWifiEnabled
    } catch (_: Throwable) {
        false
    }
}

private fun isMobileDataEnabled(context: Context): Boolean = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
} catch (_: Throwable) {
    false
}

/** Soft-AP / hotspot on — reflection for the hidden WifiManager API, then settings fallback. */
@Suppress("DEPRECATION")
private fun isHotspotEnabled(context: Context): Boolean {
    try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val method = wm.javaClass.getMethod("isWifiApEnabled")
        return method.invoke(wm) as? Boolean == true
    } catch (_: Throwable) {
    }
    return try {
        // wifi_ap_state: 13 = WIFI_AP_STATE_ENABLED
        android.provider.Settings.Global.getInt(context.contentResolver, "wifi_ap_state", 11) == 13
    } catch (_: Throwable) {
        false
    }
}

private fun readBatteryPercent(context: Context): Int {
    return try {
        val bm = context.getSystemService(BatteryManager::class.java)
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    } catch (_: Throwable) {
        0
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(
                    if (active) {
                        Modifier
                            .background(Color.White.copy(alpha = 0.55f), CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    }
                )
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (active) Color.Black else Color.White.copy(alpha = 0.45f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AppIcon(app: FocusApp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (app.icon != null) {
                Image(
                    painter = rememberAsyncImagePainter(app.icon),
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Text(app.label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
