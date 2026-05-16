package com.fantasyidler.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReopenTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val themePreference by viewModel.themePreference.collectAsState()
    var notificationsEnabled by remember { mutableStateOf(false) }
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportSave { jsonString ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
            scope.launch { snackbarHostState.showSnackbar("Save exported") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return@rememberLauncherForActivityResult
        viewModel.importSave(jsonString) { success ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (success) "Save imported successfully" else "Failed to import — invalid file"
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    if (showResetConfirm1) {
        AlertDialog(
            onDismissRequest = { showResetConfirm1 = false },
            title   = { Text(stringResource(R.string.reset_confirm1_title)) },
            text    = { Text(stringResource(R.string.reset_confirm1_body)) },
            confirmButton = {
                Button(
                    onClick = { showResetConfirm1 = false; showResetConfirm2 = true },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_reset_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm1 = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showResetConfirm2) {
        AlertDialog(
            onDismissRequest = { showResetConfirm2 = false },
            title   = { Text(stringResource(R.string.reset_confirm2_title)) },
            text    = { Text(stringResource(R.string.reset_confirm2_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm2 = false
                        viewModel.resetProgression()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.reset_confirm2_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm2 = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Appearance section
            SectionHeader(title = "Appearance")

            SettingsRow(
                title    = "Theme",
                subtitle = "Choose your preferred colour scheme",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "dark"   to "Dark",
                            "light"  to "Light",
                            "system" to "System",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = themePreference == key,
                                onClick  = { viewModel.setTheme(key) },
                                label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            )

            // Notifications section
            HorizontalDivider()
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

            SettingsRow(
                title    = stringResource(R.string.settings_reset_title),
                subtitle = stringResource(R.string.settings_reset_desc),
                trailing = {
                    OutlinedButton(
                        onClick = { showResetConfirm1 = true },
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_reset_btn))
                    }
                }
            )

            // Save data section
            HorizontalDivider()

            SectionHeader(title = "Save Data")

            SettingsRow(
                title    = "Export Save",
                subtitle = "Save your progress to a file",
                trailing = {
                    OutlinedButton(onClick = { exportLauncher.launch("fantasyidler_save.json") }) {
                        Text("Export")
                    }
                }
            )

            SettingsRow(
                title    = "Import Save",
                subtitle = "Restore progress from a file",
                trailing = {
                    OutlinedButton(onClick = { importLauncher.launch("*/*") }) {
                        Text("Import")
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

            SettingsRow(
                title    = "Source Code",
                subtitle = "github.com/tristinbaker/IdleFantasy",
                trailing = {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tristinbaker/IdleFantasy"))
                            )
                        }
                    ) {
                        Text("Open")
                    }
                }
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
