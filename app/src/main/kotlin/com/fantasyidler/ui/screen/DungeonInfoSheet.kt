package com.fantasyidler.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.viewmodel.CombatViewModel.Companion.MAX_DUNGEON_REPEAT_COUNT
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.GameStrings

// ---------------------------------------------------------------------------
// Dungeon info / start sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DungeonInfoSheet(
    dungeon: DungeonData,
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
    enemies: Map<String, EnemyData> = emptyMap(),
    onWeaponSlotSelected: (String) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onRepeatCountChanged: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context    = LocalContext.current
    var tappedEnemyKey by remember { mutableStateOf<String?>(null) }

    tappedEnemyKey?.let { enemyKey ->
        val enemy = enemies[enemyKey]
        if (enemy != null) {
            AlertDialog(
                onDismissRequest = { tappedEnemyKey = null },
                title = { Text(GameStrings.enemyName(context, enemyKey)) },
                text  = {
                    val drops = buildString {
                        if (enemy.alwaysDrops.isNotEmpty()) {
                            append(enemy.alwaysDrops.joinToString(", ") { "${GameStrings.itemName(context, it.item)} ×${it.quantity}" })
                        }
                        val notable = enemy.dropTable.sortedByDescending { it.chance }.take(4)
                        if (notable.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append(notable.joinToString(", ") {
                                val pct = (it.chance * 100).toInt()
                                val qty = if (it.quantityMin == it.quantityMax) "×${it.quantityMin}" else "×${it.quantityMin}–${it.quantityMax}"
                                "${GameStrings.itemName(context, it.item)} $qty ($pct%)"
                            })
                        }
                    }
                    val xp = enemy.xpDrops.entries.joinToString(", ") { (skill, xp) ->
                        "${GameStrings.skillName(context, skill)}: $xp XP"
                    }
                    Column {
                        Text("HP: ${enemy.hp}   Atk: ${enemy.combatStats.attackLevel}   Str: ${enemy.combatStats.strengthLevel}   Def: ${enemy.combatStats.defenseLevel}")
                        if (xp.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(xp, style = MaterialTheme.typography.bodySmall) }
                        if (drops.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(drops, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { tappedEnemyKey = null }) {
                        Text(stringResource(R.string.btn_close))
                    }
                },
            )
        }
    }
    val combatLvl  = combatLevelFrom(skillLevels)
    val canEnter   = combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = GameStrings.skillName(context, combatStyle)
    val canStart   = canEnter && !isStarting && !isQueueFull &&
        (combatStyle != "magic" || selectedSpell != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
    ) {
        Column(modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())) {
        Text(
            text       = GameStrings.dungeonName(context, dungeon.name),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = GameStrings.dungeonDesc(context, dungeon.name).takeIf { it.isNotBlank() } ?: dungeon.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Level and combat style rows
        StatRow(label = stringResource(R.string.combat_rec_level),
            value = dungeon.recommendedLevel.toString(),
            valueColor = if (canEnter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        StatRow(label = stringResource(R.string.combat_your_level), value = combatLvl.toString())
        StatRow(label = stringResource(R.string.label_combat_style), value = styleLabel, valueColor = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(12.dp))

        // Enemy spawn list
        if (dungeon.enemySpawns.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.combat_enemies),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dungeon.enemySpawns.forEach { spawn ->
                val hasStats = enemies.containsKey(spawn.enemy)
                Text(
                    text     = "• ${GameStrings.enemyName(context, spawn.enemy)}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = if (hasStats) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    modifier = if (hasStats) Modifier.clickable { tappedEnemyKey = spawn.enemy } else Modifier,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Weapon picker (show when 2+ weapon slots are occupied, or always if any weapon is equipped)
        if (equippedWeapons.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.combat_select_loadout),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier              = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding( vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                equippedWeapons.forEach { (slot, weaponData) ->
                    val isSelected = slot == (selectedWeaponSlot
                        ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) })
                    val style = EquipSlot.combatStyleForSlot(slot)!!
                    val iconRes = GameStrings.skillIconRes(style)
                    FilterChip(
                        selected = isSelected,
                        onClick  = { onWeaponSlotSelected(slot) },
                        label    = {
                            weaponData.combatStyle?.let { style ->
                                Text(
                                    text  = GameStrings.skillName(context, style),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
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
                }
            }
            Spacer(Modifier.height(12.dp))
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

        // Run count picker (queue this dungeon N times in one queue slot)
        Spacer(Modifier.height(12.dp))
        Text(
            text  = stringResource(R.string.combat_run_count_label),
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
                enabled = repeatCount < MAX_DUNGEON_REPEAT_COUNT,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 5, 10, 20).forEach { n ->
                    FilterChip(
                        selected = repeatCount == n,
                        onClick  = { onRepeatCountChanged(n) },
                        label    = { Text("×$n") },
                    )
                }
            }
        }
        } // end scrollable content

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
                        modifier  = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                    else Text(stringResource(R.string.btn_enter_dungeon))
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

@Composable
internal fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    val resolvedColor = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface
                        else valueColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = resolvedColor)
    }
}
