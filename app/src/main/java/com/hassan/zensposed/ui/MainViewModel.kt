package com.hassan.zensposed.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.zensposed.ZenSposedApp
import com.hassan.zensposed.core.AppResolver
import com.hassan.zensposed.data.model.Profile
import com.hassan.zensposed.data.settings.FocusSettings
import com.hassan.zensposed.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ZenSposedApp
    private val settingsRepo = application.settingsRepository
    private val securePrefs = application.securePrefs

    /** True once we know onboarding status (sync prefs or first DataStore emit). */
    private val _bootReady = MutableStateFlow(
        SettingsRepository.isOnboardingDoneSync(application)
    )
    val bootReady: StateFlow<Boolean> = _bootReady

    val settings: StateFlow<FocusSettings> = settingsRepo.settings
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FocusSettings(
                onboardingDone = SettingsRepository.isOnboardingDoneSync(application),
                deepZenMinutes = SettingsRepository.deepZenMinutesSync(application) ?: 120
            )
        )

    init {
        viewModelScope.launch {
            // One-time heal: if settings say "no password" but a hash exists, it is an
            // orphan from a failed/partial write — clear it so first-time set works.
            val snap = settingsRepo.settings.first()
            if (!snap.hasPassword && securePrefs.hasPassword()) {
                securePrefs.clearPassword()
            } else if (snap.hasPassword != securePrefs.hasPassword()) {
                settingsRepo.setHasPassword(securePrefs.hasPassword())
            }
            settingsRepo.settings.collect { s ->
                if (s.onboardingDone) {
                    SettingsRepository.writeOnboardingSync(application, true)
                }
                _bootReady.value = true
            }
        }
    }

    val profiles: StateFlow<List<Profile>> = settingsRepo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Packages always allowed in the UI picker (dialer/messaging/contacts/calculator). */
    val alwaysAllowed: Set<String> by lazy { AppResolver.defaultUiPackages(application) }

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    /** Profile currently being created/edited (drives the editor screen). */
    val editingProfile = MutableStateFlow<Profile?>(null)

    fun startNewProfile() {
        editingProfile.value = Profile(
            id = java.util.UUID.randomUUID().toString(),
            name = "",
            durationMinutes = 30,
            noLimit = false,
            whitelist = emptySet()
        )
    }

    fun startEditProfile(profile: Profile) {
        editingProfile.value = profile
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val seen = LinkedHashSet<String>()
            val apps = ArrayList<InstalledApp>()

            fun addApp(info: ApplicationInfo, forceInclude: Boolean = false) {
                val pkg = info.packageName
                if (pkg == application.packageName) return
                if (!seen.add(pkg)) return
                val launchable = pm.getLaunchIntentForPackage(pkg) != null
                if (!launchable && !forceInclude) return
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                apps.add(
                    InstalledApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                        isSystem = isSystem
                    )
                )
            }

            pm.getInstalledApplications(0).forEach { addApp(it) }
            // Share / docs / permission UI often has no launcher icon — still listable.
            for (pkg in com.hassan.zensposed.core.Constants.SESSION_SYSTEM_UI) {
                runCatching {
                    addApp(pm.getApplicationInfo(pkg, 0), forceInclude = true)
                }
            }
            _installedApps.value = apps.sortedBy { it.label.lowercase() }
        }
    }

    fun toggleWhitelist(pkg: String, add: Boolean) {
        viewModelScope.launch {
            val current = settings.value.whitelist.toMutableSet()
            if (add) current.add(pkg) else current.remove(pkg)
            settingsRepo.setWhitelist(current)
        }
    }

    fun setWhitelist(packages: Set<String>) {
        viewModelScope.launch { settingsRepo.setWhitelist(packages) }
    }

    fun upsertProfile(profile: Profile) {
        viewModelScope.launch { settingsRepo.upsertProfile(profile) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { settingsRepo.deleteProfile(id) }
    }

    fun setAllowNotificationPanel(allow: Boolean) =
        viewModelScope.launch {
            settingsRepo.setAllowNotificationPanel(allow)
            pushStatusBarFlagsIfSessionActive(
                allowPanel = allow,
                blockHome = settings.value.blockHomeButton,
                blockRecent = settings.value.blockRecentApps
            )
        }

    fun setBlockHomeButton(block: Boolean) =
        viewModelScope.launch {
            settingsRepo.setBlockHomeButton(block)
            pushStatusBarFlagsIfSessionActive(
                allowPanel = settings.value.allowNotificationPanel,
                blockHome = block,
                blockRecent = settings.value.blockRecentApps
            )
        }

    fun setBlockRecentApps(block: Boolean) =
        viewModelScope.launch {
            settingsRepo.setBlockRecentApps(block)
            pushStatusBarFlagsIfSessionActive(
                allowPanel = settings.value.allowNotificationPanel,
                blockHome = settings.value.blockHomeButton,
                blockRecent = block
            )
        }

    fun setAllowQuickToggles(allow: Boolean) =
        viewModelScope.launch { settingsRepo.setAllowQuickToggles(allow) }

    private fun pushStatusBarFlagsIfSessionActive(
        allowPanel: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean
    ) {
        val session = com.hassan.zensposed.focus.FocusState.current(getApplication())
        if (session?.active != true) return
        com.hassan.zensposed.lock.LockEnforcer.updateBridge(
            getApplication(),
            endTime = session.endTime,
            allowPanel = allowPanel,
            blockHome = blockHome,
            blockRecent = blockRecent,
            whitelist = session.whitelist
        )
    }

    fun setTheme(themeId: String) = viewModelScope.launch { settingsRepo.setTheme(themeId) }

    fun setAppearanceMode(mode: String) =
        viewModelScope.launch { settingsRepo.setAppearanceMode(mode) }

    fun setAod(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAod(enabled) }

    fun setExitMethod(method: String) = viewModelScope.launch {
        if (method == FocusSettings.EXIT_QR) {
            // Don't auto-generate if the user may want to scan their own; only
            // ensure a secret exists when none is registered yet so exit isn't empty.
            // Leaving empty is OK — the manage dialog lets them scan or generate.
            settingsRepo.setExitMethod(FocusSettings.EXIT_QR)
        } else {
            settingsRepo.setExitMethod(FocusSettings.EXIT_PASSWORD)
        }
    }

    fun qrPayload(): String = securePrefs.ensureQrSecret()

    fun regenerateQr(): String = securePrefs.regenerateQrSecret()

    fun setCustomQr(scanned: String): Boolean = securePrefs.setCustomQrPayload(scanned)

    fun isCustomQr(): Boolean = securePrefs.isCustomQr()

    fun hasQrSecret(): Boolean = securePrefs.hasQrSecret()

    fun qrPayloadOrNull(): String? = securePrefs.qrPayloadOrNull()

    fun setDeepZenMinutes(minutes: Int) =
        viewModelScope.launch { settingsRepo.setDeepZenMinutes(minutes) }

    fun setQuickTimers(minutes: List<Int>) =
        viewModelScope.launch { settingsRepo.setQuickTimers(minutes) }

    fun completeOnboarding() = viewModelScope.launch { settingsRepo.setOnboardingDone(true) }

    fun hasPassword(): Boolean = securePrefs.hasPassword()

    fun verifyPassword(pw: String): Boolean = securePrefs.verify(pw)

    /** Change/set password. Requires current password only when one already exists in SecurePrefs. */
    suspend fun setPassword(newPassword: String, currentPassword: String?): Result<Unit> {
        val alreadySet = securePrefs.hasPassword()
        if (alreadySet) {
            if (currentPassword.isNullOrEmpty() || !securePrefs.verify(currentPassword)) {
                return Result.failure(IllegalArgumentException("Current password is incorrect"))
            }
        }
        if (newPassword.length < com.hassan.zensposed.core.Constants.MIN_PASSWORD_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Password must be at least ${com.hassan.zensposed.core.Constants.MIN_PASSWORD_LENGTH} characters")
            )
        }
        withContext(Dispatchers.IO) { securePrefs.setPassword(newPassword) }
        settingsRepo.setHasPassword(true)
        settingsRepo.setExitMethod(FocusSettings.EXIT_PASSWORD)
        return Result.success(Unit)
    }
}
