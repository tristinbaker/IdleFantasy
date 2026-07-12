package com.fantasyidler.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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

    val themePreference        by viewModel.themePreference.collectAsState()
    val fontScale              by viewModel.fontScale.collectAsState()
    val showRecentActivityLog  by viewModel.showRecentActivityLog.collectAsState()
    val showJournalButton      by viewModel.showJournalButton.collectAsState()
    val showSeasonalEvents     by viewModel.showSeasonalEvents.collectAsState()
    val profileLayout          by viewModel.profileLayout.collectAsState()
    val backupFolderUri  by viewModel.backupFolderUri.collectAsState()
    val backupFrequency  by viewModel.backupFrequency.collectAsState()
    var notificationsEnabled by remember { mutableStateOf(false) }
    var showResetConfirm1    by remember { mutableStateOf(false) }
    var showResetConfirm2    by remember { mutableStateOf(false) }
    var showChangelogDialog  by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val permFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, permFlags)
        viewModel.setBackupFolder(uri.toString())
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_backup_folder_set), withDismissAction = true) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportSave { jsonString ->
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(jsonString.toByteArray()) }
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_exported_ok), withDismissAction = true) }
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
                    message         = if (success) context.getString(R.string.settings_imported_ok) else context.getString(R.string.settings_imported_fail),
                    withDismissAction = true,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    if (showChangelogDialog) {
        val changelogText = remember {
            runCatching { context.assets.open("changelog.txt").bufferedReader().readText().trim() }.getOrElse { "" }
        }
        if (changelogText.isNotEmpty()) {
            val sections = remember(changelogText) {
                val versionRegex = Regex("^v\\d+\\..*")
                val result = mutableListOf<Pair<String, String>>()
                var currentVersion = ""
                val bodyLines = mutableListOf<String>()
                for (line in changelogText.lines()) {
                    if (line.matches(versionRegex)) {
                        if (currentVersion.isNotEmpty()) result += currentVersion to bodyLines.joinToString("\n").trim()
                        currentVersion = line
                        bodyLines.clear()
                    } else {
                        bodyLines += line
                    }
                }
                if (currentVersion.isNotEmpty()) result += currentVersion to bodyLines.joinToString("\n").trim()
                result
            }
            AlertDialog(
                onDismissRequest = { showChangelogDialog = false },
                title = { Text(stringResource(R.string.home_whats_new)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        sections.forEachIndexed { i, (version, body) ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            val isCurrent = version == "v${BuildConfig.VERSION_NAME}"
                            Text(
                                text = if (isCurrent) "$version (current)" else version,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChangelogDialog = false }) {
                        Text(stringResource(R.string.home_got_it))
                    }
                },
            )
        }
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Appearance section
            SectionHeader(title = stringResource(R.string.settings_appearance))

            SettingsRow(
                title    = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_desc),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "dark"   to stringResource(R.string.settings_theme_dark),
                            "light"  to stringResource(R.string.settings_theme_light),
                            "system" to stringResource(R.string.settings_theme_system),
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

            SettingsRow(
                title    = stringResource(R.string.settings_font_size),
                subtitle = null,
                trailing = {
                    val fontOptions = listOf(
                        0.7f  to stringResource(R.string.settings_font_tiny),
                        0.85f to stringResource(R.string.settings_font_small),
                        1.0f  to stringResource(R.string.settings_font_normal),
                        1.25f to stringResource(R.string.settings_font_large),
                        1.5f  to stringResource(R.string.settings_font_huge),
                    )
                    val fontLabel = fontOptions.firstOrNull { it.first == fontScale }?.second
                        ?: stringResource(R.string.settings_font_normal)
                    var fontExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = fontExpanded,
                        onExpandedChange = { fontExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = fontLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(130.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = fontExpanded,
                            onDismissRequest = { fontExpanded = false },
                        ) {
                            fontOptions.forEach { (scale, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setFontScale(scale)
                                        fontExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            )

            SettingsRow(
                title    = stringResource(R.string.settings_recent_activity),
                subtitle = stringResource(R.string.settings_recent_activity_desc),
                trailing = {
                    Switch(
                        checked         = showRecentActivityLog,
                        onCheckedChange = { viewModel.setShowRecentActivityLog(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_journal_button),
                subtitle = stringResource(R.string.settings_journal_button_desc),
                trailing = {
                    Switch(
                        checked         = showJournalButton,
                        onCheckedChange = { viewModel.setShowJournalButton(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_seasonal_events),
                subtitle = stringResource(R.string.settings_seasonal_events_desc),
                trailing = {
                    Switch(
                        checked         = showSeasonalEvents,
                        onCheckedChange = { viewModel.setShowSeasonalEvents(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_profile_layout),
                subtitle = stringResource(R.string.settings_profile_layout_desc),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = profileLayout == "rail",
                            onClick  = { viewModel.setProfileLayout("rail") },
                            label    = { Text(stringResource(R.string.settings_profile_layout_rail)) },
                        )
                        FilterChip(
                            selected = profileLayout == "tabs",
                            onClick  = { viewModel.setProfileLayout("tabs") },
                            label    = { Text(stringResource(R.string.settings_profile_layout_tabs)) },
                        )
                    }
                }
            )

            HorizontalDivider()
            LanguageSection()

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
                title    = stringResource(R.string.settings_changelog_title),
                subtitle = stringResource(R.string.settings_changelog_desc),
                trailing = {
                    OutlinedButton(onClick = { showChangelogDialog = true }) {
                        Text(stringResource(R.string.settings_changelog_btn))
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

            SectionHeader(title = stringResource(R.string.settings_save_data))

            SettingsRow(
                title    = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                trailing = {
                    OutlinedButton(onClick = { exportLauncher.launch("fantasyidler_save.json") }) {
                        Text(stringResource(R.string.settings_export_btn))
                    }
                }
            )

            SettingsRow(
                title    = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                trailing = {
                    OutlinedButton(onClick = { importLauncher.launch("*/*") }) {
                        Text(stringResource(R.string.settings_import_btn))
                    }
                }
            )

            // Automatic Backup section
            HorizontalDivider()

            SectionHeader(title = stringResource(R.string.settings_backup_title))

            val noFolderStr = stringResource(R.string.settings_backup_no_folder)
            val folderLabel = if (backupFolderUri.isEmpty()) {
                noFolderStr
            } else {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(Uri.parse(backupFolderUri))
                    docId.substringAfterLast(':').ifEmpty { noFolderStr }
                } catch (_: Exception) {
                    noFolderStr
                }
            }

            SettingsRow(
                title    = stringResource(R.string.settings_backup_folder),
                subtitle = folderLabel,
                trailing = {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                        Text(stringResource(R.string.settings_backup_choose))
                    }
                }
            )

            val freqSubtitle = if (backupFrequency == "daily" || backupFrequency == "weekly")
                "${stringResource(R.string.settings_backup_at_5am)}" else ""
            SettingsRow(
                title    = stringResource(R.string.settings_backup_frequency),
                subtitle = freqSubtitle,
                trailing = {
                    val freqOptions = listOf(
                        ""       to stringResource(R.string.settings_backup_off),
                        "hourly" to stringResource(R.string.settings_backup_hourly),
                        "daily"  to stringResource(R.string.settings_backup_daily),
                        "weekly" to stringResource(R.string.settings_backup_weekly),
                    )
                    val freqLabel = freqOptions.firstOrNull { it.first == backupFrequency }?.second
                        ?: stringResource(R.string.settings_backup_off)
                    var freqExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = freqLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon =  { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(150.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = freqExpanded,
                            onDismissRequest = { freqExpanded = false },
                        ) {
                            freqOptions.forEach { (freq, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setBackupFrequency(freq)
                                        freqExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            OutlinedButton(
                onClick = {
                    viewModel.backupNow { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message           = if (success) context.getString(R.string.settings_backup_success)
                                                    else context.getString(R.string.settings_backup_failed),
                                withDismissAction = true,
                            )
                        }
                    }
                },
                enabled  = backupFolderUri.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_backup_now))
            }

            // About section
            HorizontalDivider()

            SectionHeader(title = stringResource(R.string.settings_about))

            SettingsRow(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.format_version, BuildConfig.VERSION_NAME)
            )

            SettingsRow(
                title    = stringResource(R.string.settings_source_code),
                subtitle = stringResource(R.string.settings_source_url),
                trailing = {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tristinbaker/IdleFantasy"))
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_source_open))
                    }
                }
            )

            SettingsRow(
                title    = stringResource(R.string.settings_wiki),
                subtitle = stringResource(R.string.settings_wiki_url),
                trailing = {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://idlefantasy.tristinbaker.xyz"))
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_source_open))
                    }
                }
            )

            Text(
                text = stringResource(R.string.settings_foss_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Art credits section
            HorizontalDivider()

            SectionHeader(title = stringResource(R.string.settings_art_credits))

            val artCredits = listOf(
                Triple(
                    stringResource(R.string.settings_credit_banners_title),
                    stringResource(R.string.settings_credit_banners_subtitle),
                    "https://wenrexa.itch.io/banners-kingdoms",
                ),
                Triple(
                    stringResource(R.string.settings_credit_skill_icons_title),
                    stringResource(R.string.settings_credit_skill_icons_subtitle),
                    "https://shikashipx.itch.io/shikashis-fantasy-icons-pack",
                ),
            )
            artCredits.forEach { (title, subtitle, url) ->
                SettingsRow(
                    title    = title,
                    subtitle = subtitle,
                    trailing = {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        ) {
                            Text(stringResource(R.string.settings_source_open))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection() {
    val currentTag = remember {
        val locales = AppCompatDelegate.getApplicationLocales()

        if (locales.isEmpty) {
            "system"
        } else {
            locales[0]?.toLanguageTag() ?: "system"
        }
    }

    val options = listOf(
        "en"     to stringResource(R.string.settings_lang_english),
        "de"     to stringResource(R.string.settings_lang_deutsch),
        "fr"     to stringResource(R.string.settings_lang_français),
        "es"     to stringResource(R.string.settings_lang_español),
        "es-ES"  to stringResource(R.string.settings_lang_español_españa),
        "nl"     to stringResource(R.string.settings_lang_dutch),
        "tr"     to stringResource(R.string.settings_lang_turkish),
        "it"     to stringResource(R.string.settings_lang_italiano),
        "ru"     to stringResource(R.string.settings_lang_russian),
        "id"     to stringResource(R.string.settings_lang_indonesian),
        "pt-BR"  to stringResource(R.string.settings_lang_portugues_brasil),
        "system" to stringResource(R.string.settings_lang_system),
    )
    val selectedLabel =
        options.find { it.first == currentTag }?.second
            ?: options.find { currentTag.startsWith("${it.first}-") }?.second
            ?: options.last().second
    var expanded by remember { mutableStateOf(false) }

    SectionHeader(title = stringResource(R.string.settings_language))
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
                        .width(140.dp),
                    textStyle     = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { (key, label) ->
                        DropdownMenuItem(
                            text    = { Text(label) },
                            onClick = {
                                val localeList = if (key == "system") LocaleListCompat.getEmptyLocaleList()
                                                 else LocaleListCompat.forLanguageTags(key)
                                AppCompatDelegate.setApplicationLocales(localeList)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    )
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
    subtitle: String? = null,
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
