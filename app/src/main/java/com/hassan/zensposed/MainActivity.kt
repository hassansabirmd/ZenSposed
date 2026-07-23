package com.hassan.zensposed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.core.PrivilegeRequirements
import com.hassan.zensposed.data.settings.FocusSettings
import com.hassan.zensposed.data.settings.SettingsRepository
import com.hassan.zensposed.focus.FocusController
import com.hassan.zensposed.focus.FocusState
import com.hassan.zensposed.ui.MainViewModel
import com.hassan.zensposed.ui.focus.FocusActivity
import com.hassan.zensposed.ui.home.HomeScreen
import com.hassan.zensposed.ui.onboarding.OnboardingScreen
import com.hassan.zensposed.ui.profile.ProfileEditorScreen
import com.hassan.zensposed.ui.settings.SettingsScreen
import com.hassan.zensposed.ui.stats.StatsScreen
import com.hassan.zensposed.ui.stats.StatsViewModel
import com.hassan.zensposed.ui.theme.ZenSposedTheme

class MainActivity : ComponentActivity() {

    private var congratsFocusedMs by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeCongratsExtra(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.appearanceMode) {
                FocusSettings.APPEARANCE_LIGHT -> false
                FocusSettings.APPEARANCE_DARK -> true
                else -> isSystemInDarkTheme()
            }
            ZenSposedTheme(darkTheme = darkTheme) {
                val bootReady by viewModel.bootReady.collectAsStateWithLifecycle()
                val profiles by viewModel.profiles.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                if (!bootReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    // No limited/normal mode — root + LSPosed hooks are mandatory.
                    val privilegesReady = PrivilegeRequirements.check().ready
                    val onboarded = settings.onboardingDone ||
                        SettingsRepository.isOnboardingDoneSync(this@MainActivity)
                    val start = if (onboarded && privilegesReady) "home" else "onboarding"

                    NavHost(navController = navController, startDestination = start) {
                        composable("onboarding") {
                            OnboardingScreen(onContinue = {
                                viewModel.completeOnboarding()
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                profiles = profiles,
                                deepZenWhitelist = settings.whitelist,
                                deepZenMinutes = settings.deepZenMinutes,
                                quickTimersMinutes = settings.quickTimersMinutes,
                                onDeepZenMinutesChange = viewModel::setDeepZenMinutes,
                                onStartFocus = { minutes, noLimit, name, whitelist ->
                                    if (!noLimit && minutes <= 0) return@HomeScreen
                                    FocusController.startFocus(
                                        this@MainActivity,
                                        durationMinutes = minutes,
                                        noLimit = noLimit,
                                        spaceName = name,
                                        themeId = settings.themeId,
                                        whitelist = whitelist
                                    )
                                },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenStats = { navController.navigate("stats") },
                                onNewProfile = {
                                    viewModel.startNewProfile()
                                    navController.navigate("profileEditor")
                                },
                                onEditProfile = { p ->
                                    viewModel.startEditProfile(p)
                                    navController.navigate("profileEditor")
                                }
                            )
                        }
                        composable("profileEditor") {
                            ProfileEditorScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("stats") {
                            val statsVm: StatsViewModel = viewModel()
                            StatsScreen(viewModel = statsVm, onBack = { navController.popBackStack() })
                        }
                    }
                }

                if (congratsFocusedMs >= 0L) {
                    val totalMin = (congratsFocusedMs / 60_000L).toInt().coerceAtLeast(0)
                    val label = when {
                        totalMin <= 0 -> "less than a minute"
                        totalMin == 1 -> "1 focus minute"
                        else -> "$totalMin focus minutes"
                    }
                    AlertDialog(
                        onDismissRequest = { congratsFocusedMs = -1L },
                        title = { Text("Congratulations") },
                        text = { Text("$label completed.") },
                        confirmButton = {
                            TextButton(onClick = { congratsFocusedMs = -1L }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeCongratsExtra(intent)
    }

    private fun consumeCongratsExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(Constants.EXTRA_SHOW_CONGRATS, false) == true) {
            congratsFocusedMs = intent.getLongExtra(Constants.EXTRA_FOCUSED_MS, 0L)
            intent.removeExtra(Constants.EXTRA_SHOW_CONGRATS)
            intent.removeExtra(Constants.EXTRA_FOCUSED_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (FocusState.isActive(this)) {
            startActivity(
                Intent(this, FocusActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
