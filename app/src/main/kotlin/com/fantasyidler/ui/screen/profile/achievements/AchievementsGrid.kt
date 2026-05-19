package com.fantasyidler.ui.screen.profile.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.screen.profile.ProfileEmptyState
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.Achievement

/**
 * Profile → Achievements tab. Header shows total progress (X / Y) against a
 * gold-tinted strip; per-group sections list each achievement as a row with
 * leading emoji, name + description, and a checkmark for unlocked entries.
 */
@Composable
fun AchievementsGrid(
    byGroup: Map<String, List<Achievement>>,
    unlockedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    if (totalCount == 0) {
        ProfileEmptyState(
            title    = stringResource(R.string.profile_achievements_empty),
            modifier = modifier,
        )
        return
    }
    val tokens = LocalFantasyTokens.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item("hdr_progress") { AchievementsProgressBar(unlockedCount, totalCount) }
        byGroup.forEach { (group, achievements) ->
            item(key = "hdr_$group") { SectionHeader(group) }
            items(achievements, key = { it.id }) { ach -> AchievementRow(ach) }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun AchievementsProgressBar(unlockedCount: Int, totalCount: Int) {
    val tokens = LocalFantasyTokens.current
    Surface(
        color    = tokens.colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.label_achievements),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            Text(
                text       = "$unlockedCount / $totalCount",
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
            )
        }
    }
}

@Composable
private fun AchievementRow(ach: Achievement) {
    val tokens = LocalFantasyTokens.current
    val alpha  = if (ach.isUnlocked) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(tokens.spacing.xxl + tokens.spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = ach.emoji,
                style = tokens.typography.titleLarge,
                color = tokens.colors.onSurface.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = ach.name,
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = tokens.colors.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = ach.description,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted.copy(alpha = alpha),
            )
        }
        if (ach.isUnlocked) {
            Text(
                text       = "✓",
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

@PreviewLightDark
@Composable
private fun PreviewAchievementsGridEmpty() {
    FantasyPreviewSurface {
        AchievementsGrid(byGroup = emptyMap(), unlockedCount = 0, totalCount = 0)
    }
}

@PreviewLightDark
@Composable
private fun PreviewAchievementsGridPopulated() {
    FantasyPreviewSurface {
        AchievementsGrid(
            byGroup = linkedMapOf(
                "Levelling" to listOf(
                    Achievement("total_50",  "Adventurer", "Reach total level 50",  "⚔️", true),
                    Achievement("total_100", "Journeyman", "Reach total level 100", "🗺️", false),
                ),
                "Combat" to listOf(
                    Achievement("combat_10", "Fighter", "Reach combat level 10", "🗡️", true),
                ),
            ),
            unlockedCount = 2,
            totalCount    = 3,
        )
    }
}
