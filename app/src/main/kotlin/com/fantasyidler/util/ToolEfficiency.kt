package com.fantasyidler.util

import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository

private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

private fun tierIndex(level: Int): Int = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

/**
 * Returns the efficiency multiplier for the equipped tool in [slot]. Falls back to 1.0 if no
 * tool is equipped or the item has no efficiency for that slot.
 *
 * If [resourceLevelRequired] > 0, applies a per-tier bonus of +0.25x for each tier the tool is
 * above the resource/activity being worked: base × (1.0 + 0.25 × tierDiff).
 */
fun GameDataRepository.toolEfficiency(itemKey: String?, slot: String, resourceLevelRequired: Int = 0): Float {
    if (itemKey == null) return 1.0f
    val eq = equipment[itemKey] ?: return 1.0f
    val base = when (slot) {
        EquipSlot.PICKAXE        -> eq.miningEfficiency      ?: 1.0f
        EquipSlot.AXE            -> eq.woodcuttingEfficiency ?: 1.0f
        EquipSlot.FISHING_ROD    -> eq.fishingEfficiency     ?: 1.0f
        EquipSlot.HOE            -> 1f + (eq.farmingEfficiency ?: 0f)
        EquipSlot.HAMMER         -> eq.smithingEfficiency    ?: 1.0f
        EquipSlot.TINDERBOX      -> eq.firemakingEfficiency  ?: 1.0f
        EquipSlot.GRAPPLING_HOOK -> eq.agilityEfficiency     ?: 1.0f
        EquipSlot.FRYING_PAN     -> eq.cookingEfficiency     ?: 1.0f
        else                     -> 1.0f
    }
    if (resourceLevelRequired <= 0) return base
    val skillKey = when (slot) {
        EquipSlot.PICKAXE        -> Skills.MINING
        EquipSlot.AXE            -> Skills.WOODCUTTING
        EquipSlot.FISHING_ROD    -> Skills.FISHING
        EquipSlot.HAMMER         -> Skills.SMITHING
        EquipSlot.TINDERBOX      -> Skills.FIREMAKING
        EquipSlot.GRAPPLING_HOOK -> Skills.AGILITY
        EquipSlot.FRYING_PAN     -> Skills.COOKING
        else                     -> return base
    }
    val toolReqLevel = eq.requirements[skillKey] ?: 1
    val tierDiff = tierIndex(toolReqLevel) - tierIndex(resourceLevelRequired)
    return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
}
