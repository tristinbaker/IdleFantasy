package com.fantasyidler.ui.screen

import android.widget.Toast
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.ConstructionRecipe
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.Achievement
import com.fantasyidler.ui.viewmodel.AchievementsViewModel
import com.fantasyidler.ui.viewmodel.ArmoryViewModel
import com.fantasyidler.ui.viewmodel.BestiaryViewModel
import com.fantasyidler.ui.viewmodel.InventoryCategory
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.toTitleCase

// ---------------------------------------------------------------------------
// Equipment tab
// ---------------------------------------------------------------------------

@Composable
internal fun EquipmentTab(
    equipped: Map<String, String?>,
    context: android.content.Context,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBestTools: () -> Unit = {},
    onNavigateToCombat: () -> Unit = {},
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedButton(
                onClick  = onNavigateToCombat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.profile_view_combat_gear))
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_gathering_tools)) }
        item {
            Button(
                onClick  = onEquipBestTools,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_equip_best_tools))
            }
        }
        items(EquipSlot.TOOL_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = slotDisplayName(context, slot),
                itemKey   = equipped[slot],
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun FoodRow(
    itemKey: String,
    qty: Int,
    healValue: Int,
    isEquipped: Boolean,
    context: android.content.Context,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = GameStrings.itemName(context, itemKey),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text  = stringResource(R.string.profile_food_desc, qty, healValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isEquipped) {
            TextButton(onClick = onUnequip) {
                Text(stringResource(R.string.btn_unequip), color = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = onEquip) {
                Text(stringResource(R.string.btn_equip), color = GoldPrimary)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun SlotSectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun EquipSlotRow(
    slotName: String,
    itemKey: String?,
    xpLabel: String? = null,
    equipment: com.fantasyidler.data.json.EquipmentData? = null,
    onTap: () -> Unit,
    onUnequip: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = slotName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        if (itemKey != null) {
            val baseName = itemKey.replace('_', ' ').split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            val displayName = if (xpLabel != null) "$baseName ($xpLabel)" else baseName
            Column(Modifier.weight(1f)) {
                Text(
                    text       = displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (equipment != null) {
                    val detail = buildEquipDetail(equipment, context, showReq = false)
                    if (detail.isNotEmpty()) {
                        Text(
                            text  = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onUnequip) {
                Icon(
                    imageVector        = Icons.Filled.Clear,
                    contentDescription = "Unequip",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text     = stringResource(R.string.label_none),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Equip picker sheet
// ---------------------------------------------------------------------------

@Composable
internal fun EquipPickerSheet(
    slot: String,
    candidates: List<com.fantasyidler.data.json.EquipmentData>,
    context: android.content.Context,
    onEquip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        item {
            Text(
                text     = stringResource(R.string.profile_choose_slot, slotDisplayName(context, slot)),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }

        if (candidates.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.profile_no_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                candidates.sortedWith(
                    compareBy({ it.requirements.values.maxOrNull() ?: 0 }, { it.name })
                )
            ) { item ->
                val xpLabel = weaponXpLabel(item.combatStyle, context).takeIf { item.slot == EquipSlot.WEAPON || EquipSlot.combatStyleForSlot(item.slot) != null }
                val displayName = buildString {
                    append(GameStrings.itemName(context, item.name))
                    if (xpLabel != null) append(" ($xpLabel)")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEquip(item.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val detail = buildEquipDetail(item, context)
                        if (detail.isNotEmpty()) {
                            Text(
                                text  = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text  = stringResource(R.string.btn_equip),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

internal fun weaponXpLabel(combatStyle: String?, context: android.content.Context): String? = when (combatStyle) {
    "attack"   -> context.getString(R.string.profile_stat_atk)
    "strength" -> context.getString(R.string.profile_stat_str)
    "ranged"   -> context.getString(R.string.profile_stat_ranged)
    "magic"    -> context.getString(R.string.profile_stat_magic)
    else       -> null
}

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

internal fun buildEquipDetail(item: com.fantasyidler.data.json.EquipmentData, context: android.content.Context, showReq: Boolean = true): String {
    val parts = mutableListOf<String>()
    item.miningEfficiency?.let      { parts.add("${context.getString(R.string.profile_stat_mining)} ×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { parts.add("${context.getString(R.string.profile_stat_wc)} ×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_fishing)} ×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_farming)} ${context.getString(R.string.farming_fertilizer_yield, (it * 100).toInt())}") }
    item.smithingEfficiency?.let    { parts.add("${context.getString(R.string.profile_stat_smithing)} ×${"%.2f".format(it)}") }
    item.firemakingEfficiency?.let  { parts.add("${context.getString(R.string.profile_stat_firemaking)} ×${"%.2f".format(it)}") }
    item.agilityEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_agility)} ×${"%.2f".format(it)}") }
    item.cookingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_cooking)} ×${"%.2f".format(it)}") }
    item.thievingEfficiency?.let    { parts.add("${context.getString(R.string.profile_stat_thieving)} ×${"%.2f".format(it)}") }
    if (parts.isEmpty()) {
        if (item.attackBonus   != 0) parts.add("${context.getString(R.string.profile_stat_atk)} +${item.attackBonus}")
        if (item.strengthBonus != 0) parts.add("${context.getString(R.string.profile_stat_str)} +${item.strengthBonus}")
        if (item.defenseBonus  != 0) parts.add("${context.getString(R.string.profile_stat_def)} +${item.defenseBonus}")
    }
    if ((item.rangedAttackBonus   ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_ranged)} ${context.getString(R.string.profile_stat_atk)} +${item.rangedAttackBonus}")
    if ((item.rangedStrengthBonus ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_ranged)} ${context.getString(R.string.profile_stat_str)} +${item.rangedStrengthBonus}")
    if ((item.magicAttackBonus    ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_magic)} ${context.getString(R.string.profile_stat_atk)} +${item.magicAttackBonus}")
    if ((item.magicDamageBonus    ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_magic)} Dmg +${item.magicDamageBonus}")
    item.attackSpeed?.let { parts.add("${context.getString(R.string.armory_stat_attack_speed)} ${"%.1f".format(it)}s") }
    if (item.capeBonus != 0f) {
        val capeLabelRes = if (item.capeSkill in COMBAT_CAPE_SKILLS) R.string.armory_stat_cape else R.string.armory_stat_cape_yield
        parts.add("${context.getString(capeLabelRes)} +${(item.capeBonus * 100).toInt()}%")
    }
    if (showReq) {
        for ((skill, lvl) in item.requirements) {
            parts.add("${context.getString(R.string.profile_req_lv)} $lvl ${skill.replaceFirstChar { it.uppercase() }}")
        }
    }
    return parts.joinToString("  •  ")
}
