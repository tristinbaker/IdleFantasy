package com.fantasyidler.ui.screen.combat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.BossCombatStats
import com.fantasyidler.data.json.BossCommonLoot
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.BossDefensiveStats
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemySpawn
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.ui.components.DungeonCard
import com.fantasyidler.ui.components.EmptyState
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Top-level scrollable list of dungeons + solo-bosses. Both groups share the
 * surface so the player sees their full PvE roster in one place.
 *
 * Each row is a [ChunkyCard] (boss) or [DungeonCard] (dungeon) — the card
 * primitive enforces the gold border, press-scale, and 48dp tap target.
 */
@Composable
fun CombatSelectionList(
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    skillLevels: Map<String, Int>,
    survivalRatings: Map<String, CombatSimulator.SurvivalRating>,
    onDungeon: (DungeonData) -> Unit,
    onBoss: (BossData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val combatLv = combatLevel(skillLevels)
    val tokens   = LocalFantasyTokens.current

    if (dungeons.isEmpty() && bosses.isEmpty()) {
        EmptyState(
            title       = stringResource(R.string.combat_empty_selection_title),
            description = stringResource(R.string.combat_empty_selection_description),
            modifier    = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (dungeons.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.label_dungeons_tab),
                    showDivider = false,
                )
            }
            items(dungeons, key = { it.name }) { dungeon ->
                DungeonCard(
                    dungeon        = dungeon,
                    unlocked       = combatLv >= dungeon.recommendedLevel - UNLOCK_TOLERANCE,
                    survivalRating = survivalRatings[dungeon.name],
                    onTap          = { onDungeon(dungeon) },
                )
            }
        }
        if (bosses.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.combat_solo_bosses),
                    showDivider = false,
                )
            }
            items(bosses, key = { it.id }) { boss ->
                BossRow(
                    boss     = boss,
                    unlocked = combatLv >= boss.combatLevelRequired,
                    onTap    = { onBoss(boss) },
                )
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun BossRow(
    boss: BossData,
    unlocked: Boolean,
    onTap: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val cardDescription = stringResource(
        if (unlocked) R.string.cd_open_boss else R.string.cd_locked_boss,
        boss.displayName, boss.combatLevelRequired,
    )

    ChunkyCard(
        onClick  = onTap,
        enabled  = unlocked,
        modifier = Modifier
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.s)
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l) // 48dp
            .semantics { contentDescription = cardDescription },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisk(
                emoji      = boss.emoji,
                background = if (unlocked) tokens.colors.primary.copy(alpha = 0.18f)
                             else tokens.colors.surfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(tokens.spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    text       = boss.displayName,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = if (unlocked) tokens.colors.onSurface
                                 else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
                )
                Text(
                    text     = boss.description,
                    style    = tokens.typography.bodyMedium,
                    color    = if (unlocked) tokens.colors.onSurfaceMuted
                               else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(tokens.spacing.m))
            Text(
                text       = "Lv. ${boss.combatLevelRequired}",
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = if (unlocked) tokens.colors.primary
                             else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
            )
        }
    }
}

private val sampleDungeon = DungeonData(
    name              = "dark_cave",
    displayName       = "Dark Cave",
    description       = "A damp cave teeming with goblins and weak rats.",
    recommendedLevel  = 12,
    encounterRate     = 0.6,
    enemySpawns       = listOf(EnemySpawn("goblin", 5)),
)

private val sampleBoss = BossData(
    id                  = "balrog",
    displayName         = "Balrog the Eternal",
    emoji               = "👹",
    description         = "A demon of fire and shadow.",
    combatLevelRequired = 80,
    durationMinutes     = 12,
    hp                  = 800,
    combatStats         = BossCombatStats(90, 90, 80, 30, 30),
    defensiveStats      = BossDefensiveStats(30, 30),
    xpRewards           = mapOf("attack" to 2500),
    commonLoot          = BossCommonLoot(1000, 4000, emptyMap()),
    rareDrops           = emptyList(),
    pet                 = null,
)

@PreviewLightDark
@Composable
private fun PreviewCombatSelectionListPopulated() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            CombatSelectionList(
                dungeons        = listOf(sampleDungeon),
                bosses          = listOf(sampleBoss),
                skillLevels     = mapOf("attack" to 15, "strength" to 15, "defense" to 12, "hitpoints" to 15),
                survivalRatings = emptyMap(),
                onDungeon       = {},
                onBoss          = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatSelectionListEmpty() {
    FantasyPreviewSurface {
        CombatSelectionList(
            dungeons        = emptyList(),
            bosses          = emptyList(),
            skillLevels     = emptyMap(),
            survivalRatings = emptyMap(),
            onDungeon       = {},
            onBoss          = {},
        )
    }
}
