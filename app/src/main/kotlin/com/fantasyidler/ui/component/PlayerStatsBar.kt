package com.fantasyidler.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.screen.StatInline
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatDurationMs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerStatsBar(
    context: Context,
    combatLevel: Int,
    totalLevel: Int,
    coins: Long,
    activeBlessingKey: String,
    activeBlessingRemainingMs: Long,
    prayerCapeMult: Float,
    xpBoostRemainingMs: Long,
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
        if (blessingActive) {
            val nameResId = context.resources.getIdentifier(
                "blessing_${activeBlessingKey}_name", "string", context.packageName,
            )
            val blessingName = if (nameResId != 0) stringResource(nameResId) else activeBlessingKey
            val blessingData = ChurchRepository.ALL_BLESSINGS.firstOrNull { it.key == activeBlessingKey }
            val boostDesc = blessingData?.let { b ->
                when (b.type) {
                    BlessingType.XP      -> "%1.2fx XP".format(1f + (b.magnitude - 1f) * prayerCapeMult)
                    BlessingType.DEFENSE -> "+${(b.magnitude * prayerCapeMult).toInt()} DEF"
                    BlessingType.COINS   -> "+${(b.magnitude * prayerCapeMult * 100).toInt()}% coins"
                }
            }
            val timeLeft = activeBlessingRemainingMs.formatDurationMs(context)
            val blessingText = if (boostDesc != null) "$blessingName ($boostDesc) - $timeLeft"
                              else "$blessingName - $timeLeft"
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.Star,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = blessingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (boostActive) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.Star,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.tertiary,
                    modifier           = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = stringResource(R.string.home_xp_boost_active, xpBoostRemainingMs.formatDurationMs(context)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
