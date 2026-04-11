package com.fantasyidler.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.fantasyidler.BuildConfig
import com.fantasyidler.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReopenTutorial: () -> Unit = {}) {
    val context = LocalContext.current

    var notificationsEnabled by remember { mutableStateOf(false) }
    var isIgnoringBattery by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        isIgnoringBattery = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notifications section
            SectionHeader(title = stringResource(R.string.settings_notifications_header))

            SettingsRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_desc),
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    )
                }
            )

            // Battery section
            SectionHeader(title = stringResource(R.string.settings_performance_header))

            SettingsRow(
                title = stringResource(R.string.settings_battery_optimization),
                subtitle = stringResource(R.string.settings_battery_optimization_desc),
                trailing = {
                    if (isIgnoringBattery) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.settings_battery_optimization_btn))
                        }
                    }
                }
            )

            // General section
            HorizontalDivider()

            SectionHeader(title = stringResource(R.string.settings_general_header))

            SettingsRow(
                title    = stringResource(R.string.settings_tutorial_title),
                subtitle = stringResource(R.string.settings_tutorial_desc),
                trailing = {
                    OutlinedButton(onClick = { onReopenTutorial(); onBack() }) {
                        Text(stringResource(R.string.settings_reopen))
                    }
                }
            )

            // About section
            HorizontalDivider()

            SectionHeader(title = stringResource(R.string.settings_about))

            SettingsRow(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.format_version, BuildConfig.VERSION_NAME)
            )

            Text(
                text = stringResource(R.string.settings_foss_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (trailing != null) 16.dp else 0.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
