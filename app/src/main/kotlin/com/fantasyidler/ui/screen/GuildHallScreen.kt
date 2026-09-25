package com.fantasyidler.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.components.SkillRowCard
import com.fantasyidler.ui.viewmodel.GuildHallViewModel
import com.fantasyidler.ui.viewmodel.GuildSummary
import com.fantasyidler.util.GameStrings

private data class GuildGroup(val headerRes: Int, val keys: List<String>)

private fun guildIconSkillKey(guildKey: String): String = when (guildKey) {
    "warriors" -> "attack"
    "archers"  -> "ranged"
    "mages"    -> "magic"
    else       -> guildKey
}

private val GUILD_GROUPS = listOf(
    GuildGroup(
        headerRes = R.string.label_gathering_skills,
        keys = listOf("mining", "fishing", "woodcutting", "farming", "thieving"),
    ),
    GuildGroup(
        headerRes = R.string.label_crafting_skills,
        keys = listOf("smithing", "cooking", "fletching", "crafting", "firemaking", "runecrafting", "herblore", "construction"),
    ),
    GuildGroup(
        headerRes = R.string.label_support_skills,
        keys = listOf("prayer", "mercantile", "agility"),
    ),
    GuildGroup(
        headerRes = R.string.label_combat,
        keys = listOf("warriors", "archers", "mages", "slayer"),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildHallScreen(
    onBack: () -> Unit = {},
    onNavigateToGuild: (String) -> Unit = {},
    viewModel: GuildHallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
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
                if (BuildConfig.DEBUG) {
                    TextButton(onClick  = { viewModel.debugResetGuildDailies() }) {
                        Text("[Debug] Reset dailies")
                    }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuildCard(
    summary: GuildSummary,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val claimable = summary.claimableQuestCount + summary.claimableDailyCount

    SkillRowCard(
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val iconRes = GameStrings.skillIconRes(guildIconSkillKey(summary.guildKey))
                if (iconRes != null) {
                    Image(
                        painter            = painterResource(iconRes),
                        contentDescription = null,
                        modifier           = Modifier.size(28.dp),
                    )
                }
            }
            if (claimable > 0) {
                ClaimableCountBadge(
                    count    = claimable,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        },
        header = {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FlowRow(
                    verticalArrangement    = Arrangement.spacedBy(2.dp),
                    horizontalArrangement  = Arrangement.spacedBy(8.dp),
                    modifier               = Modifier.weight(1f),
                ) {
                    Text(
                        text       = GameStrings.guildName(context, summary.guildKey),
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LevelBadge(level = summary.level)
                }
                DailyStatusIndicator(summary = summary)
            }
        },
        progress = if (summary.level < 10) {
            (summary.dailiesCompletedThisTier.toFloat() / summary.dailiesRequiredThisTier.toFloat()).coerceIn(0f, 1f)
        } else null,
        description = if (summary.level < 10) {
            {
                Text(
                    text  = guildProgressCaption(summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else null,
    )
}

/** Builds the combined dailies-progress / quest-gate caption shown under a guild's rank bar. */
@Composable
private fun guildProgressCaption(summary: GuildSummary): String {
    val dailiesRemain = summary.dailiesCompletedThisTier < summary.dailiesRequiredThisTier
    return when {
        summary.questGateBlocked && dailiesRemain -> stringResource(
            R.string.guild_progress_dailies_and_quest,
            summary.dailiesCompletedThisTier,
            summary.dailiesRequiredThisTier,
        )
        !summary.guildUnlocked || summary.questGateBlocked -> stringResource(R.string.guild_quest_required)
        else -> stringResource(
            R.string.guild_progress_dailies_only,
            summary.dailiesCompletedThisTier,
            summary.dailiesRequiredThisTier,
        )
    }
}

/** Shows whether this guild's dailies are locked, all done, or still have some left today. */
@Composable
private fun DailyStatusIndicator(summary: GuildSummary) {
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    when {
        !summary.guildUnlocked -> Icon(
            imageVector        = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.guild_not_unlocked),
            tint               = dimColor,
            modifier           = Modifier.height(20.dp).width(20.dp),
        )
        summary.dailiesTodayTotal > 0 && summary.dailiesTodayRemaining == 0 -> Icon(
            imageVector        = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.guild_dailies_done_today),
            tint               = MaterialTheme.colorScheme.tertiary,
            modifier           = Modifier.height(20.dp).width(20.dp),
        )
        summary.dailiesTodayTotal > 0 -> Text(
            text      = stringResource(R.string.guild_dailies_remaining_today, summary.dailiesTodayRemaining),
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier  = Modifier.widthIn(max = 84.dp),
        )
    }
}

@Composable
private fun ClaimableCountBadge(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 12.dp, minHeight = 12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onError,
        )
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
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
