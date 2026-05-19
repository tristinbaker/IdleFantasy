package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.CoinsBadge
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.HeroBlock
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.screen.profile.ProfileLoadingState
import com.fantasyidler.ui.screen.profile.achievements.AchievementsGrid
import com.fantasyidler.ui.screen.profile.equipment.EquipPickerSheet
import com.fantasyidler.ui.screen.profile.equipment.EquipmentSlotsSection
import com.fantasyidler.ui.screen.profile.inventory.InventorySection
import com.fantasyidler.ui.screen.profile.pets.PetsTab
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.Achievement
import com.fantasyidler.ui.viewmodel.AchievementsUiState
import com.fantasyidler.ui.viewmodel.AchievementsViewModel
import com.fantasyidler.ui.viewmodel.DISPLAY_SKILL_ORDER
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp

/**
 * Top-level Profile screen. Hosts the character hero header and a five-tab
 * pager (Skills / Inventory / Gear / Pets / Achievements). Each tab body is
 * extracted into the `ui/screen/profile/` subpackage so this file stays at
 * "glue + skills" weight rather than the previous 800-line monolith.
 */
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
    val tokens = LocalFantasyTokens.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var selectedTab   by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.label_skills),
        stringResource(R.string.label_inventory),
        stringResource(R.string.label_equipment),
        stringResource(R.string.label_pets),
        stringResource(R.string.label_achievements),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                ProfileLoadingState()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CoinsBadge(state.coins)

            Box(modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m)) {
                ProfileHeroHeader(
                    name        = state.characterName.ifBlank { stringResource(R.string.profile_unnamed) },
                    race        = state.characterRace,
                    gender      = state.characterGender,
                    totalLevel  = state.totalLevel,
                    onEdit      = { showEditSheet = true },
                )
            }

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) },
                        modifier = Modifier.defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l),
                    )
                }
            }

            when (selectedTab) {
                0 -> SkillsTab(state.skillLevels, state.skillXp, context)
                1 -> InventorySection(state.inventory, context)
                2 -> EquipmentSlotsSection(
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
                4 -> AchievementsGrid(
                    byGroup       = achState.byGroup,
                    unlockedCount = achState.unlockedCount,
                    totalCount    = achState.totalCount,
                )
            }
        }
    }

    state.pickingSlot?.let { slot ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlotPicker,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            EquipPickerSheet(
                slot       = slot,
                candidates = state.candidatesFor(slot, viewModel.allEquipment),
                context    = context,
                onEquip    = { itemKey -> viewModel.equip(itemKey, slot) },
                onDismiss  = viewModel::dismissSlotPicker,
            )
        }
    }

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

/**
 * Hero card at the top of the profile screen — wraps [HeroBlock] with the
 * character avatar, name / race / gender subtitle, edit shortcut, and the
 * total-level readout. Kept inline because it's a one-off composition of the
 * shared HeroBlock primitive rather than a reusable section.
 */
@Composable
private fun ProfileHeroHeader(
    name: String,
    race: String,
    gender: String,
    totalLevel: Int,
    onEdit: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    HeroBlock(
        title    = name,
        subtitle = buildString {
            if (race.isNotBlank()) append(race)
            if (race.isNotBlank() && gender.isNotBlank()) append(" • ")
            if (gender.isNotBlank()) append(gender)
        }.ifBlank { null },
        leading  = {
            IconDisk(
                imageVector        = Icons.Filled.Person,
                contentDescription = null,
                size               = tokens.spacing.xxl + tokens.spacing.xl,
                background         = tokens.colors.primary.copy(alpha = 0.24f),
            )
        },
        trailing = {
            IconButton(
                onClick  = onEdit,
                modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.l),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_character),
                    tint               = tokens.colors.onSurfaceMuted,
                )
            }
        },
        content  = {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.label_total_level),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                Text(
                    text       = totalLevel.toString(),
                    style      = tokens.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.primary,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Skills tab (kept inline — small, tightly coupled to the screen header).
// ---------------------------------------------------------------------------

@Composable
private fun SkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    context: android.content.Context,
) {
    val tokens = LocalFantasyTokens.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(DISPLAY_SKILL_ORDER, key = { it }) { key ->
            ProfileSkillRow(
                name  = GameStrings.skillName(context, key),
                level = skillLevels[key] ?: 1,
                xp    = skillXp[key] ?: 0L,
            )
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun ProfileSkillRow(name: String, level: Int, xp: Long) {
    val tokens = LocalFantasyTokens.current
    val progress = xpProgressFraction(xp)
    ChunkyCard(
        modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape    = tokens.shapes.badge,
                color    = tokens.colors.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.m),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text       = level.toString(),
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.primary,
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s + tokens.spacing.xs))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text       = name,
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.onSurface,
                    )
                    Text(
                        text  = "${xp.formatXp()} XP",
                        style = tokens.typography.labelSmall,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.s + tokens.spacing.xs))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.s + tokens.spacing.xs)
                        .clip(RoundedCornerShape(tokens.spacing.xs)),
                    color    = tokens.colors.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun PreviewProfileHeroHeader() {
    FantasyPreviewSurface {
        ProfileHeroHeader(
            name       = "Lorenzo",
            race       = "Elf",
            gender     = "Male",
            totalLevel = 247,
            onEdit     = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewProfileSkillRow() {
    FantasyPreviewSurface {
        ProfileSkillRow(name = "Mining", level = 42, xp = 123_456L)
    }
}

@PreviewLightDark
@Composable
private fun PreviewProfileScreenLoading() {
    FantasyPreviewSurface { ProfileLoadingState() }
}

@Suppress("UnusedPrivateMember")
@PreviewLightDark
@Composable
private fun PreviewProfileScreenAchievementsState() {
    FantasyPreviewSurface {
        // Stand-in to demo the AchievementsUiState shape used by the screen
        // body — preview surface only, not wired to a real VM.
        val state = AchievementsUiState(
            isLoading     = false,
            byGroup       = mapOf("Levelling" to listOf(Achievement("a", "Adventurer", "Reach total level 50", "⚔️", true))),
            unlockedCount = 1,
            totalCount    = 1,
        )
        AchievementsGrid(state.byGroup, state.unlockedCount, state.totalCount)
    }
}
