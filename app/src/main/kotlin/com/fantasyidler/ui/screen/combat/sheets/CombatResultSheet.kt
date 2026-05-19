package com.fantasyidler.ui.screen.combat.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.screen.combat.COMBAT_SKILLS
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.CombatSessionResult
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

/**
 * Post-fight summary sheet — surfaced the moment a session resolves. Wraps
 * the body in [ChunkySheet] so the modal matches the rest of the combat slice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatResultSheet(
    result: CombatSessionResult,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        CombatResultSheetContent(result = result, onDismiss = onDismiss)
    }
}

@Composable
private fun CombatResultSheetContent(
    result: CombatSessionResult,
    onDismiss: () -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!result.won) {
            Text(
                text       = stringResource(R.string.combat_you_died),
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.error,
            )
            Spacer(Modifier.height(tokens.spacing.xs))
        }
        Text(
            text       = result.dungeonDisplayName,
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        Text(
            text  = if (result.won) stringResource(R.string.label_session_results)
                    else stringResource(R.string.combat_died_reward),
            style = tokens.typography.bodyMedium,
            color = if (result.won) tokens.colors.onSurfaceMuted else tokens.colors.error,
        )
        Spacer(Modifier.height(tokens.spacing.l))

        if (result.xpPerSkill.isNotEmpty()) {
            Text(
                text  = if (result.won) stringResource(R.string.label_xp_gained)
                        else stringResource(R.string.combat_xp_consolation),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            for (skill in COMBAT_SKILLS) {
                val xp = result.xpPerSkill[skill] ?: continue
                if (xp <= 0L) continue
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.skillName(context, skill),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                    Text(
                        text       = "+${xp.formatXp()} ${stringResource(R.string.label_xp)}",
                        style      = tokens.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = tokens.colors.primary,
                    )
                }
            }
            Spacer(Modifier.height(tokens.spacing.m))
        }

        if (result.coinsGained > 0L) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.label_coins),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )
                Text(
                    text       = "+${result.coinsGained.formatCoins()}",
                    style      = tokens.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = tokens.colors.primary,
                )
            }
            Spacer(Modifier.height(tokens.spacing.m))
        }

        if (result.itemsGained.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_items_collected),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            for ((item, qty) in result.itemsGained) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.itemName(context, item),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                    Text(
                        text  = "×$qty",
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
            }
            Spacer(Modifier.height(tokens.spacing.l))
        }

        ChunkyButton(
            text     = stringResource(R.string.btn_close),
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatResultSheetVictory() {
    FantasyPreviewSurface {
        CombatResultSheetContent(
            result = CombatSessionResult(
                dungeonDisplayName = "Dark Cave",
                xpPerSkill         = mapOf(Skills.ATTACK to 2400L, Skills.STRENGTH to 1800L, Skills.HITPOINTS to 1200L),
                itemsGained        = mapOf("bones" to 8, "copper_ore" to 3),
                coinsGained        = 412L,
                won                = true,
            ),
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatResultSheetDeath() {
    FantasyPreviewSurface {
        CombatResultSheetContent(
            result = CombatSessionResult(
                dungeonDisplayName = "Goblin Caves",
                xpPerSkill         = mapOf(Skills.ATTACK to 240L),
                itemsGained        = emptyMap(),
                coinsGained        = 0L,
                won                = false,
            ),
            onDismiss = {},
        )
    }
}
