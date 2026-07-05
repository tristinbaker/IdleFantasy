package com.fantasyidler.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.SuccessGreen
import com.fantasyidler.ui.viewmodel.CombatSessionResult
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import com.fantasyidler.util.toTitleCase
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    potionEffects: Map<String, Map<String, Int>>,
    selectedPotionKey: String?,
    selectedArrowKey: String?,
    isStarting: Boolean,
    enemies: Map<String, EnemyData> = emptyMap(),
    onWeaponSlotSelected: (String) -> Unit,
    onSpellSelected: (SpellData) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onArrowSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context    = LocalContext.current
    var tappedEnemyKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    tappedEnemyKey?.let { enemyKey ->
        val enemy = enemies[enemyKey]
        if (enemy != null) {
            AlertDialog(
                onDismissRequest = { tappedEnemyKey = null },
                title = { Text(enemy.displayName) },
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
    val combatLvl  = combatLevel(skillLevels)
    val canEnter   = combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = GameStrings.skillName(context, combatStyle)
    val canStart   = canEnter && !isStarting &&
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
            valueColor = if (canEnter) GoldPrimary else MaterialTheme.colorScheme.error)
        StatRow(label = stringResource(R.string.combat_your_level), value = combatLvl.toString())
        StatRow(label = stringResource(R.string.label_combat_style), value = styleLabel, valueColor = GoldPrimary)

        // Ranged: arrow picker
        if (combatStyle == "ranged") {
            val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
            Text(
                text  = stringResource(R.string.combat_label_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val arrowOptions = listOf(null) + availableArrows
            arrowOptions.forEach { key ->
                val isSelected = selectedArrowKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArrowSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_arrow_auto)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (key != null) {
                        Text(
                            text  = "×${inventory[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

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
                text  = "Weapon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                equippedWeapons.forEach { (slot, weaponData) ->
                    val isSelected = slot == (selectedWeaponSlot
                        ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) })
                    FilterChip(
                        selected = isSelected,
                        onClick  = { onWeaponSlotSelected(slot) },
                        label    = {
                            Column {
                                Text(
                                    text  = GameStrings.itemName(context, weaponData.name),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                weaponData.combatStyle?.let { style ->
                                    Text(
                                        text  = style.replaceFirstChar { it.titlecase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Magic: spell picker
        if (combatStyle == "magic") {
            Text(
                text  = stringResource(R.string.label_spell),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (availableSpells.isEmpty()) {
                Text(
                    text  = stringResource(R.string.combat_no_spells),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var onlyCastable by remember { mutableStateOf(true) }
                val displaySpells = if (onlyCastable)
                    availableSpells.filter { spell ->
                        equippedWeapon?.infiniteRunes == "all" ||
                        equippedWeapon?.infiniteRunes == spell.runeType ||
                        (inventory[spell.runeType] ?: 0) >= spell.runeCost
                    }
                else
                    availableSpells
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    FilterChip(
                        selected = onlyCastable,
                        onClick  = { onlyCastable = !onlyCastable },
                        label    = { Text(stringResource(R.string.combat_only_castable)) },
                    )
                }
                displaySpells.forEach { spell ->
                    val isSelected = selectedSpell?.name == spell.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpellSelected(spell) }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text       = GameStrings.spellName(context, spell.name),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "${spell.runeCost}× ${GameStrings.itemName(context, spell.runeType)}  •  ${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val infinite = equippedWeapon?.infiniteRunes == "all" || equippedWeapon?.infiniteRunes == spell.runeType
                            if (infinite) {
                                Text(
                                    text  = stringResource(R.string.combat_infinite_runes, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val held = inventory[spell.runeType] ?: 0
                                Text(
                                    text  = stringResource(R.string.combat_you_have_runes, held, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (held >= spell.runeCost) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (isSelected) {
                            Text(
                                text  = "✓",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // Potion picker
        if (availablePotions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Potion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val potionOptions = listOf(null) + availablePotions.keys.toList()
            potionOptions.forEach { key ->
                val isSelected = selectedPotionKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPotionSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_no_potion)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (key != null) {
                            val effectStr = potionEffects[key]?.entries
                                ?.joinToString(", ") { (stat, bonus) -> "+$bonus ${stat.toTitleCase()}" }
                            if (effectStr != null) {
                                Text(
                                    text  = "($effectStr)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (key != null) {
                        Text(
                            text  = "×${availablePotions[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        } // end scrollable content

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(
                onClick  = onStart,
                modifier = Modifier.weight(1f),
                enabled  = canStart,
            ) {
                if (isStarting) CircularProgressIndicator(
                    modifier  = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                )
                else Text(stringResource(R.string.btn_enter_dungeon))
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
