package com.fantasyidler.ui.screen

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
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.repository.resolveCapeMultiplier
import com.fantasyidler.repository.isGuildCapeForSkill
import com.fantasyidler.repository.resolveOwnedCapeKeysForSkill
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.toTitleCase

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

    val boostActive    = state.xpBoostExpiresAt > now
    val blessingActive = state.activeBlessingXpPct > 0 && state.activeBlessingExpiresAt > now
    val cape           = state.equipped[EquipSlot.CAPE]?.let { allEquipment[it] }?.takeIf { it.capeBonus > 0f }
    val bonusPets      = allPets.values.filter { it.id in state.ownedPetIds && it.boostPercent > 0 }
    val prestigeEntries = state.skillPrestige.entries.filter { it.value > 0 }

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
            skillPrestige = state.skillPrestige,
            allEquipment = allEquipment
        )
        mult > 1.0f
    }

    val specificSkillKeys = buildSet<String> {
        addAll(activeCapeSkills)
        specificBonusPets.forEach { add(it.boostedSkill) }
        prestigeEntries.forEach { add(it.key) }
    }

    val skillEntries: List<SkillBonusEntry> = specificSkillKeys.sorted().map { skillKey ->
        val capePrestige   = state.skillPrestige[skillKey] ?: 0
        val capeMult = resolveCapeMultiplier(
            skillName = skillKey,
            equippedCape = cape,
            inventoryKeys = state.inventory.keys,
            townBuildingTiers = state.townBuildingTiers,
            skillPrestige = state.skillPrestige,
            allEquipment = allEquipment
        )
        val isCombatStat = skillKey in COMBAT_STAT_SKILLS
        val yieldPct = if (!isCombatStat && skillKey != "slayer") ((capeMult - 1f) * 100 + 0.5f).toInt() else 0
        val capeXpPct = if (isCombatStat || skillKey == "slayer") ((capeMult - 1f) * 100 + 0.5f).toInt() else 0

        val specificPetPct = specificBonusPets.filter { it.boostedSkill == skillKey }.sumOf { it.boostPercent }
        val totalPetPct    = specificPetPct + allPetBoostPct
        val prestigeLevel  = state.skillPrestige[skillKey] ?: 0
        val prestigePct    = if (isCombatStat) 0 else prestigeLevel * 10
        val statBonus      = if (isCombatStat) prestigeLevel * 5 else 0

        val activeCapeName = run {
            if (capeMult <= 1.0f) return@run null
            val equippedCapeSkill = cape?.capeSkill
            if (equippedCapeSkill != null && (equippedCapeSkill == skillKey || isGuildCapeForSkill(equippedCapeSkill, skillKey))) {
                return@run cape.displayName
            }
            val candidateKeys = resolveOwnedCapeKeysForSkill(skillKey)
            val bestCapeKey = candidateKeys.filter { state.inventory.containsKey(it) }
                .maxByOrNull { allEquipment[it]?.capeBonus ?: 0f }
            bestCapeKey?.let { allEquipment[it]?.displayName }
        }

        val xpSources = buildList {
            if (totalPetPct > 0)  add(context.getString(R.string.label_pets) to totalPetPct)
            if (prestigePct > 0)  add(context.getString(R.string.prestige) to prestigePct)
            if (capeXpPct > 0)    add((activeCapeName ?: "Cape") to capeXpPct)
        }
        SkillBonusEntry(
            skillKey    = skillKey,
            skillName   = GameStrings.skillName(context, skillKey),
            xpPct       = totalPetPct + prestigePct + capeXpPct,
            yieldPct    = yieldPct,
            statBonus   = statBonus,
            xpSources   = xpSources,
            yieldSource = if (yieldPct > 0) activeCapeName else null,
        )
    }

    val agilityPrestige    = state.skillPrestige[Skills.AGILITY] ?: 0
    val mercantilePrestige = state.skillPrestige[Skills.MERCANTILE] ?: 0

    // "all" pets with no skill-specific rows: surface them in the Boosts section
    val showAllPetsInBoosts = allPetBoostPct > 0 && specificSkillKeys.isEmpty()

    if (!boostActive && !blessingActive && cape == null && bonusPets.isEmpty() && prestigeEntries.isEmpty()) {
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
        if (boostActive || blessingActive || showAllPetsInBoosts) {
            item { SlotSectionHeader(stringResource(R.string.bonus_section_boosts)) }
            if (boostActive) {
                item {
                    val remaining = (state.xpBoostExpiresAt - now).formatDurationMs()
                    BonusRow(
                        name   = stringResource(R.string.label_xp_boost),
                        pct    = "+100%",
                        scope  = stringResource(R.string.bonus_all_skills),
                        detail = stringResource(R.string.church_expires_in, remaining),
                    )
                }
            }
            if (blessingActive) {
                item {
                    val blessingName = context.stringByName("blessing_${state.activeBlessingKey}_name")
                        ?: state.activeBlessingKey.toTitleCase()
                    val remaining = (state.activeBlessingExpiresAt - now).formatDurationMs()
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
                        name  = pet.displayName,
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
                    name   = cape.displayName,
                    pct    = "+$pct%",
                    scope  = GameStrings.skillName(context, cape.capeSkill ?: ""),
                    detail = stringResource(R.string.bonus_combat_stat_boost),
                )
            }
        }

        if (skillEntries.isNotEmpty() || agilityPrestige > 0 || mercantilePrestige > 0) {
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
                    BonusRow(
                        name   = entry.skillName,
                        pct    = stringResource(R.string.bonus_yield, entry.yieldPct),
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
            if (agilityPrestige > 0) {
                item {
                    val agilityLevel = state.skillLevels[Skills.AGILITY] ?: 1
                    val savedMinutes = ((SkillSimulator.sessionDurationMs(agilityLevel, 0) -
                        SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige)) / 60_000L).toInt()
                    if (savedMinutes > 0) {
                        BonusRow(
                            name  = GameStrings.skillName(context, Skills.AGILITY),
                            pct   = stringResource(R.string.bonus_session_shorter, savedMinutes),
                            scope = stringResource(R.string.bonus_all_skills),
                        )
                    }
                }
            }
            if (mercantilePrestige > 0) {
                item {
                    BonusRow(
                        name  = GameStrings.skillName(context, Skills.MERCANTILE),
                        pct   = stringResource(R.string.bonus_coin_return, mercantilePrestige * 10),
                        scope = "",
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
                        pct   = stringResource(R.string.bonus_hp, state.towerHpBonus),
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
        Text(
            text  = pct,
            style = MaterialTheme.typography.labelLarge,
            color = GoldPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = scope,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
