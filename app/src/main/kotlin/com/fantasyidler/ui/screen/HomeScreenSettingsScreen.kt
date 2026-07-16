package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.SettingsViewModel

/** Home screen display toggles, split out of the main Settings screen (was too busy on one page). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val showRecentActivityLog by viewModel.showRecentActivityLog.collectAsState()
    val showJournalButton     by viewModel.showJournalButton.collectAsState()
    val showSeasonalEvents    by viewModel.showSeasonalEvents.collectAsState()
    val collapsibleTownGrid   by viewModel.collapsibleTownGrid.collectAsState()
    val showCharacterViewer   by viewModel.showCharacterViewer.collectAsState()
    val showStatsBar          by viewModel.showStatsBar.collectAsState()
    val showSessionEndTime    by viewModel.showSessionEndTime.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_home_screen)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                title    = stringResource(R.string.settings_collapsible_town_grid),
                subtitle = stringResource(R.string.settings_collapsible_town_grid_desc),
                trailing = {
                    Switch(
                        checked         = collapsibleTownGrid,
                        onCheckedChange = { viewModel.setCollapsibleTownGrid(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_character_viewer),
                subtitle = stringResource(R.string.settings_character_viewer_desc),
                trailing = {
                    Switch(
                        checked         = showCharacterViewer,
                        onCheckedChange = { viewModel.setShowCharacterViewer(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_stats_bar),
                subtitle = stringResource(R.string.settings_stats_bar_desc),
                trailing = {
                    Switch(
                        checked         = showStatsBar,
                        onCheckedChange = { viewModel.setShowStatsBar(it) },
                    )
                }
            )
            SettingsRow(
                title    = stringResource(R.string.settings_session_end_time),
                subtitle = stringResource(R.string.settings_session_end_time_desc),
                trailing = {
                    Switch(
                        checked         = showSessionEndTime,
                        onCheckedChange = { viewModel.setShowSessionEndTime(it) },
                    )
                }
            )
        }
    }
}
