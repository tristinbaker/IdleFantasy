package com.fantasyidler.ui.screen

import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.Achievement
import com.fantasyidler.ui.viewmodel.AchievementsViewModel
import com.fantasyidler.ui.viewmodel.DISPLAY_SKILL_ORDER
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel:      InventoryViewModel    = hiltViewModel(),
    achievementsVm: AchievementsViewModel = hiltViewModel(),
) {
    val state    by viewModel.uiState.collectAsState()
    val achState by achievementsVm.uiState.collectAsState()
    val context   = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }
    var selectedTab  by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.label_skills),
        stringResource(R.string.label_inventory),
        stringResource(R.string.label_equipment),
        stringResource(R.string.label_pets),
        stringResource(R.string.label_achievements),
    )

    Scaffold(
        topBar       = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CoinsBanner(state.coins)

            // ── Character identity header ────────────────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text       = state.characterName.ifBlank { stringResource(R.string.profile_unnamed) },
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val subtitle = buildString {
                            if (state.characterRace.isNotBlank()) append(state.characterRace)
                            if (state.characterRace.isNotBlank() && state.characterGender.isNotBlank()) append(" • ")
                            if (state.characterGender.isNotBlank()) append(state.characterGender)
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = "Edit character",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.label_total_level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text       = state.totalLevel.toString(),
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> SkillsTab(state.skillLevels, state.skillXp, context)
                1 -> InventoryTab(state.inventory, context)
                2 -> EquipmentTab(
                    equipped       = state.equipped,
                    inventory      = state.inventory,
                    equippedFood   = state.equippedFood,
                    foodHealValues = viewModel.foodHealValues,
                    cookingRecipes = viewModel.cookingRecipes,
                    allEquipment   = viewModel.allEquipment,
                    context        = context,
                    onSlotTap      = viewModel::openSlotPicker,
                    onUnequip      = viewModel::unequip,
                    onEquipBest    = viewModel::equipBestGear,
                    onEquipFood    = viewModel::equipFood,
                    onUnequipFood  = viewModel::unequipFood,
                )
                3 -> PetsTab(
                    allPets     = viewModel.allPets,
                    ownedPetIds = state.ownedPetIds,
                )
                4 -> AchievementsTab(achState.byGroup, achState.unlockedCount, achState.totalCount)
            }
        }
    }

    // Equip-picker sheet
    state.pickingSlot?.let { slot ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlotPicker,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            EquipPickerSheet(
                slot      = slot,
                candidates = state.candidatesFor(slot, viewModel.allEquipment),
                context   = context,
                onEquip   = { itemKey -> viewModel.equip(itemKey, slot) },
                onDismiss = viewModel::dismissSlotPicker,
            )
        }
    }

    // Character edit sheet
    if (showEditSheet) {
        CharacterSetupSheet(
            isFirstTime   = false,
            initialName   = state.characterName,
            initialGender = state.characterGender,
            initialRace   = state.characterRace,
            onSave        = { name, gender, race ->
                viewModel.saveCharacterProfile(name, gender, race)
                showEditSheet = false
            },
            onDismiss     = { showEditSheet = false },
        )
    }

}

// ---------------------------------------------------------------------------
// Coins banner (also used by SkillsScreen result sheet)
// ---------------------------------------------------------------------------

@Composable
fun CoinsBanner(coins: Long) {
    Surface(
        color    = GoldPrimary.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.label_coins),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = coins.formatCoins(),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = GoldPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Skills tab
// ---------------------------------------------------------------------------

@Composable
private fun SkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    context: android.content.Context,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(DISPLAY_SKILL_ORDER) { key ->
            ProfileSkillRow(
                name  = GameStrings.skillName(context, key),
                level = skillLevels[key] ?: 1,
                xp    = skillXp[key] ?: 0L,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ProfileSkillRow(name: String, level: Int, xp: Long) {
    val progress = xpProgressFraction(xp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = level.toString(),
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text  = "${xp.formatXp()} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color    = GoldPrimary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Inventory tab
// ---------------------------------------------------------------------------

@Composable
private fun InventoryTab(
    inventory: Map<String, Int>,
    context: android.content.Context,
) {
    if (inventory.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.label_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(inventory.entries.toList()) { (key, qty) ->
            InventoryRow(name = GameStrings.itemName(context, key), qty = qty)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InventoryRow(name: String, qty: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text       = "×$qty",
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Achievements tab
// ---------------------------------------------------------------------------

@Composable
private fun AchievementsTab(
    byGroup: Map<String, List<Achievement>>,
    unlockedCount: Int,
    totalCount: Int,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.label_achievements),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text       = "$unlockedCount / $totalCount",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = GoldPrimary,
                    )
                }
            }
        }
        byGroup.forEach { (group, achievements) ->
            item(key = "hdr_$group") { SlotSectionHeader(group) }
            items(achievements, key = { it.id }) { ach ->
                AchievementRow(ach)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AchievementRow(ach: Achievement) {
    val alpha = if (ach.isUnlocked) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = ach.emoji,
            style    = MaterialTheme.typography.titleLarge,
            modifier = Modifier.size(36.dp),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = ach.name,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = ach.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (ach.isUnlocked) {
            Text(
                text  = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Pets tab
// ---------------------------------------------------------------------------

@Composable
private fun PetsTab(
    allPets: Map<String, com.fantasyidler.data.json.PetData>,
    ownedPetIds: Set<String>,
) {
    if (allPets.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.profile_no_pets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        val owned  = allPets.values.filter { it.id in ownedPetIds }
        val locked = allPets.values.filter { it.id !in ownedPetIds }

        if (owned.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_collected)) }
            items(owned, key = { it.id }) { pet ->
                PetRow(pet = pet, owned = true)
            }
        }
        if (locked.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_not_found)) }
            items(locked, key = { it.id }) { pet ->
                PetRow(pet = pet, owned = false)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PetRow(pet: com.fantasyidler.data.json.PetData, owned: Boolean) {
    val alpha = if (owned) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = pet.emoji,
            style    = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .width(48.dp)
                .then(if (owned) Modifier else Modifier),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text       = pet.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = pet.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Text(
            text  = stringResource(R.string.format_xp_boost_percent, pet.boostPercent),
            style = MaterialTheme.typography.labelMedium,
            color = (if (owned) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = alpha),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Equipment tab
// ---------------------------------------------------------------------------

@Composable
private fun EquipmentTab(
    equipped: Map<String, String?>,
    inventory: Map<String, Int>,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    cookingRecipes: Map<String, CookingRecipe>,
    allEquipment: Map<String, com.fantasyidler.data.json.EquipmentData>,
    context: android.content.Context,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBest: () -> Unit,
    onEquipFood: (String) -> Unit,
    onUnequipFood: (String) -> Unit,
) {
    val cookedItemKeys = remember(cookingRecipes) {
        cookingRecipes.values.map { it.cookedItem }.toSet()
    }
    val foodInInventory = remember(inventory, cookedItemKeys) {
        inventory.filterKeys { it in cookedItemKeys }.entries.toList()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Button(
                onClick  = onEquipBest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_equip_best))
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_combat_gear)) }
        items(EquipSlot.COMBAT_SLOTS) { slot ->
            val xpLabel = if (slot == EquipSlot.WEAPON)
                weaponXpLabel(allEquipment[equipped[slot]]?.combatStyle, context)
            else null
            EquipSlotRow(
                slotName   = slotDisplayName(slot),
                itemKey    = equipped[slot],
                xpLabel    = xpLabel,
                onTap      = { onSlotTap(slot) },
                onUnequip  = { onUnequip(slot) },
            )
        }
        item { SlotSectionHeader(stringResource(R.string.profile_gathering_tools)) }
        items(EquipSlot.TOOL_SLOTS) { slot ->
            EquipSlotRow(
                slotName   = slotDisplayName(slot),
                itemKey    = equipped[slot],
                onTap      = { onSlotTap(slot) },
                onUnequip  = { onUnequip(slot) },
            )
        }
        item { SlotSectionHeader(stringResource(R.string.profile_food_dungeon)) }
        if (foodInInventory.isEmpty()) {
            item {
                Text(
                    text     = stringResource(R.string.profile_no_food),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(foodInInventory, key = { "food_${it.key}" }) { (key, qty) ->
                FoodRow(
                    itemKey    = key,
                    qty        = qty,
                    healValue  = foodHealValues[key] ?: 0,
                    isEquipped = key in equippedFood,
                    context    = context,
                    onEquip    = { onEquipFood(key) },
                    onUnequip  = { onUnequipFood(key) },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun FoodRow(
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
private fun SlotSectionHeader(title: String) {
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
private fun EquipSlotRow(
    slotName: String,
    itemKey: String?,
    xpLabel: String? = null,
    onTap: () -> Unit,
    onUnequip: () -> Unit,
) {
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
            Text(
                text       = displayName,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
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
private fun EquipPickerSheet(
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
                text     = stringResource(R.string.profile_choose_slot, slotDisplayName(slot)),
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
                val xpLabel = weaponXpLabel(item.combatStyle, context).takeIf { item.slot == EquipSlot.WEAPON }
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

private fun weaponXpLabel(combatStyle: String?, context: android.content.Context): String? = when (combatStyle) {
    "attack"   -> context.getString(R.string.profile_stat_atk)
    "strength" -> context.getString(R.string.profile_stat_str)
    "ranged"   -> context.getString(R.string.profile_stat_ranged)
    "magic"    -> context.getString(R.string.profile_stat_magic)
    else       -> null
}

private fun buildEquipDetail(item: com.fantasyidler.data.json.EquipmentData, context: android.content.Context): String {
    val parts = mutableListOf<String>()
    item.miningEfficiency?.let      { parts.add("${context.getString(R.string.profile_stat_mining)} ×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { parts.add("${context.getString(R.string.profile_stat_wc)} ×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_fishing)} ×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_farming)} +${(it * 100).roundToInt()}%") }
    if (parts.isEmpty()) {
        if (item.attackBonus   != 0) parts.add("${context.getString(R.string.profile_stat_atk)} +${item.attackBonus}")
        if (item.strengthBonus != 0) parts.add("${context.getString(R.string.profile_stat_str)} +${item.strengthBonus}")
        if (item.defenseBonus  != 0) parts.add("${context.getString(R.string.profile_stat_def)} +${item.defenseBonus}")
    }
    val req = item.requirements.entries.firstOrNull()
    if (req != null) parts.add("${context.getString(R.string.profile_req_lv)}${req.value} ${req.key}")
    return parts.joinToString("  •  ")
}

