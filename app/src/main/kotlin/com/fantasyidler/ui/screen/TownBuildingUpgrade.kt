package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.model.TownBuildings
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins

@Composable
fun BuildingUpgradeCard(
    buildingKey: String,
    currentTier: Int,
    constructionLevel: Int,
    coins: Long,
    inventory: Map<String, Int>,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val def = TownBuildings.byKey(buildingKey) ?: return
    val context = LocalContext.current
    val isMaxed = currentTier >= def.tiers.size
    val nextTier = if (!isMaxed) def.tiers[currentTier] else null

    val canUpgrade = nextTier != null &&
        constructionLevel >= nextTier.constructionLevelRequired &&
        coins >= nextTier.coinCost &&
        nextTier.materials.all { (k, qty) -> (inventory[k] ?: 0) >= qty }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val titleRes = when (buildingKey) {
                "inn"          -> R.string.town_building_inn_name
                "guild_hall"   -> R.string.town_building_guild_hall_name
                "church"       -> R.string.town_building_church_name
                "fairgrounds"  -> R.string.town_building_fairgrounds_name
                else           -> R.string.town_upgrade_section_title
            }
            Text(
                text       = stringResource(titleRes),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            // Current tier
            val tierText = if (isMaxed)
                stringResource(R.string.town_building_maxed_label)
            else
                stringResource(R.string.town_building_current_tier, currentTier)
            Text(
                text       = tierText,
                style      = MaterialTheme.typography.bodyMedium,
                color      = if (isMaxed) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isMaxed) FontWeight.SemiBold else FontWeight.Normal,
            )

            // Current bonus
            Text(
                text  = buildingBonusText(buildingKey, currentTier),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (nextTier != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = stringResource(R.string.town_upgrade_next_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))

                Text(
                    text  = stringResource(R.string.town_upgrade_constr_req, nextTier.constructionLevelRequired),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (constructionLevel >= nextTier.constructionLevelRequired)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error,
                )
                Text(
                    text  = stringResource(R.string.town_upgrade_cost, nextTier.coinCost.formatCoins()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (coins >= nextTier.coinCost)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error,
                )
                Text(
                    text     = stringResource(R.string.town_upgrade_materials_header),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                nextTier.materials.forEach { (key, qty) ->
                    val have = inventory[key] ?: 0
                    Text(
                        text  = "  ${GameStrings.itemName(context, key)}: $have / $qty",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (have >= qty)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text     = buildingBonusText(buildingKey, currentTier + 1),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = onUpgrade,
                    enabled  = canUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.town_upgrade_btn))
                }
            }
        }
    }
}

@Composable
fun buildingBonusText(buildingKey: String, tier: Int): String = when (buildingKey) {
    "inn" -> if (tier <= 0) stringResource(R.string.town_inn_no_bonus)
             else stringResource(R.string.town_inn_active_bonus, tier * 10)
    "guild_hall" -> if (tier <= 0) stringResource(R.string.town_guild_no_bonus)
                    else stringResource(R.string.town_guild_active_bonus, tier * 10)
    "church" -> if (tier <= 0) stringResource(R.string.town_church_no_bonus)
                else {
                    val hours = when (tier) { 1 -> 30; 2 -> 36; else -> 48 }
                    stringResource(R.string.town_church_active_bonus, hours)
                }
    "fairgrounds" -> when (tier) {
        0    -> stringResource(R.string.town_fairgrounds_no_bonus)
        1    -> stringResource(R.string.town_fairgrounds_t1_bonus)
        2    -> stringResource(R.string.town_fairgrounds_t2_bonus)
        else -> stringResource(R.string.town_fairgrounds_t3_bonus)
    }
    else -> ""
}
