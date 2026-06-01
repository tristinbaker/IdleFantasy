package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.GuildHallViewModel
import com.fantasyidler.ui.viewmodel.GuildSummary

private data class GuildGroup(val headerRes: Int, val keys: List<String>)

private val GUILD_GROUPS = listOf(
    GuildGroup(
        headerRes = R.string.label_gathering_skills,
        keys = listOf("mining", "fishing", "woodcutting", "farming", "firemaking", "agility"),
    ),
    GuildGroup(
        headerRes = R.string.label_crafting_skills,
        keys = listOf("smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore"),
    ),
    GuildGroup(
        headerRes = R.string.label_combat,
        keys = listOf("warriors", "archers", "mages"),
    ),
    GuildGroup(
        headerRes = R.string.label_support_skills,
        keys = listOf("prayer", "mercantile"),
    ),
)

@Composable
fun guildDisplayName(guildKey: String): String = when (guildKey) {
    "mining"      -> stringResource(R.string.guild_name_mining)
    "fishing"     -> stringResource(R.string.guild_name_fishing)
    "woodcutting" -> stringResource(R.string.guild_name_woodcutting)
    "farming"     -> stringResource(R.string.guild_name_farming)
    "firemaking"  -> stringResource(R.string.guild_name_firemaking)
    "agility"     -> stringResource(R.string.guild_name_agility)
    "smithing"    -> stringResource(R.string.guild_name_smithing)
    "cooking"     -> stringResource(R.string.guild_name_cooking)
    "fletching"   -> stringResource(R.string.guild_name_fletching)
    "crafting"    -> stringResource(R.string.guild_name_crafting)
    "runecrafting"-> stringResource(R.string.guild_name_runecrafting)
    "herblore"    -> stringResource(R.string.guild_name_herblore)
    "warriors"    -> stringResource(R.string.guild_name_warriors)
    "archers"     -> stringResource(R.string.guild_name_archers)
    "mages"       -> stringResource(R.string.guild_name_mages)
    "prayer"      -> stringResource(R.string.guild_name_prayer)
    "mercantile"  -> stringResource(R.string.guild_name_mercantile)
    else          -> guildKey
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildHallScreen(
    onBack: () -> Unit = {},
    onNavigateToGuild: (String) -> Unit = {},
    viewModel: GuildHallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guild_hall_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val guildMap = state.guilds.associateBy { it.guildKey }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "intro") {
                Text(
                    text     = stringResource(R.string.guild_hall_intro),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            for (group in GUILD_GROUPS) {
                item(key = "header_${group.headerRes}") {
                    Text(
                        text     = stringResource(group.headerRes),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                    )
                }
                items(group.keys, key = { it }) { guildKey ->
                    val summary = guildMap[guildKey]
                    if (summary != null) {
                        GuildCard(
                            summary  = summary,
                            onClick  = { onNavigateToGuild(guildKey) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GuildCard(
    summary: GuildSummary,
    onClick: () -> Unit,
) {
    val claimable = summary.claimableQuestCount + summary.claimableDailyCount

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text       = guildDisplayName(summary.guildKey),
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                LevelBadge(level = summary.level)
            }
            if (summary.questGateBlocked) {
                Text(
                    text  = stringResource(R.string.guild_quest_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (summary.hasDailiesAvailable) {
                Text(
                    text  = stringResource(R.string.guild_dailies_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                )
            }
            if (summary.level < 10) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (summary.repInLevel.toFloat() / summary.repForLevel.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color    = GoldPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(R.string.guild_rep_label, summary.repInLevel, summary.repForLevel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (claimable > 0) {
            Badge { Text("$claimable") }
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    val text = if (level >= 10) stringResource(R.string.guild_level_max)
               else stringResource(R.string.guild_level_label, level)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (level > 0) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall,
            color    = if (level > 0) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
