package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.simulator.PrestigeBoosts
import com.fantasyidler.repository.resolveCapeMultiplier
import com.fantasyidler.repository.isGuildCapeForSkill
import com.fantasyidler.repository.resolveOwnedCapeKeysForSkill
import com.fantasyidler.ui.screen.SlotSectionHeader
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.toTitleCase
import kotlin.math.roundToInt

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

private val COMBAT_STAT_SKILLS = setOf(
    Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE, Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
)

private data class SkillBonusEntry(
    val skillKey: String,
    val skillName: String,
    val xpPct: Int,
    val yieldPct: Int,
    val statBonus: Int,
    val xpSources: List<Pair<String, Int>>,
    val yieldSource: String?,
)

@Composable
internal fun BonusesTab(
    state: InventoryViewModel.UiState,
    allEquipment: Map<String, EquipmentData>,
    allPets: Map<String, PetData>,
) {
    val context = LocalContext.current
    val now     = System.currentTimeMillis()

    val prestigeBoosts = state.prestigeXpBoosts.filterValues { it > now }

    // Ironman: purchased multipliers are inert, but earned prestige effects apply,
    // so show the notice only when no prestige nodes or post-prestige boosts are active either.
    if (state.ironman && state.prestigeEffects.isEmpty() && prestigeBoosts.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text      = stringResource(R.string.ironman_bonuses_notice),
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val boostActive    = state.xpBoostExpiresAt > now
    val blessingActive = state.activeBlessingXpPct > 0 && state.activeBlessingExpiresAt > now
    val capeKey        = state.equipped[EquipSlot.CAPE]
    val cape           = capeKey?.let { allEquipment[it] }?.takeIf { it.capeBonus > 0f }
    val bonusPets      = allPets.values.filter { it.id in state.ownedPetIds && it.boostPercent > 0 }
    val prestigeEffects = state.prestigeEffects

    val isGatheringCape = cape != null && (cape.capeSkill ?: "") !in COMBAT_CAPE_SKILLS
    val isCombatCape    = cape != null && !isGatheringCape

    val allPetBoostPct     = bonusPets.filter { it.boostedSkill == "all" }.sumOf { it.boostPercent }
    val specificBonusPets  = bonusPets.filter { it.boostedSkill != "all" && it.boostedSkill.isNotEmpty() }

    val activeCapeSkills = Skills.ALL.filter { skillKey ->
        val mult = resolveCapeMultiplier(
            skillName = skillKey,
            equippedCape = cape,
            inventoryKeys = state.inventory.keys,
            townBuildingTiers = state.townBuildingTiers,
            capeScaling = state.capeScalingBySkill,
            allEquipment = allEquipment,
            ironman = state.ironman,
        )
        mult > 1.0f
    }

    val specificSkillKeys = buildSet {
        addAll(activeCapeSkills)
        specificBonusPets.forEach { add(it.boostedSkill) }
        addAll(prestigeEffects.keys)
    }

    val skillEntries: List<SkillBonusEntry> = specificSkillKeys.sorted().map { skillKey ->
        val capeMult = resolveCapeMultiplier(
            skillName = skillKey,
            equippedCape = cape,
            inventoryKeys = state.inventory.keys,
            townBuildingTiers = state.townBuildingTiers,
            capeScaling = state.capeScalingBySkill,
            allEquipment = allEquipment,
            ironman = state.ironman,
        )
        val isCombatStat = skillKey in COMBAT_STAT_SKILLS
        // Agility's cape boosts XP, not yield (agility produces no items)
        val isXpCape  = isCombatStat || skillKey == "slayer" || skillKey == "agility"
        val yieldPct  = if (!isXpCape) ((capeMult - 1f) * 100 + 0.5f).toInt() else 0
        val capeXpPct = if (isXpCape) ((capeMult - 1f) * 100 + 0.5f).toInt() else 0

        val specificPetPct = specificBonusPets.filter { it.boostedSkill == skillKey }.sumOf { it.boostPercent }
        val totalPetPct    = specificPetPct + allPetBoostPct
        val effects        = prestigeEffects[skillKey].orEmpty()
        val prestigePct    = (effects[PrestigeBoosts.XP_PCT] ?: 0.0).toInt()
        val perLevelRate   = effects[PrestigeBoosts.PER_LEVEL_BONUS] ?: 0.0
        val level          = state.skillLevels[skillKey] ?: 1
        val statBonus      = (effects[PrestigeBoosts.COMBAT_STAT_FLAT] ?: 0.0).toInt() +
            (perLevelRate * level).roundToInt()
        val prestigeYieldPct = (effects[PrestigeBoosts.YIELD_PCT] ?: 0.0).toInt()

        val activeCapeName = run {
            if (capeMult <= 1.0f) return@run null
            val activeNames = mutableListOf<String>()
            val rackTier = state.townBuildingTiers["cape_rack"] ?: 0
            val isCategoryUnlocked = when {
                skillKey in Skills.GATHERING -> rackTier >= 1
                skillKey in Skills.CRAFTING_SKILLS -> rackTier >= 2
                else -> rackTier >= 3
            }
            val candidateKeys = resolveOwnedCapeKeysForSkill(skillKey)
            val availableKeys = mutableSetOf<String>()
            if (isCategoryUnlocked) {
                candidateKeys.filterTo(availableKeys) { state.inventory.containsKey(it) }
            }
            if (cape?.capeSkill != null) {
                val capeSkill = cape.capeSkill!!
                if (capeSkill == skillKey || isGuildCapeForSkill(capeSkill, skillKey)) {
                    availableKeys.add(cape.name)
                }
            }
            val skillCapeKey = availableKeys.filter { !it.endsWith("_guild_cape") && allEquipment[it]?.capeSkill !in setOf("warriors", "archers", "mages") }
                .maxByOrNull { allEquipment[it]?.capeBonus ?: 0f }
            val guildCapeKey = availableKeys.filter { it.endsWith("_guild_cape") || allEquipment[it]?.capeSkill in setOf("warriors", "archers", "mages") }
                .maxByOrNull { allEquipment[it]?.capeBonus ?: 0f }

            skillCapeKey?.let { activeNames.add(GameStrings.itemName(context, it)) }
            guildCapeKey?.let { activeNames.add(GameStrings.itemName(context, it)) }
            if (activeNames.isNotEmpty()) activeNames.distinct().joinToString(" + ") else null
        }

        val xpSources = buildList {
            if (totalPetPct > 0)  add(context.getString(R.string.label_pets) to totalPetPct)
            if (prestigePct > 0)  add(context.getString(R.string.prestige) to prestigePct)
            if (capeXpPct > 0)    add((activeCapeName ?: GameStrings.slotName(context, EquipSlot.CAPE)) to capeXpPct)
        }
        SkillBonusEntry(
            skillKey    = skillKey,
            skillName   = GameStrings.skillName(context, skillKey),
            xpPct       = totalPetPct + prestigePct + capeXpPct,
            yieldPct    = yieldPct + prestigeYieldPct,
            statBonus   = statBonus,
            xpSources   = xpSources,
            yieldSource = listOfNotNull(
                activeCapeName.takeIf { yieldPct > 0 },
                context.getString(R.string.prestige).takeIf { prestigeYieldPct > 0 },
            ).joinToString(" + ").ifEmpty { null },
        )
    }

    val agilityFloorMin  = prestigeEffects[Skills.AGILITY].orEmpty()[PrestigeBoosts.SESSION_FLOOR_MIN] ?: 0.0
    val coinPctBySkill   = prestigeEffects.mapNotNull { (skill, eff) ->
        (eff[PrestigeBoosts.COIN_PCT] ?: 0.0).toInt().takeIf { it > 0 }?.let { skill to it }
    }
    // Effects with dedicated sections above; everything else gets a generic row below.
    val coveredEffects = setOf(
        PrestigeBoosts.XP_PCT, PrestigeBoosts.YIELD_PCT, PrestigeBoosts.COMBAT_STAT_FLAT,
        PrestigeBoosts.PER_LEVEL_BONUS, PrestigeBoosts.SESSION_FLOOR_MIN, PrestigeBoosts.COIN_PCT,
        PrestigeBoosts.CAPE_SCALING,
    )
    val otherPrestigeRows = prestigeEffects.flatMap { (skill, eff) ->
        eff.filterKeys { it !in coveredEffects }.filterValues { it > 0.0 }
            .map { (effect, value) -> Triple(skill, effect, value) }
    }.sortedBy { it.first }
    val builderDiscountPct = (TownRepository.builderDiscount(state.skillLevels[Skills.CONSTRUCTION] ?: 1) * 100).toInt()

    // "all" pets with no skill-specific rows: surface them in the Boosts section
    val showAllPetsInBoosts = allPetBoostPct > 0 && specificSkillKeys.isEmpty()

    if (!boostActive && !blessingActive && cape == null && bonusPets.isEmpty() && prestigeEffects.isEmpty() && prestigeBoosts.isEmpty() && builderDiscountPct <= 0) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.bonus_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val xpBoostFactor   = if (boostActive) 2.0 else 1.0
    val blessingFactor  = if (blessingActive) 1.0 + state.activeBlessingXpPct / 100.0 else 1.0
    val combinedXpMult  = xpBoostFactor * blessingFactor
    val showCombined    = boostActive && blessingActive

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (boostActive || blessingActive || showAllPetsInBoosts || prestigeBoosts.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.bonus_section_boosts)) }
            if (boostActive) {
                item {
                    val remaining = (state.xpBoostExpiresAt - now).formatDurationMs(context)
                    BonusRow(
                        name   = stringResource(R.string.label_xp_boost),
                        pct    = "+100%",
                        scope  = stringResource(R.string.bonus_all_skills),
                        detail = stringResource(R.string.church_expires_in, remaining),
                    )
                }
            }
            items(prestigeBoosts.entries.sortedBy { it.key }, key = { "pboost_${it.key}" }) { (skill, expiresAt) ->
                val remaining = (expiresAt - now).formatDurationMs(context)
                BonusRow(
                    name   = stringResource(R.string.label_prestige_xp_boost),
                    pct    = "+100%",
                    scope  = GameStrings.skillName(context, skill),
                    detail = stringResource(R.string.church_expires_in, remaining),
                )
            }
            if (blessingActive) {
                item {
                    val blessingName = context.stringByName("blessing_${state.activeBlessingKey}_name")
                        ?: state.activeBlessingKey.toTitleCase()
                    val remaining = (state.activeBlessingExpiresAt - now).formatDurationMs(context)
                    BonusRow(
                        name   = blessingName,
                        pct    = "+${state.activeBlessingXpPct}%",
                        scope  = stringResource(R.string.bonus_all_skills),
                        detail = stringResource(R.string.church_expires_in, remaining),
                    )
                }
            }
            if (showCombined) {
                item {
                    val multStr = "%.2f".format(combinedXpMult).trimEnd('0').trimEnd('.')
                    BonusRow(
                        name  = stringResource(R.string.bonus_combined_xp),
                        pct   = "${multStr}×",
                        scope = stringResource(R.string.bonus_all_skills),
                    )
                }
            }
            if (showAllPetsInBoosts) {
                items(bonusPets.filter { it.boostedSkill == "all" }, key = { it.id }) { pet ->
                    BonusRow(
                        name  = GameStrings.petName(context, pet.id),
                        pct   = "+${pet.boostPercent}%",
                        scope = stringResource(R.string.bonus_all_skills),
                    )
                }
            }
        }

        if (isCombatCape) {
            item { SlotSectionHeader(GameStrings.slotName(context, EquipSlot.CAPE)) }
            item {
                val pct = (cape!!.capeBonus * 100 + 0.5f).toInt()
                BonusRow(
                    name   = GameStrings.itemName(context, capeKey!!),
                    pct    = "+$pct%",
                    scope  = GameStrings.skillName(context, cape.capeSkill ?: ""),
                    detail = stringResource(R.string.bonus_combat_stat_boost),
                )
            }
        }

        if (skillEntries.isNotEmpty() || agilityFloorMin > 0.0 || coinPctBySkill.isNotEmpty() || builderDiscountPct > 0) {
            item { SlotSectionHeader(stringResource(R.string.bonus_section_skills)) }
            items(skillEntries, key = { it.skillKey }) { entry ->
                if (entry.xpPct > 0) {
                    val xpDetail = entry.xpSources.joinToString(" • ") { (label, pct) -> "$label +$pct%" }
                    BonusRow(
                        name   = entry.skillName,
                        pct    = stringResource(R.string.bonus_xp, entry.xpPct),
                        scope  = "",
                        detail = xpDetail.ifEmpty { null },
                    )
                }
                if (entry.yieldPct > 0) {
                    // Prayer's cape boosts burial XP rather than item yield
                    val yieldLabelRes = if (entry.skillKey == "prayer") R.string.bonus_boost else R.string.bonus_yield
                    BonusRow(
                        name   = entry.skillName,
                        pct    = stringResource(yieldLabelRes, entry.yieldPct),
                        scope  = "",
                        detail = entry.yieldSource,
                    )
                }
                if (entry.statBonus > 0) {
                    BonusRow(
                        name   = entry.skillName,
                        pct    = "+${entry.statBonus}",
                        scope  = "",
                        detail = stringResource(R.string.bonus_combat_stat_boost),
                    )
                }
            }
            if (agilityFloorMin > 0.0) {
                item {
                    val agilityLevel = state.skillLevels[Skills.AGILITY] ?: 1
                    val baseMinutes     = (SkillSimulator.sessionDurationMs(agilityLevel, 0.0) / 60_000L).toInt()
                    val prestigeMinutes = (SkillSimulator.sessionDurationMs(agilityLevel, agilityFloorMin) / 60_000L).toInt()
                    val savedMinutes    = baseMinutes - prestigeMinutes
                    if (savedMinutes > 0) {
                        BonusRow(
                            name   = GameStrings.skillName(context, Skills.AGILITY),
                            pct    = "-" + stringResource(R.string.combat_duration_min, savedMinutes),
                            scope  = stringResource(R.string.bonus_all_skills),
                            // Spell out that this row is only the prestige slice: the level-based
                            // reduction is already in baseMinutes, so players at high prestige
                            // don't read the increment as their total bonus
                            detail = stringResource(R.string.bonus_agility_prestige_detail, baseMinutes, agilityLevel, prestigeMinutes),
                        )
                    }
                }
            }
            items(coinPctBySkill, key = { "coin_${it.first}" }) { (skill, pct) ->
                BonusRow(
                    name  = GameStrings.skillName(context, skill),
                    pct   = stringResource(R.string.bonus_coin_return, pct),
                    scope = "",
                )
            }
            items(otherPrestigeRows, key = { "fx_${it.first}_${it.second}" }) { (skill, effect, value) ->
                BonusRow(
                    name   = GameStrings.skillName(context, skill),
                    pct    = prestigeEffectValueLabel(effect, value),
                    scope  = "",
                    detail = GameStrings.prestigeEffectDesc(context, effect, value),
                )
            }
            if (builderDiscountPct > 0) {
                item {
                    BonusRow(
                        name   = GameStrings.skillName(context, Skills.CONSTRUCTION),
                        pct    = "-$builderDiscountPct%",
                        scope  = stringResource(R.string.builder_title),
                        detail = stringResource(R.string.bonus_builder_discount_detail),
                    )
                }
            }
            if (state.towerXpBonusPct > 0) {
                item {
                    BonusRow(
                        name  = stringResource(R.string.tower_title),
                        pct   = stringResource(R.string.bonus_xp, state.towerXpBonusPct),
                        scope = stringResource(R.string.bonus_tower)
                    )
                }
            }
            if (state.towerCoinBonusPct > 0) {
                item {
                    BonusRow(
                        name  = stringResource(R.string.tower_title),
                        pct   = stringResource(R.string.bonus_coin_drops, state.towerCoinBonusPct),
                        scope = stringResource(R.string.bonus_tower)
                    )
                }
            }
            if (state.towerHpBonus > 0) {
                item {
                    BonusRow(
                        name  = stringResource(R.string.tower_title),
                        pct   = stringResource(R.string.bonus_hp, state.towerHpBonus * 10),
                        scope = stringResource(R.string.bonus_tower)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BonusRow(
    name: String,
    pct: String,
    scope: String,
    detail: String? = null,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = name,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (detail != null) {
                Text(
                    text  = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text  = pct,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (scope.isNotEmpty()) {
                Text(
                    text  = scope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/** Compact value label for a generic prestige-effect row (e.g. "+15%", "+1"). */
@Composable
internal fun prestigeEffectValueLabel(effect: String, value: Double): String = when (effect) {
    PrestigeBoosts.POTION_BONUS_FLAT, PrestigeBoosts.QUEUE_SLOT -> "+${value.toInt()}"
    PrestigeBoosts.BLESSING_COST_PCT, PrestigeBoosts.BUILDER_DISCOUNT_PCT, PrestigeBoosts.INPUT_SAVE_PCT -> "-${value.toInt()}%"
    PrestigeBoosts.FLOW_INTERVAL_REDUCTION -> "-${value.toInt()}m"
    else -> "+${value.toInt()}%"
}
