package com.fantasyidler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemySpawn
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DungeonCard(
    dungeon: DungeonData,
    unlocked: Boolean,
    playerCombatLevel: Int,
    onSchedule: () -> Unit,
    onAttack: () -> Unit,
    modifier: Modifier = Modifier,
    survivalRating: CombatSimulator.SurvivalRating? = null,
) {
    val tokens = LocalFantasyTokens.current
    val dimAlpha = if (unlocked) 1f else 0.55f

    ChunkyCard(
        modifier = modifier.padding(
            horizontal = tokens.spacing.l,
            vertical   = tokens.spacing.s,
        ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            EntityIcon(
                entityId = dungeon.name,
                label    = dungeon.displayName,
                size     = 72.dp,
                modifier = Modifier.alpha(dimAlpha),
            )
            Spacer(Modifier.width(tokens.spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = dungeon.displayName,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = if (unlocked) tokens.colors.onSurface
                                 else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tokens.spacing.xs))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                    verticalArrangement   = Arrangement.spacedBy(tokens.spacing.xs),
                ) {
                    LevelPill(
                        label = stringResource(R.string.dungeon_my_lvl),
                        value = playerCombatLevel,
                        tint  = tokens.colors.success,
                    )
                    LevelPill(
                        label = stringResource(R.string.dungeon_rec_lvl),
                        value = dungeon.recommendedLevel,
                        tint  = tokens.colors.success,
                    )
                }

                if (dungeon.advantageVs.isNotEmpty() || dungeon.disadvantageVs.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                        verticalArrangement   = Arrangement.spacedBy(tokens.spacing.xs),
                    ) {
                        if (dungeon.advantageVs.isNotEmpty()) {
                            CategoryPill(
                                label    = stringResource(R.string.dungeon_advantage),
                                values   = dungeon.advantageVs,
                                bg       = tokens.colors.secondaryContainer,
                                labelFg  = tokens.colors.secondary,
                                valuesFg = tokens.colors.onSurface,
                            )
                        }
                        if (dungeon.disadvantageVs.isNotEmpty()) {
                            CategoryPill(
                                label    = stringResource(R.string.dungeon_disadvantage),
                                values   = dungeon.disadvantageVs,
                                bg       = tokens.colors.secondaryContainer,
                                labelFg  = tokens.colors.secondary,
                                valuesFg = tokens.colors.error,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(tokens.spacing.s))
                Text(
                    text     = dungeon.description,
                    style    = tokens.typography.bodyMedium,
                    color    = if (unlocked) tokens.colors.onSurfaceMuted
                               else tokens.colors.onSurfaceMuted.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unlocked && survivalRating != null) {
                    val (ratingText, ratingColor) = when (survivalRating) {
                        CombatSimulator.SurvivalRating.LIKELY   -> "Looks manageable" to tokens.colors.success
                        CombatSimulator.SurvivalRating.RISKY    -> "Risky with current setup" to tokens.colors.warning
                        CombatSimulator.SurvivalRating.UNLIKELY -> "Likely to die" to tokens.colors.error
                    }
                    Spacer(Modifier.height(tokens.spacing.xs))
                    Text(
                        text  = ratingText,
                        style = tokens.typography.labelSmall,
                        color = ratingColor,
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.m))
            Column(
                verticalArrangement   = Arrangement.spacedBy(tokens.spacing.s),
                horizontalAlignment   = Alignment.CenterHorizontally,
            ) {
                ActionTile(
                    label    = stringResource(R.string.dungeon_schedule),
                    variant  = ChunkyButtonVariant.Secondary,
                    enabled  = unlocked,
                    onClick  = onSchedule,
                    icon     = { tint ->
                        Icon(
                            imageVector        = Icons.Outlined.HourglassEmpty,
                            contentDescription = null,
                            tint               = tint,
                            modifier           = Modifier.size(tokens.spacing.xl - tokens.spacing.xs),
                        )
                    },
                )
                ActionTile(
                    label   = stringResource(R.string.dungeon_attack),
                    variant = ChunkyButtonVariant.Primary,
                    enabled = unlocked,
                    onClick = onAttack,
                    icon    = { tint ->
                        Text(
                            text       = "⚔",
                            style      = tokens.typography.titleLarge,
                            color      = tint,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LevelPill(label: String, value: Int, tint: Color) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.chip,
        color = tint.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.m,
                vertical   = tokens.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = label,
                style      = tokens.typography.labelSmall,
                color      = tint,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text       = value.toString(),
                style      = tokens.typography.labelSmall,
                color      = tint,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    values: List<String>,
    bg: Color,
    labelFg: Color,
    valuesFg: Color,
) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.chip,
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.m,
                vertical   = tokens.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "$label:",
                style      = tokens.typography.labelSmall,
                color      = labelFg,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text       = values.joinToString(", "),
                style      = tokens.typography.labelSmall,
                color      = valuesFg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    variant: ChunkyButtonVariant,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val container: Color
    val onContainer: Color
    val borderColor: Color
    when (variant) {
        ChunkyButtonVariant.Primary -> {
            container   = tokens.colors.primary
            onContainer = tokens.colors.onPrimary
            borderColor = tokens.colors.primary
        }
        ChunkyButtonVariant.Secondary -> {
            container   = Color.Transparent
            onContainer = tokens.colors.primary
            borderColor = tokens.colors.primary
        }
        else -> {
            container   = Color.Transparent
            onContainer = tokens.colors.onSurface
            borderColor = Color.Transparent
        }
    }
    val tileDescription = "$label dungeon"
    Surface(
        shape  = tokens.shapes.button,
        color  = container,
        border = if (borderColor == Color.Transparent) null
                 else BorderStroke(tokens.shapes.hairlineStroke, borderColor),
        modifier = modifier
            .size(tokens.spacing.xxl + tokens.spacing.xxl)  // 64dp
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { contentDescription = tileDescription },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                icon(onContainer)
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(
                    text       = label,
                    style      = tokens.typography.labelSmall,
                    color      = onContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                )
            }
        }
    }
}

private val sampleDungeon = DungeonData(
    name              = "goblin_cave",
    displayName       = "Goblin Cave",
    description       = "A dark cave infested with goblins and giant rats. A good place for new adventurers to train.",
    recommendedLevel  = 3,
    encounterRate     = 0.22,
    enemySpawns       = listOf(EnemySpawn("goblin", 7), EnemySpawn("giant_rat", 3)),
    advantageVs       = listOf("Goblinoids", "Vermin"),
    disadvantageVs    = emptyList(),
)

@PreviewLightDark
@Composable
private fun PreviewDungeonCardUnlocked() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            DungeonCard(
                dungeon           = sampleDungeon,
                unlocked          = true,
                playerCombatLevel = 18,
                onSchedule        = {},
                onAttack          = {},
                survivalRating    = CombatSimulator.SurvivalRating.LIKELY,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewDungeonCardLocked() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            DungeonCard(
                dungeon = sampleDungeon.copy(
                    name              = "infernal_stronghold",
                    displayName       = "Infernal Stronghold",
                    recommendedLevel  = 50,
                    advantageVs       = emptyList(),
                    disadvantageVs    = listOf("Demons", "Dragons"),
                ),
                unlocked          = false,
                playerCombatLevel = 18,
                onSchedule        = {},
                onAttack          = {},
            )
        }
    }
}
