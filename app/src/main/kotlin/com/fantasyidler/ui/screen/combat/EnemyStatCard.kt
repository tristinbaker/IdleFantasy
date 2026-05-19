package com.fantasyidler.ui.screen.combat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.EnemyCombatStats
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EnemyDefensiveStats
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Compact enemy-stats block used inside [CombatSessionBanner] — name + HP bar
 * + ATK/STR/DEF triplet. Falls back to a generic "Fighting…" label when the
 * banner has no current enemy frame to point at.
 */
@Composable
fun EnemyStatCard(
    enemy: EnemyData?,
    currentHp: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current

    if (enemy == null) {
        Text(
            text     = stringResource(R.string.combat_fighting),
            style    = tokens.typography.titleLarge,
            color    = tokens.colors.onSurface,
            modifier = modifier,
        )
        return
    }

    val atkLabel = stringResource(R.string.combat_atk)
    val strLabel = stringResource(R.string.combat_str)
    val defLabel = stringResource(R.string.combat_def)
    val hpLabel  = stringResource(R.string.label_hp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${enemy.displayName} $hpLabel $currentHp of ${enemy.hp}"
            },
    ) {
        Text(
            text       = enemy.displayName,
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        Spacer(Modifier.height(tokens.spacing.s))
        LinearProgressIndicator(
            progress  = { if (enemy.hp > 0) currentHp / enemy.hp.toFloat() else 0f },
            modifier  = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.m - tokens.spacing.xs)
                .clip(tokens.shapes.chip),
            color     = tokens.colors.error,
            trackColor = tokens.colors.surfaceVariant,
        )
        Spacer(Modifier.height(tokens.spacing.s))
        Text(
            text  = "$hpLabel $currentHp/${enemy.hp}  $atkLabel ${enemy.combatStats.attackLevel}" +
                    "  $strLabel ${enemy.combatStats.strengthLevel}" +
                    "  $defLabel ${enemy.combatStats.defenseLevel}",
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
    }
}

private val sampleEnemy = EnemyData(
    name           = "goblin",
    displayName    = "Goblin",
    hp             = 35,
    combatStats    = EnemyCombatStats(attackLevel = 8, strengthLevel = 9, defenseLevel = 4, attackBonus = 1, strengthBonus = 1),
    defensiveStats = EnemyDefensiveStats(0, 0, 0, 0),
    xpDrops        = mapOf("attack" to 30),
)

@PreviewLightDark
@Composable
private fun PreviewEnemyStatCardFull() {
    FantasyPreviewSurface {
        EnemyStatCard(enemy = sampleEnemy, currentHp = 35)
    }
}

@PreviewLightDark
@Composable
private fun PreviewEnemyStatCardWounded() {
    FantasyPreviewSurface {
        EnemyStatCard(enemy = sampleEnemy, currentHp = 12)
    }
}

@PreviewLightDark
@Composable
private fun PreviewEnemyStatCardEmpty() {
    FantasyPreviewSurface {
        EnemyStatCard(enemy = null, currentHp = 0)
    }
}
