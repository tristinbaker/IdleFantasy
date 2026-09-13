package com.fantasyidler.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.screen.StatInline
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatDurationMs
import kotlin.math.roundToInt

private const val MAX_VISIBLE_BOOST_LINES = 3

private data class BoostLine(val icon: ImageVector, val tint: Color, val text: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerStatsBar(
    context: Context,
    combatLevel: Int,
    totalLevel: Int,
    coins: Long,
    activeBlessingKey: String,
    allBlessings: List<BlessingData>,
    prayerCapeMult: Float,
    activeBlessingRemainingMs: Long,
    xpBoostRemainingMs: Long,
    prestigeBoostsRemainingMs: Map<String, Long> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val blessingActive = activeBlessingKey.isNotEmpty() && activeBlessingRemainingMs > 0
    val boostActive = xpBoostRemainingMs > 0

    Column(modifier) {
        FlowRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement   = Arrangement.spacedBy(4.dp),
        ) {
            StatInline(
                label = stringResource(R.string.label_combat_level),
                value = combatLevel.toString(),
            )
            StatInline(
                label = stringResource(R.string.label_total_level),
                value = totalLevel.toString(),
            )
            StatInline(
                label      = stringResource(R.string.label_coins),
                value      = coins.formatCoins(),
                valueColor = MaterialTheme.colorScheme.primary,
            )
        }
        val primaryTint = MaterialTheme.colorScheme.primary
        val secondaryTint = MaterialTheme.colorScheme.secondary
        val tertiaryTint = MaterialTheme.colorScheme.tertiary
        val boostLines = buildList {
            if (blessingActive) {
                val nameResId = context.resources.getIdentifier(
                    "blessing_${activeBlessingKey}_name", "string", context.packageName,
                )
                val blessingName = if (nameResId != 0) stringResource(nameResId) else activeBlessingKey
                val blessingData = allBlessings.firstOrNull { it.key == activeBlessingKey }
                val boostDesc = blessingData?.let { b ->
                    val eff = ChurchRepository.effectiveMagnitude(b, prayerCapeMult)
                    when (b.type) {
                        BlessingType.XP      -> "${(eff * 100).roundToInt() / 100f}× All skills XP"
                        BlessingType.DEFENSE -> "+${eff.toInt()} DEF"
                        BlessingType.COINS   -> "+${(eff * 100).roundToInt()}% coins"
                    }
                }
                val timeLeft = activeBlessingRemainingMs.formatDurationMs(context)
                val blessingText = if (boostDesc != null) "$boostDesc - $blessingName - $timeLeft"
                                  else "$blessingName - $timeLeft"
                add(
                    BoostLine(
                        icon = Icons.Filled.Star,
                        tint = primaryTint,
                        text = blessingText
                    )
                )
            }
            if (boostActive) {
                add(
                    BoostLine(
                        icon = Icons.Filled.Bolt,
                        tint = secondaryTint,
                        text = stringResource(R.string.home_xp_boost_active, xpBoostRemainingMs.formatDurationMs(context)),
                    )
                )
            }
            prestigeBoostsRemainingMs.entries.sortedBy { it.key }.forEach { (skill, remainingMs) ->
                add(
                    BoostLine(
                        icon = Icons.Filled.WorkspacePremium,
                        tint = tertiaryTint,
                        text = stringResource(
                            R.string.home_prestige_xp_boost_active,
                            GameStrings.skillName(context, skill),
                            remainingMs.formatDurationMs(context),
                        ),
                    )
                )
            }
        }

        if (boostLines.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Whole lines plus an explicit "+N more" toggle instead of an inner scroll: a
            // fixed-height scroll area cut lines mid-height and its drag spilled into the
            // page scroll on the Home screen (issue #1579).
            var expanded by rememberSaveable { mutableStateOf(false) }
            // Collapse only when it hides at least two lines: the "+N more" toggle occupies
            // a line itself, so collapsing a single overflow line saved no space (issue #1768).
            val collapsible = boostLines.size > MAX_VISIBLE_BOOST_LINES + 1
            val visibleLines = if (collapsible && !expanded) boostLines.take(MAX_VISIBLE_BOOST_LINES) else boostLines
            Column(
                modifier = if (collapsible) Modifier.fillMaxWidth().clickable { expanded = !expanded } else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visibleLines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = line.icon,
                            contentDescription = null,
                            tint               = line.tint,
                            modifier           = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = line.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = line.tint,
                        )
                    }
                }
                if (collapsible) {
                    Text(
                        text  = if (expanded) stringResource(R.string.boosts_show_less)
                                else stringResource(R.string.boosts_show_more, boostLines.size - MAX_VISIBLE_BOOST_LINES),
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiaryTint,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
