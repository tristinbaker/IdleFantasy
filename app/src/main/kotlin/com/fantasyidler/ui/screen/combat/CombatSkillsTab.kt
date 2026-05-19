package com.fantasyidler.ui.screen.combat

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp

/**
 * Vertical list of seven combat-skill rows. The list lives outside any active
 * session — it's the "stats" tab on the no-session combat screen.
 */
@Composable
fun CombatSkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    totalAttackBonus: Int,
    totalStrengthBonus: Int,
    totalDefenseBonus: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(COMBAT_SKILLS, key = { it }) { key ->
            val gearBonus = when (key) {
                Skills.ATTACK   -> totalAttackBonus
                Skills.STRENGTH -> totalStrengthBonus
                Skills.DEFENSE  -> totalDefenseBonus
                else            -> 0
            }
            CombatSkillRow(
                skillKey  = key,
                level     = skillLevels[key] ?: 1,
                xp        = skillXp[key] ?: 0L,
                gearBonus = gearBonus,
            )
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun CombatSkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    gearBonus: Int = 0,
) {
    val tokens   = LocalFantasyTokens.current
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val emoji    = GameStrings.skillEmoji(skillKey)
    val progress = xpProgressFraction(xp)

    ChunkyCard(
        modifier = Modifier
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.s)
            .semantics {
                contentDescription = "$name level $level"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.l)) {
                IconDisk(
                    emoji      = emoji,
                    size       = tokens.spacing.xxl + tokens.spacing.l, // 48dp
                    background = tokens.colors.primary.copy(alpha = 0.14f),
                )
                Text(
                    text       = level.toString(),
                    style      = tokens.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                    modifier   = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            color = tokens.colors.surface,
                            shape = tokens.shapes.badge,
                        )
                        .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
                )
            }
            Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.xs))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = name,
                            style      = tokens.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = tokens.colors.onSurface,
                        )
                        if (gearBonus > 0) {
                            Spacer(Modifier.width(tokens.spacing.s + tokens.spacing.xs))
                            Text(
                                text  = stringResource(R.string.combat_gear_bonus, gearBonus),
                                style = tokens.typography.labelSmall,
                                color = tokens.colors.primary,
                            )
                        }
                    }
                    Text(
                        text  = "${xp.formatXp()} ${stringResource(R.string.label_xp)}",
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.s + tokens.spacing.xs))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.m)
                        .clip(tokens.shapes.chip),
                    color      = tokens.colors.primary,
                    trackColor = tokens.colors.surfaceVariant,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatSkillsTab() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            CombatSkillsTab(
                skillLevels = mapOf(
                    Skills.ATTACK to 42, Skills.STRENGTH to 35, Skills.DEFENSE to 31,
                    Skills.RANGED to 18, Skills.MAGIC to 12, Skills.HITPOINTS to 36, Skills.PRAYER to 8,
                ),
                skillXp = mapOf(
                    Skills.ATTACK to 100_000L, Skills.STRENGTH to 60_000L, Skills.DEFENSE to 45_000L,
                    Skills.RANGED to 5_000L, Skills.MAGIC to 1_500L, Skills.HITPOINTS to 65_000L, Skills.PRAYER to 500L,
                ),
                totalAttackBonus   = 12,
                totalStrengthBonus = 10,
                totalDefenseBonus  = 8,
            )
        }
    }
}
