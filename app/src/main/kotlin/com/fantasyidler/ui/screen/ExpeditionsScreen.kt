package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.fantasyidler.R
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.ExpeditionsUiState
import com.fantasyidler.ui.viewmodel.ExpeditionsViewModel
import com.fantasyidler.ui.viewmodel.SkillingDungeonUiItem
import com.fantasyidler.util.GameStrings

@Composable
fun ExpeditionsScreen(
    viewModel: ExpeditionsViewModel = hiltViewModel(),
    showTitle: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.nav_expeditions),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (state.isLoading) {
            Text(
                text = stringResource(R.string.expedition_loading),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val context = LocalContext.current
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val skillOrder = listOf("mining", "woodcutting", "fishing", "agility", "thieving")
            skillOrder.forEach { skill ->
                val dungeons = state.dungeonsBySkill[skill]
                if (!dungeons.isNullOrEmpty()) {
                    item {
                        Text(
                            text = GameStrings.skillName(context, skill),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(dungeons, key = { it.key }) { item ->
                        SkillingDungeonCard(
                            item = item,
                            anySessionActive = state.anySessionActive,
                            onExplore = { viewModel.startExpedition(item.key) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(snackbarData = data, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun SkillingDungeonCard(
    item: SkillingDungeonUiItem,
    anySessionActive: Boolean,
    onExplore: () -> Unit,
) {
    val context = LocalContext.current
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textColor = if (item.isAccessible) MaterialTheme.colorScheme.onSurface else dimColor
    val subColor = if (item.isAccessible) MaterialTheme.colorScheme.onSurfaceVariant else dimColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isAccessible)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = GameStrings.skillingDungeonName(context, item.key, item.dungeon.displayName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    Text(
                        text = GameStrings.skillingDungeonDesc(context, item.key, item.dungeon.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onExplore,
                    enabled = item.isAccessible,
                ) {
                    Text(stringResource(R.string.expedition_explore_button))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.isAccessible) {
                val progress = item.notesFound.toFloat() / item.dungeon.noteThreshold.toFloat()
                Text(
                    text = stringResource(R.string.expedition_lore_notes, item.notesFound, item.dungeon.noteThreshold),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(6.dp),
                    color = GoldPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (item.notesFound >= item.dungeon.noteThreshold) {
                    Text(
                        text = stringResource(R.string.expedition_dungeon_unlocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                item.lockReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = dimColor,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}
