package com.fantasyidler.ui.screen

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyDialog
import com.fantasyidler.ui.components.foundation.DangerZone
import com.fantasyidler.ui.components.foundation.SettingsSectionHeader
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.SettingsViewModel
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
    val tokens = LocalFantasyTokens.current

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
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_exported_ok)) }
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
                    if (success) context.getString(R.string.settings_imported_ok)
                    else context.getString(R.string.settings_imported_fail)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    if (showResetConfirm1) {
        ChunkyDialog(
            title            = stringResource(R.string.reset_confirm1_title),
            onDismissRequest = { showResetConfirm1 = false },
            body             = { Text(stringResource(R.string.reset_confirm1_body)) },
            actions          = {
                ChunkyButton(
                    text    = stringResource(R.string.btn_cancel),
                    onClick = { showResetConfirm1 = false },
                    variant = ChunkyButtonVariant.Secondary,
                )
                ChunkyButton(
                    text    = stringResource(R.string.settings_reset_btn),
                    onClick = { showResetConfirm1 = false; showResetConfirm2 = true },
                    variant = ChunkyButtonVariant.Destructive,
                )
            },
        )
    }

    if (showResetConfirm2) {
        ChunkyDialog(
            title            = stringResource(R.string.reset_confirm2_title),
            onDismissRequest = { showResetConfirm2 = false },
            body             = { Text(stringResource(R.string.reset_confirm2_body)) },
            actions          = {
                ChunkyButton(
                    text    = stringResource(R.string.btn_cancel),
                    onClick = { showResetConfirm2 = false },
                    variant = ChunkyButtonVariant.Secondary,
                )
                ChunkyButton(
                    text    = stringResource(R.string.reset_confirm2_btn),
                    onClick = {
                        showResetConfirm2 = false
                        viewModel.resetProgression()
                        onBack()
                    },
                    variant = ChunkyButtonVariant.Destructive,
                )
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier.defaultMinSize(
                            minWidth  = tokens.touchTargetSize(),
                            minHeight = tokens.touchTargetSize(),
                        ),
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(tokens.spacing.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.m + tokens.spacing.s),
        ) {
            SettingsSectionHeader(
                icon  = Icons.Filled.Palette,
                title = stringResource(R.string.settings_appearance),
            )
            SettingsRow(
                title    = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_desc),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
                        listOf(
                            "dark"   to stringResource(R.string.settings_theme_dark),
                            "light"  to stringResource(R.string.settings_theme_light),
                            "system" to stringResource(R.string.settings_theme_system),
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = themePreference == key,
                                onClick  = { viewModel.setTheme(key) },
                                label    = { Text(label, style = tokens.typography.labelSmall) },
                            )
                        }
                    }
                },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LanguageSection(context)
            }

            SettingsSectionHeader(
                icon  = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_notifications_header),
            )
            SettingsRow(
                title    = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_desc),
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                },
                            )
                        },
                    )
                },
            )

            SettingsSectionHeader(
                icon  = Icons.Filled.Tune,
                title = stringResource(R.string.settings_general_header),
            )
            SettingsRow(
                title    = stringResource(R.string.settings_tutorial_title),
                subtitle = stringResource(R.string.settings_tutorial_desc),
                trailing = {
                    ChunkyButton(
                        text    = stringResource(R.string.settings_reopen),
                        onClick = { onReopenTutorial(); onBack() },
                        variant = ChunkyButtonVariant.Secondary,
                    )
                },
            )

            SettingsSectionHeader(
                icon  = Icons.Filled.Save,
                title = stringResource(R.string.settings_save_data),
            )
            SettingsRow(
                title    = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                trailing = {
                    ChunkyButton(
                        text    = stringResource(R.string.settings_export_btn),
                        onClick = { exportLauncher.launch("fantasyidler_save.json") },
                        variant = ChunkyButtonVariant.Secondary,
                    )
                },
            )
            SettingsRow(
                title    = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                trailing = {
                    ChunkyButton(
                        text    = stringResource(R.string.settings_import_btn),
                        onClick = { importLauncher.launch("*/*") },
                        variant = ChunkyButtonVariant.Secondary,
                    )
                },
            )

            SettingsSectionHeader(
                icon  = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
            )
            SettingsRow(
                title    = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.format_version, BuildConfig.VERSION_NAME),
            )
            SettingsRow(
                title    = stringResource(R.string.settings_source_code),
                subtitle = stringResource(R.string.settings_source_url),
                trailing = {
                    ChunkyButton(
                        text    = stringResource(R.string.settings_source_open),
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tristinbaker/IdleFantasy")),
                            )
                        },
                        variant = ChunkyButtonVariant.Secondary,
                    )
                },
            )
            Text(
                text     = stringResource(R.string.settings_foss_desc),
                style    = tokens.typography.bodyMedium,
                color    = tokens.colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = tokens.spacing.s),
            )

            DangerZone(
                title    = stringResource(R.string.settings_reset_title),
                subtitle = stringResource(R.string.settings_reset_desc),
            ) {
                ChunkyButton(
                    text     = stringResource(R.string.settings_reset_btn),
                    onClick  = { showResetConfirm1 = true },
                    modifier = Modifier.fillMaxWidth(),
                    variant  = ChunkyButtonVariant.Destructive,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageSection(context: Context) {
    val tokens = LocalFantasyTokens.current
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val currentTag = remember {
        val locales = localeManager.applicationLocales
        if (locales.isEmpty) "system" else locales[0]?.language ?: "system"
    }
    val options = listOf(
        "en"     to stringResource(R.string.settings_lang_english),
        "de"     to stringResource(R.string.settings_lang_deutsch),
        "system" to stringResource(R.string.settings_lang_system),
    )
    val selectedLabel = options.find { it.first == currentTag }?.second ?: options.last().second
    var expanded by remember { mutableStateOf(false) }

    SettingsSectionHeader(
        icon  = Icons.Filled.Language,
        title = stringResource(R.string.settings_language),
    )
    SettingsRow(
        title    = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_desc),
        trailing = {
            ExposedDropdownMenuBox(
                expanded         = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value         = selectedLabel,
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    singleLine    = true,
                    modifier      = Modifier
                        .menuAnchor()
                        .width(tokens.spacing.xxl * 4 + tokens.spacing.l),
                    textStyle     = tokens.typography.bodyMedium,
                )
                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { (key, label) ->
                        DropdownMenuItem(
                            text    = { Text(label) },
                            onClick = {
                                localeManager.applicationLocales =
                                    if (key == "system") LocaleList.getEmptyLocaleList()
                                    else LocaleList.forLanguageTags(key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.touchTargetSize()),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (trailing != null) tokens.spacing.l else tokens.spacing.s),
        ) {
            Text(
                text       = title,
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
            )
            Text(
                text  = subtitle,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        if (trailing != null) trailing()
    }
}

// 48dp Material tap-target minimum, expressed through tokens (xxl + l = 32 + 16).
private fun com.fantasyidler.ui.theme.fantasy.FantasyTokens.touchTargetSize(): Dp =
    spacing.xxl + spacing.l

@PreviewLightDark
@Composable
private fun PreviewSettingsRow() {
    FantasyPreviewSurface {
        SettingsRow(
            title    = "Theme",
            subtitle = "Choose your preferred colour scheme",
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSettingsDangerZone() {
    FantasyPreviewSurface {
        DangerZone(
            title    = "Reset Progression",
            subtitle = "Erase all skills, items, quests, and coins",
        ) {
            ChunkyButton(
                text     = "Reset",
                onClick  = {},
                modifier = Modifier.fillMaxWidth(),
                variant  = ChunkyButtonVariant.Destructive,
            )
        }
    }
}
