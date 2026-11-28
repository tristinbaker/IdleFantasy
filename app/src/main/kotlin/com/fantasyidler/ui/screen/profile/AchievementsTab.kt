package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.components.CompletionProgressBar
import com.fantasyidler.ui.screen.SlotSectionHeader
import com.fantasyidler.ui.viewmodel.Achievement

@Composable
internal fun AchievementsTab(
    byGroup: Map<String, List<Achievement>>,
    unlockedCount: Int,
    totalCount: Int,
    hidden: List<Achievement> = emptyList(),
) {
    var onlyMissing by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            CompletionProgressBar(
                completed = unlockedCount,
                total = totalCount,
                label = stringResource(R.string.achievements_progress_bar)
            )
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !onlyMissing,
                    onClick = { onlyMissing = false },
                    label = { Text(stringResource(R.string.bestiary_filter_all)) },
                )
                FilterChip(
                    selected = onlyMissing,
                    onClick = { onlyMissing = true },
                    label = { Text(stringResource(R.string.bestiary_filter_missing)) },
                )
            }
        }
        byGroup.forEach { (group, allAchievements) ->
            val achievements =
                if (onlyMissing) allAchievements.filter { !it.isUnlocked } else allAchievements
            if (achievements.isEmpty()) return@forEach
            item(key = "hdr_$group") {
                val groupLabel = when (group) {
                    "Levelling" -> stringResource(R.string.achievement_group_levelling)
                    "Combat" -> stringResource(R.string.achievement_group_combat)
                    "Quests" -> stringResource(R.string.achievement_group_quests)
                    "Collection" -> stringResource(R.string.achievement_group_collection)
                    "Arena" -> stringResource(R.string.achievement_group_arena)
                    else -> group
                }
                SlotSectionHeader(groupLabel)
            }
            itemsIndexed(achievements, key = { _, it -> it.id }) { index, ach ->
                AchievementRow(ach)
                if (index < achievements.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        val hiddenShown = if (onlyMissing) hidden.filter { !it.isUnlocked } else hidden
        if (hiddenShown.isNotEmpty()) {
            item(key = "hdr_hidden") {
                SlotSectionHeader(stringResource(R.string.achievement_group_hidden))
                Text(
                    text = stringResource(
                        R.string.achievement_hidden_count,
                        hidden.count { it.isUnlocked },
                        hidden.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = stringResource(R.string.achievement_hidden_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(hiddenShown, key = { _, it -> "hidden_${it.id}" }) { index, ach ->
                AchievementRow(ach)
                if (index < hiddenShown.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AchievementRow(ach: Achievement) {
    val alpha = if (ach.isUnlocked) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ach.emoji,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = ach.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = ach.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (ach.isUnlocked) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}