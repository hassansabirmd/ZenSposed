package com.hassan.zensposed.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hassan.zensposed.MainActivity
import com.hassan.zensposed.R
import com.hassan.zensposed.core.AppResolver
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.data.db.AppDatabase
import com.hassan.zensposed.data.db.SessionEntity
import com.hassan.zensposed.data.settings.SettingsRepository
import com.hassan.zensposed.lock.LockEnforcer
import com.hassan.zensposed.root.RootManager
import com.hassan.zensposed.ui.focus.FocusActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the running timer, drives all enforcement layers,
 * and keeps the focus screen in front until the timer elapses.
 */
class FocusService : LifecycleService() {

    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var presentReceiver: com.hassan.zensposed.receiver.UserPresentReceiver? = null

    private lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        createChannel()
        registerPresentReceiver()
    }

    private fun registerPresentReceiver() {
        presentReceiver = com.hassan.zensposed.receiver.UserPresentReceiver()
        val filter = android.content.IntentFilter().apply {
            // Only relaunch after unlock — ignore SCREEN_ON so power-off works.
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(presentReceiver, filter)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            Constants.ACTION_START_FOCUS -> handleStart(intent)
            Constants.ACTION_STOP_FOCUS -> handleStop(recordCompleted = false, showCongrats = false)
            else -> resumeIfActive()
        }
        return START_STICKY
    }

    private fun resumeIfActive() {
        val session = FocusState.current(this) ?: run {
            stopSelf()
            return
        }
        beginForeground(session.spaceName, session.endTime)
        acquireWakeLock()
        lifecycleScope.launch {
            val s = settings.settings.first()
            LockEnforcer.onSessionStart(
                this@FocusService,
                endTime = session.endTime,
                allowPanel = s.allowNotificationPanel,
                blockHome = s.blockHomeButton,
                blockRecent = s.blockRecentApps,
                whitelist = session.whitelist
            )
        }
        startTicking(session)
        launchFocusScreen()
    }

    private fun handleStart(intent: Intent) {
        if (!com.hassan.zensposed.core.PrivilegeRequirements.check().ready) {
            android.util.Log.e("ZenSposed/Service", "Refusing session — root/LSPosed not ready")
            stopSelf()
            return
        }
        val minutes = intent.getIntExtra(Constants.EXTRA_DURATION_MINUTES, 25)
        val noLimit = intent.getBooleanExtra("no_limit", false)
        if (!noLimit && minutes <= 0) {
            stopSelf()
            return
        }
        val spaceName = intent.getStringExtra(Constants.EXTRA_SPACE_NAME) ?: "Focus"
        val themeId = intent.getStringExtra(Constants.EXTRA_THEME_ID) ?: "ocean"
        val whitelistCsv = intent.getStringExtra(Constants.EXTRA_WHITELIST) ?: ""
        val requestedWhitelist = whitelistCsv.split(",").filter { it.isNotBlank() }.toSet()

        val alwaysAllowed = AppResolver.alwaysAllowedPackages(this)
        val sessionWhitelist = requestedWhitelist + alwaysAllowed

        val now = System.currentTimeMillis()
        val plannedMs = if (noLimit) 0L else minutes.toLong() * 60_000L
        val endTime = if (noLimit) Long.MAX_VALUE else now + plannedMs

        FocusState.start(this, now, endTime, plannedMs, spaceName, themeId, noLimit, sessionWhitelist)

        beginForeground(spaceName, endTime)
        acquireWakeLock()

        lifecycleScope.launch {
            val s = settings.settings.first()
            LockEnforcer.onSessionStart(
                this@FocusService,
                endTime = endTime,
                allowPanel = s.allowNotificationPanel,
                blockHome = s.blockHomeButton,
                blockRecent = s.blockRecentApps,
                whitelist = sessionWhitelist
            )
        }

        startTicking(FocusState.current(this)!!)
        launchFocusScreen()
    }

    private fun handleStop(recordCompleted: Boolean, showCongrats: Boolean) {
        val session = FocusState.current(this)
        tickJob?.cancel()
        var focusedMs = 0L
        if (session != null) {
            val now = System.currentTimeMillis()
            focusedMs = (now - session.startTime).coerceAtLeast(0L)
            recordSession(session, focusedMs, recordCompleted)
            val toKill = session.whitelist - AppResolver.alwaysAllowedPackages(this)
            RootManager.forceStopPackages(toKill)
        }
        FocusState.clear(this)
        LockEnforcer.onSessionStop(this)
        releaseWakeLock()
        FocusController.publish(FocusController.UiState(active = false))

        // Close the focus screen and optionally show a congratulations dialog.
        val main = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            if (showCongrats && focusedMs > 0L) {
                putExtra(Constants.EXTRA_SHOW_CONGRATS, true)
                putExtra(Constants.EXTRA_FOCUSED_MS, focusedMs)
            }
        }
        startActivity(main)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordSession(session: FocusState.Session, focusedMs: Long, completed: Boolean) {
        val dao = AppDatabase.get(this).sessionDao()
        lifecycleScope.launch {
            dao.insert(
                SessionEntity(
                    spaceName = session.spaceName,
                    startTime = session.startTime,
                    endTime = System.currentTimeMillis(),
                    focusedMs = focusedMs,
                    plannedMs = session.plannedMs,
                    completed = completed
                )
            )
        }
    }

    private fun startTicking(session: FocusState.Session) {
        tickJob?.cancel()
        tickJob = lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = (now - session.startTime).coerceAtLeast(0L)
                val remaining = if (session.noLimit) 0L else (session.endTime - now).coerceAtLeast(0L)

                FocusController.publish(
                    FocusController.UiState(
                        active = true,
                        remainingMs = remaining,
                        elapsedMs = elapsed,
                        plannedMs = session.plannedMs,
                        spaceName = session.spaceName,
                        themeId = session.themeId,
                        noLimit = session.noLimit,
                        whitelist = session.whitelist
                    )
                )

                if (!session.noLimit && remaining <= 0L) {
                    handleStop(recordCompleted = true, showCongrats = true)
                    return@launch
                }

                // If the user left a whitelisted app for the launcher / another app,
                // pull focus back. Whitelisted packages (and our own) are left alone.
                enforceTopApp(session)

                delay(1000L)
            }
        }
    }

    private fun enforceTopApp(session: FocusState.Session) {
        if (com.hassan.zensposed.ui.focus.FocusActivity.isReassertSuppressed()) return
        val top = RootManager.topResumedPackage() ?: return
        if (top == Constants.PACKAGE_NAME) return
        if (top == "com.android.systemui") return
        if (AppResolver.isTransientSystemUi(top)) return
        if (AppResolver.isInputMethod(top, this)) return
        if (top in session.whitelist) return
        if (top in AppResolver.alwaysAllowedPackages(this)) return
        // Launcher or any other non-allowed package → bring focus back.
        RootManager.forceLaunchFocus()
    }

    private fun launchFocusScreen() {
        val intent = Intent(this, FocusActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun beginForeground(spaceName: String, endTime: Long) {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = Notification.Builder(this, Constants.NOTIF_CHANNEL_ID)
            .setContentTitle("ZenSposed active")
            .setContentText("Session \"$spaceName\" in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Constants.NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            Constants.NOTIF_CHANNEL_ID,
            "Focus session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing focus-lock session"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        // PARTIAL_WAKE_LOCK keeps the CPU alive for the timer but does NOT keep the
        // screen on — the user must be able to lock / turn off the display.
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZenSposed::session").apply {
            setReferenceCounted(false)
            acquire(Constants.MAX_TIMER_MINUTES.toLong() * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        tickJob?.cancel()
        releaseWakeLock()
        try {
            presentReceiver?.let { unregisterReceiver(it) }
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
