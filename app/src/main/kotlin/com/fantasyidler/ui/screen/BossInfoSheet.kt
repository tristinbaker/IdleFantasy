package com.fantasyidler.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.painterResource
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.viewmodel.CombatViewModel.Companion.MAX_BOSS_REPEAT_COUNT
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.ui.viewmodel.MercContract
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown

// ---------------------------------------------------------------------------
// Boss info / start sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BossInfoSheet(
    boss: BossData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    equippedWeapons: Map<String, EquipmentData>,
    selectedWeaponSlot: String?,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    potionEffects: Map<String, Map<String, Int>>,
    selectedPotionKey: String?,
    isStarting: Boolean,
    isQueueFull: Boolean = false,
    repeatCount: Int,
    fullCoinKillsLeft: Int = PlayerRepository.BOSS_FULL_COIN_KILLS_PER_DAY,
    hiredMercs: List<MercContract> = emptyList(),
    onOpenMercCamp: () -> Unit = {},
    onWeaponSlotSelected: (String) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onRepeatCountChanged: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context   = LocalContext.current
    val combatLvl = combatLevelFrom(skillLevels)
    // Raid bosses show a beyond-player combat level for flavor; the mercenary party is
    // their real gate, so the level requirement never blocks starting one.
    val canFight  = boss.raid || combatLvl >= boss.combatLevelRequired
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = GameStrings.skillName(context, combatStyle)
    val canStart = canFight && !isStarting && !isQueueFull &&
        (combatStyle != "magic" || selectedSpell != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Column(modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BossIcon(
                bossId        = boss.id,
                modifier      = Modifier.size(48.dp),
                fallbackEmoji = boss.emoji,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text       = GameStrings.bossName(context, boss.id),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text  = GameStrings.bossDesc(context, boss.id).takeIf { it.isNotBlank() } ?: boss.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_req_level), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text       = boss.combatLevelRequired.toString(),
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = if (canFight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_your_level), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(combatLvl.toString(), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_duration), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.combat_duration_min, boss.durationMinutes), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }
        StatRow(label = stringResource(R.string.label_combat_style), value = styleLabel, valueColor = MaterialTheme.colorScheme.primary)

        // Weapon picker
        if (equippedWeapons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.combat_select_loadout),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val currentWeaponSlot = selectedWeaponSlot
                ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) }
            var weaponExpanded by remember { mutableStateOf(false) }
            val style = currentWeaponSlot?.let { EquipSlot.combatStyleForSlot(it) }
            val iconRes = style?.let { GameStrings.skillIconRes(it) }
            ExposedDropdownMenuBox(
                expanded         = weaponExpanded,
                onExpandedChange = { weaponExpanded = it },
            ) {
                OutlinedTextField(
                    value         = equippedWeapons[currentWeaponSlot]?.combatStyle?.let { GameStrings.skillName(context, it) } ?: "",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weaponExpanded) },
                    colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    singleLine    = true,
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    leadingIcon = if (iconRes != null) {
                        {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                )
                ExposedDropdownMenu(
                    expanded         = weaponExpanded,
                    onDismissRequest = { weaponExpanded = false },
                ) {
                    equippedWeapons.forEach { (slot, weaponData) ->
                        val style = EquipSlot.combatStyleForSlot(slot)!!
                        val iconRes = GameStrings.skillIconRes(style)
                        DropdownMenuItem(
                            leadingIcon = if (iconRes != null) {
                                {
                                    Image(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                            text = {
                                weaponData.combatStyle?.let { style ->
                                    Text(
                                        text  = GameStrings.skillName(context, style),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            },
                            onClick = {
                                onWeaponSlotSelected(slot)
                                weaponExpanded = false
                            },
                        )
                    }
                }
            }
        }

        if (boss.xpRewards.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.combat_xp_on_victory), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((skill, xp) in boss.xpRewards) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodySmall)
                    Text("+$xp ${stringResource(R.string.label_xp)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Potion picker
        if (availablePotions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PotionLoadoutPicker(
                availablePotions  = availablePotions,
                potionEffects     = potionEffects,
                selectedPotionKey = selectedPotionKey,
                context           = context,
                onPotionSelected  = onPotionSelected,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text  = if (fullCoinKillsLeft > 0) stringResource(R.string.boss_coin_cap_remaining, fullCoinKillsLeft)
                    else stringResource(R.string.boss_coin_cap_spent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Fight count picker (queue this boss N times in one queue slot)
        Spacer(Modifier.height(12.dp))
        Text(
            text  = stringResource(R.string.combat_fight_count_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onRepeatCountChanged(repeatCount - 1) },
                enabled = repeatCount > 1,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Text(
                text       = repeatCount.toString(),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.width(48.dp),
            )
            IconButton(
                onClick = { onRepeatCountChanged(repeatCount + 1) },
                enabled = repeatCount < MAX_BOSS_REPEAT_COUNT,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 5, 10, 25, MAX_BOSS_REPEAT_COUNT).forEach { n ->
                    FilterChip(
                        selected = repeatCount == n,
                        onClick  = { onRepeatCountChanged(n) },
                        label    = { Text("×$n") },
                    )
                }
            }
        }

        if (boss.raid) {
            Spacer(Modifier.height(16.dp))
            Text(
                text       = stringResource(R.string.raid_party_title),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (hiredMercs.isEmpty()) {
                Text(
                    text  = stringResource(R.string.raid_solo_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                hiredMercs.forEach { contract ->
                    val m = contract.merc
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = "${m.emoji} ${GameStrings.mercName(context, m.id)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text  = stringResource(R.string.merc_stats, m.attackLevel + m.attackBonus, m.strengthLevel + m.strengthBonus, m.defenseLevel, m.hp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val remainingMs = (hiredMercs.minOf { it.expiresAt } - System.currentTimeMillis()).coerceAtLeast(0L)
                Text(
                    text  = stringResource(R.string.merc_contract_remaining, remainingMs.formatDurationMs(context)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenMercCamp) {
                Text(stringResource(R.string.merc_camp_open))
            }
        }

        } // end scrollable content

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            val queueFullMessage = stringResource(R.string.snackbar_queue_full)
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick  = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = canStart,
                ) {
                    if (isStarting) CircularProgressIndicator(
                        modifier    = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    ) else Text(stringResource(R.string.btn_fight))
                }
                if (isQueueFull) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null,
                                onClick           = { AppBannerCenter.enqueue(queueFullMessage) },
                            ),
                    )
                }
            }
        }
    }
}
