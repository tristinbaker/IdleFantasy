package com.fantasyidler.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.util.GameStrings

@Composable
fun RecipeCard(
    recipe: CraftableRecipe,
    craftState: CraftingUiState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val meetsLvl = craftState.meetsLevel(recipe)
    val canMake = craftState.maxCraftable(recipe)
    val enabled = meetsLvl && canMake > 0
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onTap,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityIcon(
            entityId = recipe.outputKey,
            label = recipe.displayName,
            size = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = recipe.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${craftState.inventory[item] ?: 0}/$qty"
            }
            Text(
                text = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
            )
            val statParts = buildList {
                if (recipe.outputAttackBonus > 0) add("+${recipe.outputAttackBonus} Atk")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} Str")
                if (recipe.outputDefenseBonus > 0) add("+${recipe.outputDefenseBonus} Def")
                if (recipe.outputHealingValue > 0) add("Heals ${recipe.outputHealingValue} HP")
                if (recipe.outputDamage > 0) add("+${recipe.outputDamage} dmg")
            }
            if (statParts.isNotEmpty()) {
                Text(
                    text = statParts.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            if (recipe.effects.isNotEmpty()) {
                Text(
                    text = recipe.effects.entries.joinToString("  ") { (stat, bonus) ->
                        "+$bonus ${stat.replaceFirstChar { it.uppercase() }}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            if (recipe.outputRequirements.isNotEmpty()) {
                recipe.outputRequirements.forEach { (skill, lvl) ->
                    val have = craftState.skillLevels[skill] ?: 1
                    val skillLabel = GameStrings.skillName(context, skill)
                    Text(
                        text = stringResource(R.string.skills_req_with_have, lvl, skillLabel, have, skillLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (have >= lvl) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                !meetsLvl -> Text(
                    text = "Lv. ${recipe.levelRequired}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
                canMake > 0 -> {
                    Text(
                        text = "×$canMake",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${recipe.xpPerItem.toInt()} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(
                    text = "No mats",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
