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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemySpawn
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.screen.combat.ARROW_TIERS
import com.fantasyidler.ui.screen.combat.UNLOCK_TOLERANCE
import com.fantasyidler.ui.screen.combat.combatLevel
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Dungeon confirm sheet — pulled out of the old [com.fantasyidler.ui.screen.CombatScreen].
 * Wraps the body in [ChunkySheet] so the modal honours the gold-bordered
 * sheet shape, drag handle, and theme tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DungeonInfoSheet(
    dungeon: DungeonData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    isStarting: Boolean,
    onSpellSelected: (SpellData) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        DungeonInfoSheetContent(
            dungeon           = dungeon,
            skillLevels       = skillLevels,
            equippedWeapon    = equippedWeapon,
            inventory         = inventory,
            availableSpells   = availableSpells,
            selectedSpell     = selectedSpell,
            availablePotions  = availablePotions,
            selectedPotionKey = selectedPotionKey,
            isStarting        = isStarting,
            onSpellSelected   = onSpellSelected,
            onPotionSelected  = onPotionSelected,
            onStart           = onStart,
            onDismiss         = onDismiss,
        )
    }
}

@Composable
private fun DungeonInfoSheetContent(
    dungeon: DungeonData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    isStarting: Boolean,
    onSpellSelected: (SpellData) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens     = LocalFantasyTokens.current
    val context    = LocalContext.current
    val combatLv   = combatLevel(skillLevels)
    val canEnter   = combatLv >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = combatStyle.replaceFirstChar { it.titlecase() }
    val canStart   = canEnter && !isStarting &&
        (combatStyle != "magic" || selectedSpell != null)
    val bestArrow  = ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text       = dungeon.displayName,
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
            )
            Text(
                text  = dungeon.description,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
            Spacer(Modifier.height(tokens.spacing.l))

            StatRow(
                label      = stringResource(R.string.combat_rec_level),
                value      = dungeon.recommendedLevel.toString(),
                valueColor = if (canEnter) tokens.colors.primary else tokens.colors.error,
            )
            StatRow(
                label = stringResource(R.string.combat_your_level),
                value = combatLv.toString(),
            )
            StatRow(
                label      = stringResource(R.string.label_combat_style),
                value      = styleLabel,
                valueColor = tokens.colors.primary,
            )

            if (combatStyle == "ranged") {
                val arrowText = if (bestArrow != null) {
                    "${GameStrings.itemName(context, bestArrow)} ×${inventory[bestArrow]}"
                } else {
                    stringResource(R.string.combat_no_strength_bonus)
                }
                StatRow(
                    label = stringResource(R.string.combat_best_arrow),
                    value = arrowText,
                )
            }

            Spacer(Modifier.height(tokens.spacing.m))

            if (dungeon.enemySpawns.isNotEmpty()) {
                Text(
                    text  = stringResource(R.string.combat_enemies),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                dungeon.enemySpawns.forEach { spawn ->
                    Text(
                        text  = "• ${GameStrings.itemName(context, spawn.enemy)}",
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.m))
            }

            if (combatStyle == "magic") {
                SpellPickerSection(
                    availableSpells = availableSpells,
                    selectedSpell   = selectedSpell,
                    equippedWeapon  = equippedWeapon,
                    inventory       = inventory,
                    onSpellSelected = onSpellSelected,
                )
                Spacer(Modifier.height(tokens.spacing.m))
            }

            if (availablePotions.isNotEmpty()) {
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
                text     = stringResource(R.string.btn_enter_dungeon),
                onClick  = onStart,
                enabled  = canStart,
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

private val sampleDungeon = DungeonData(
    name              = "dark_cave",
    displayName       = "Dark Cave",
    description       = "A damp cave teeming with goblins and weak rats. Bring food.",
    recommendedLevel  = 12,
    encounterRate     = 0.6,
    enemySpawns       = listOf(EnemySpawn("goblin", 5), EnemySpawn("rat", 3)),
)

@PreviewLightDark
@Composable
private fun PreviewDungeonInfoSheetMeleeUnlocked() {
    FantasyPreviewSurface {
        DungeonInfoSheetContent(
            dungeon           = sampleDungeon,
            skillLevels       = mapOf("attack" to 15, "strength" to 15, "defense" to 12, "hitpoints" to 15),
            equippedWeapon    = null,
            inventory         = emptyMap(),
            availableSpells   = emptyList(),
            selectedSpell     = null,
            availablePotions  = emptyMap(),
            selectedPotionKey = null,
            isStarting        = false,
            onSpellSelected   = {},
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewDungeonInfoSheetMagicNoSpell() {
    val staff = EquipmentData(
        name = "wizard_staff", displayName = "Wizard Staff", slot = "weapon",
        combatStyle = "magic", description = "A simple staff",
    )
    FantasyPreviewSurface {
        DungeonInfoSheetContent(
            dungeon           = sampleDungeon,
            skillLevels       = mapOf("magic" to 20, "defense" to 12, "hitpoints" to 15),
            equippedWeapon    = staff,
            inventory         = mapOf("air_rune" to 50),
            availableSpells   = listOf(
                SpellData(name = "wind_strike", displayName = "Wind Strike", runeType = "air_rune", magicLevelRequired = 1, maxHit = 2, runeCost = 1),
            ),
            selectedSpell     = null,
            availablePotions  = mapOf("magic_potion" to 2),
            selectedPotionKey = null,
            isStarting        = false,
            onSpellSelected   = {},
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewDungeonInfoSheetUnderlevelled() {
    FantasyPreviewSurface {
        DungeonInfoSheetContent(
            dungeon           = sampleDungeon,
            skillLevels       = mapOf("attack" to 3, "strength" to 3, "defense" to 3, "hitpoints" to 5),
            equippedWeapon    = null,
            inventory         = emptyMap(),
            availableSpells   = emptyList(),
            selectedSpell     = null,
            availablePotions  = emptyMap(),
            selectedPotionKey = null,
            isStarting        = false,
            onSpellSelected   = {},
            onPotionSelected  = {},
            onStart           = {},
            onDismiss         = {},
        )
    }
}
