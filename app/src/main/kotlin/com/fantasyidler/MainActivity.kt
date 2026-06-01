package com.fantasyidler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.ui.navigation.AppNavigation
import com.fantasyidler.ui.theme.FantasyIdlerTheme
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — notifications just won't appear if denied */ }

    private val pendingNavigateTo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        if (savedInstanceState == null) {
            pendingNavigateTo.value = intent?.getStringExtra(SessionNotificationManager.EXTRA_NAVIGATE_TO)
        }
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themePreference by settingsViewModel.themePreference.collectAsStateWithLifecycle()
            val fontScale       by settingsViewModel.fontScale.collectAsStateWithLifecycle()
            val baseDensity = LocalDensity.current
            FantasyIdlerTheme(themePreference = themePreference) {
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale)
                ) {
                    AppNavigation(
                        pendingNavigateTo  = pendingNavigateTo.value,
                        onNavigateConsumed = { pendingNavigateTo.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigateTo.value = intent.getStringExtra(SessionNotificationManager.EXTRA_NAVIGATE_TO)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
