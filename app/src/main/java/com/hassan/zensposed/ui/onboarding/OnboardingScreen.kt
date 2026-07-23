package com.hassan.zensposed.ui.onboarding

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hassan.zensposed.core.PermissionUtils
import com.hassan.zensposed.core.PrivilegeRequirements
import com.hassan.zensposed.root.RootManager

private data class PermItem(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onRequest: () -> Unit
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val hasNotif = remember(refresh) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasOverlay = remember(refresh) { PermissionUtils.canDrawOverlays(context) }
    val hasBattery = remember(refresh) { PermissionUtils.isIgnoringBatteryOptimizations(context) }
    val hasAccessibility = remember(refresh) { PermissionUtils.isAccessibilityEnabled(context) }
    val privileges = remember(refresh) { PrivilegeRequirements.check() }

    val items = listOf(
        PermItem(
            "Superuser (root)",
            "Required. Magisk or KernelSU must grant su to ZenSposed.",
            privileges.root,
            {
                val ok = RootManager.isRootAvailable()
                Toast.makeText(
                    context,
                    if (ok) "Root granted" else "Root not available — approve the su prompt and retry",
                    Toast.LENGTH_LONG
                ).show()
                refresh++
            }
        ),
        PermItem(
            "LSPosed / Xposed (Zygisk)",
            "Required. Install LSPosed, enable ZenSposed for System Framework (android), then soft reboot.",
            privileges.lsposedFramework && privileges.xposedHooks,
            {
                val status = PrivilegeRequirements.check()
                val msg = when {
                    !status.root -> "Grant root first, then check again"
                    !status.lsposedFramework -> "LSPosed not found under /data/adb — install Zygisk LSPosed"
                    !status.xposedHooks ->
                        "Hooks not loaded. In LSPosed enable ZenSposed → System Framework, soft reboot, then Grant again"
                    else -> "LSPosed hooks active"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                // Open LSPosed manager if present.
                runCatching {
                    val launch = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                    if (launch != null) context.startActivity(launch)
                }
                refresh++
            }
        ),
        PermItem("Notifications", "Show the ongoing session notification.", hasNotif, {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }),
        PermItem("Display over other apps", "Keep the focus screen pinned in front.", hasOverlay, {
            context.startActivity(PermissionUtils.overlaySettingsIntent(context))
        }),
        PermItem("Ignore battery optimization", "Stay alive during long sessions.", hasBattery, {
            context.startActivity(PermissionUtils.batteryOptimizationIntent(context))
        }),
        PermItem(
            "Accessibility service",
            "Secondary watchdog that re-asserts focus if a non-whitelisted app opens.",
            hasAccessibility,
            { context.startActivity(PermissionUtils.accessibilityIntent()) }
        )
    )

    val allGranted = items.all { it.granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Welcome to", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "ZenSposed",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This app requires Magisk/KernelSU (Zygisk), LSPosed, and root. " +
                "There is no limited mode without them.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        items.forEach { item ->
            PermissionCard(item)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onContinue,
            enabled = allGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (allGranted) "Continue" else "Complete required setup", fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(item: PermItem) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (item.granted) Color(0xFF16A34A).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.background,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.granted) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(8.dp))
            if (!item.granted) {
                OutlinedButton(onClick = item.onRequest, shape = RoundedCornerShape(20.dp)) {
                    Text("Grant")
                }
            }
        }
    }
}
