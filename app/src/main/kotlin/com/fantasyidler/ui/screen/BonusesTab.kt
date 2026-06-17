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
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.toTitleCase

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

private data class SkillBonusEntry(
    val skillKey: String,
    val skillName: String,
    val combinedPct: Int,
    val sources: List<Pair<String, Int>>,
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

    val specificSkillKeys = buildSet<String> {
        if (isGatheringCape) cape?.capeSkill?.let { add(it) }
        specificBonusPets.forEach { add(it.boostedSkill) }
        prestigeEntries.forEach { add(it.key) }
    }

    val skillEntries: List<SkillBonusEntry> = specificSkillKeys.sorted().map { skillKey ->
        val capePct        = if (isGatheringCape && cape?.capeSkill == skillKey) (cape.capeBonus * 100 + 0.5f).toInt() else 0
        val specificPetPct = specificBonusPets.filter { it.boostedSkill == skillKey }.sumOf { it.boostPercent }
        val totalPetPct    = specificPetPct + allPetBoostPct
        val prestigePct    = (state.skillPrestige[skillKey] ?: 0) * 10

        val sources = buildList {
            if (capePct > 0)      add(cape!!.displayName to capePct)
            if (totalPetPct > 0)  add(context.getString(R.string.label_pets) to totalPetPct)
            if (prestigePct > 0)  add(context.getString(R.string.prestige) to prestigePct)
        }
        SkillBonusEntry(
            skillKey    = skillKey,
            skillName   = GameStrings.skillName(context, skillKey),
            combinedPct = capePct + totalPetPct + prestigePct,
            sources     = sources,
        )
    }

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

        if (skillEntries.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.bonus_section_skills)) }
            items(skillEntries, key = { it.skillKey }) { entry ->
                val detail = entry.sources.joinToString(" • ") { (label, pct) -> "$label +$pct%" }
                BonusRow(
                    name   = entry.skillName,
                    pct    = "+${entry.combinedPct}%",
                    scope  = "",
                    detail = detail.ifEmpty { null },
                )
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
