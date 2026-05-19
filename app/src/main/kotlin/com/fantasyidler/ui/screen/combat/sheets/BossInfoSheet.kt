package com.fantasyidler.ui.screen.combat.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.BossCombatStats
import com.fantasyidler.data.json.BossCommonLoot
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.BossDefensiveStats
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.screen.combat.combatLevel
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Boss confirm sheet — pulled out of the old [com.fantasyidler.ui.screen.CombatScreen].
 * Wraps the body in [ChunkySheet] so the modal honours the gold-bordered
 * sheet shape, drag handle, and theme tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossInfoSheet(
    boss: BossData,
    skillLevels: Map<String, Int>,
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    isStarting: Boolean,
    onPotionSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        BossInfoSheetContent(
            boss              = boss,
            skillLevels       = skillLevels,
            availablePotions  = availablePotions,
            selectedPotionKey = selectedPotionKey,
            isStarting        = isStarting,
            onPotionSelected  = onPotionSelected,
            onStart           = onStart,
            onDismiss         = onDismiss,
        )
    }
}

@Composable
private fun BossInfoSheetContent(
    boss: BossData,
    skillLevels: Map<String, Int>,
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    isStarting: Boolean,
    onPotionSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens   = LocalFantasyTokens.current
    val context  = LocalContext.current
    val combatLv = combatLevel(skillLevels)
    val canFight = combatLv >= boss.combatLevelRequired

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text       = "${boss.emoji} ${boss.displayName}",
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
            )
            Text(
                text  = boss.description,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
            Spacer(Modifier.height(tokens.spacing.l))

            StatRow(
                label      = stringResource(R.string.combat_req_level),
                value      = boss.combatLevelRequired.toString(),
                valueColor = if (canFight) tokens.colors.primary else tokens.colors.error,
            )
            StatRow(
                label = stringResource(R.string.combat_your_level),
                value = combatLv.toString(),
            )
            StatRow(
                label = stringResource(R.string.combat_duration),
                value = stringResource(R.string.combat_duration_min, boss.durationMinutes),
            )

            if (boss.xpRewards.isNotEmpty()) {
                Spacer(Modifier.height(tokens.spacing.m))
                Text(
                    text  = stringResource(R.string.combat_xp_on_victory),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                for ((skill, xp) in boss.xpRewards) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text  = GameStrings.skillName(context, skill),
                            style = tokens.typography.bodyMedium,
                        )
                        Text(
                            text       = "+$xp ${stringResource(R.string.label_xp)}",
                            style      = tokens.typography.bodyMedium,
                            color      = tokens.colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (availablePotions.isNotEmpty()) {
                Spacer(Modifier.height(tokens.spacing.m))
                PotionPickerSection(
                    availablePotions  = availablePotions,
                    selectedPotionKey = selectedPotionKey,
                    onPotionSelected  = onPotionSelected,
                )
            }
        }

        Spacer(Modifier.height(tokens.spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
            ChunkyButton(
                text     = stringResource(R.string.btn_cancel),
                onClick  = onDismiss,
                variant  = ChunkyButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            ChunkyButton(
                text     = stringResource(R.string.btn_fight),
                onClick  = onStart,
                enabled  = canFight && !isStarting,
                modifier = Modifier.weight(1f),
                trailing = if (isStarting) ({
                    Box(modifier = Modifier.size(tokens.spacing.l)) {
                        CircularProgressIndicator(
                            strokeWidth = tokens.shapes.borderStroke,
                            color       = tokens.colors.onPrimary,
                        )
                    }
                }) else null,
            )
        }
    }
}

@Composable
internal fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val tokens = LocalFantasyTokens.current
    val resolvedColor = if (valueColor == androidx.compose.ui.graphics.Color.Unspecified) tokens.colors.onSurface
                        else valueColor
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Text(
            text       = value,
            style      = tokens.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = resolvedColor,
        )
    }
}

private val sampleBoss = BossData(
    id                  = "balrog",
    displayName         = "Balrog the Eternal",
    emoji               = "👹",
    description         = "A demon of fire and shadow, slumbering deep beneath the mountain.",
    combatLevelRequired = 80,
    durationMinutes     = 12,
    hp                  = 800,
    combatStats         = BossCombatStats(attackLevel = 90, strengthLevel = 90, defenseLevel = 80, attackBonus = 30, strengthBonus = 30),
    defensiveStats      = BossDefensiveStats(attackDefense = 30, strengthDefense = 30),
    xpRewards           = mapOf("attack" to 2500, "strength" to 2500, "hitpoints" to 2500),
    commonLoot          = BossCommonLoot(coinsMin = 1000, coinsMax = 4000, items = emptyMap()),
    rareDrops           = emptyList(),
    pet                 = null,
)

@PreviewLightDark
@Composable
private fun PreviewBossInfoSheetUnlocked() {
    FantasyPreviewSurface {
        BossInfoSheetContent(
            boss              = sampleBoss,
            skillLevels       = mapOf("attack" to 80, "strength" to 80, "defense" to 75, "hitpoints" to 80),
            availablePotions  = mapOf("attack_potion" to 2),
            selectedPotionKey = "attack_potion",
            isStarting        = false,
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewBossInfoSheetLocked() {
    FantasyPreviewSurface {
        BossInfoSheetContent(
            boss              = sampleBoss,
            skillLevels       = mapOf("attack" to 30, "strength" to 30, "defense" to 25, "hitpoints" to 30),
            availablePotions  = emptyMap(),
            selectedPotionKey = null,
            isStarting        = false,
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewBossInfoSheetStarting() {
    FantasyPreviewSurface {
        BossInfoSheetContent(
            boss              = sampleBoss,
            skillLevels       = mapOf("attack" to 80, "strength" to 80, "defense" to 75, "hitpoints" to 80),
            availablePotions  = emptyMap(),
            selectedPotionKey = null,
            isStarting        = true,
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}
