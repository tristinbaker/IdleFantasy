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
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.ui.navigation.AppNavigation
import com.fantasyidler.ui.theme.FantasyIdlerTheme
import com.fantasyidler.ui.theme.LocalAppFontScale
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var sessionNotificationManager: SessionNotificationManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — notifications just won't appear if denied */ }

    private val pendingNavigateTo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // One-time migration: re-register the backup alarm on first launch after update.
        // Previously setInexactRepeating was used with an unsupported weekly interval which
        // caused the alarm to fire once then silently stop. Players who had weekly backup
        // configured would never get another backup until a reboot or settings change.
        // Calling schedule() here is safe: it calls cancel() first and is a no-op if
        // backupFrequency is empty. This heals existing users without any manual action.
        if (savedInstanceState == null) {
            CoroutineScope(Dispatchers.IO).launch {
                val flags = playerRepository.getFlags()
                if (flags.backupFrequency.isNotEmpty()) {
                    backupScheduler.schedule(flags.backupFrequency)
                }
            }
        }
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
                    LocalDensity provides Density(baseDensity.density, fontScale),
                    LocalAppFontScale provides fontScale,
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

    override fun onStart() {
        super.onStart()
        sessionNotificationManager.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        sessionNotificationManager.setAppInForeground(false)
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
