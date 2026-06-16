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

@Composable
internal fun BonusesTab(
    state: InventoryViewModel.UiState,
    allEquipment: Map<String, EquipmentData>,
    allPets: Map<String, PetData>,
) {
    val context = LocalContext.current
    val now     = System.currentTimeMillis()

    val boostActive     = state.xpBoostExpiresAt > now
    val blessingActive  = state.activeBlessingXpPct > 0 && state.activeBlessingExpiresAt > now
    val cape            = state.equipped[EquipSlot.CAPE]?.let { allEquipment[it] }?.takeIf { it.capeBonus > 0f }
    val bonusPets       = allPets.values.filter { it.id in state.ownedPetIds && it.boostPercent > 0 }.sortedBy { it.boostedSkill }
    val prestigeEntries = state.skillPrestige.entries.filter { it.value > 0 }.sortedBy { it.key }

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
        if (boostActive || blessingActive) {
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
        }

        if (cape != null) {
            item { SlotSectionHeader(GameStrings.slotName(context, EquipSlot.CAPE)) }
            item {
                val pct   = ((cape.capeBonus - 1f) * 100 + 0.5f).toInt()
                val skill = GameStrings.skillName(context, cape.capeSkill ?: "")
                BonusRow(name = cape.displayName, pct = "+$pct%", scope = skill)
            }
        }

        if (bonusPets.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.label_pets)) }
            items(bonusPets, key = { it.id }) { pet ->
                BonusRow(
                    name  = pet.displayName,
                    pct   = stringResource(R.string.format_xp_boost_percent, pet.boostPercent),
                    scope = GameStrings.skillName(context, pet.boostedSkill),
                )
            }
        }

        if (prestigeEntries.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.prestige)) }
            items(prestigeEntries.toList(), key = { it.key }) { (skill, level) ->
                BonusRow(
                    name  = GameStrings.skillName(context, skill),
                    pct   = "+${level * 10}%",
                    scope = stringResource(R.string.prestige),
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
