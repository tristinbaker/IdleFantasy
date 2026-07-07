package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.CraftingRecipe
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.FletchingRecipe
import com.fantasyidler.data.json.HerbloreRecipe
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.SlayerTaskData
import com.fantasyidler.data.json.SmithingRecipe
import com.fantasyidler.data.json.ConstructionRecipe
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.json.TreeData
import android.content.Context
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext

/** One row on the Profile Banners tab — either an earned banner or a locked placeholder for a known event. */
data class SeasonalBannerDisplay(
    val eventId: String,
    val label: String,
    val bannerIcon: String?,
    val earned: Boolean,
    val earnedAtMs: Long?,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    data class UiState(
        val coins: Long = 0L,
        /** item key → quantity, sorted by qty descending */
        val inventory: Map<String, Int> = emptyMap(),
        val skillLevels: Map<String, Int> = emptyMap(),
        val skillXp: Map<String, Long> = emptyMap(),
        /** slot key → item key (null = empty) */
        val equipped: Map<String, String?> = emptyMap(),
        /** Owned pets (active, providing XP boosts). */
        val ownedPetIds: Set<String> = emptySet(),
        /** Non-null while the equip-picker sheet is open. */
        val pickingSlot: String? = null,
        val isLoading: Boolean = true,
        /** Food items marked for dungeon use (key = item key, value = ignored). */
        val equippedFood: Map<String, Int> = emptyMap(),
        val characterName: String = "",
        val characterGender: String = "",
        val characterRace: String = "",
        val snackbarMessage: String? = null,
        val skillingDungeonNotes: Map<String, Int> = emptyMap(),
        val unlockedDungeons: List<String> = emptyList(),
        val xpBoostExpiresAt: Long = 0L,
        val activeBlessingKey: String = "",
        val activeBlessingExpiresAt: Long = 0L,
        val activeBlessingXpPct: Int = 0,
        val skillPrestige: Map<String, Int> = emptyMap(),
        val seasonalBanners: List<SeasonalBannerDisplay> = emptyList(),
    ) {
        val totalLevel: Int get() = skillLevels.values.sum()

        /** Items in inventory that can go into [pickingSlot]. */
        fun candidatesFor(slot: String, allEquipment: Map<String, EquipmentData>): List<EquipmentData> {
            val style = EquipSlot.combatStyleForSlot(slot)
            return if (style != null) {
                inventory.keys.mapNotNull { allEquipment[it] }
                    .filter { it.slot == EquipSlot.WEAPON && it.combatStyle == style }
            } else {
                inventory.keys.mapNotNull { allEquipment[it] }.filter { it.slot == slot }
            }
        }
    }

    init {
        viewModelScope.launch { migrateWeaponSlots() }
    }

    private suspend fun migrateWeaponSlots() {
        val equipped = playerRepo.getEquipped().toMutableMap()
        val oldWeapon = equipped[EquipSlot.WEAPON] ?: return
        if (EquipSlot.WEAPON_SLOTS.any { equipped[it] != null }) return
        val style = gameData.equipment[oldWeapon]?.combatStyle
        val targetSlot = when (style) {
            "strength" -> EquipSlot.WEAPON_STR
            "ranged"   -> EquipSlot.WEAPON_RANGED
            "magic"    -> EquipSlot.WEAPON_MAGIC
            else       -> EquipSlot.WEAPON_ATK
        }
        equipped[targetSlot] = oldWeapon
        equipped.remove(EquipSlot.WEAPON)
        playerRepo.updateEquipped(equipped)
    }

    private val _extra = MutableStateFlow(UiState())

    val uiState: StateFlow<UiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) {
            extra
        } else {
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val pets: List<com.fantasyidler.data.model.OwnedPet> = json.decodeFromString(player.pets)
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            extra.copy(
                coins       = player.coins,
                inventory   = inventory.entries
                    .sortedByDescending { it.value }
                    .associate { it.key to it.value },
                skillLevels = json.decodeFromString(player.skillLevels),
                skillXp     = json.decodeFromString(player.skillXp),
                equipped    = json.decodeFromString(player.equipped),
                ownedPetIds = pets.map { it.id }.toSet(),
                equippedFood          = flags.equippedFood,
                characterName         = flags.characterName,
                characterGender       = flags.characterGender,
                characterRace         = flags.characterRace,
                skillingDungeonNotes  = flags.skillingDungeonNotes,
                unlockedDungeons      = flags.unlockedDungeons,
                xpBoostExpiresAt        = flags.xpBoostExpiresAt,
                activeBlessingKey       = flags.activeBlessingKey,
                activeBlessingExpiresAt = flags.activeBlessingExpiresAt,
                activeBlessingXpPct     = run {
                    val b = ChurchRepository.activeBlessing(flags) ?: return@run 0
                    if (b.type == BlessingType.XP) ((b.magnitude - 1f) * 100 + 0.5f).toInt() else 0
                },
                skillPrestige           = flags.skillPrestige,
                seasonalBanners         = buildSeasonalBannerDisplays(flags),
                isLoading   = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * One row per event ever known to the game (from [GameDataRepository.seasonalEvents]) plus any
     * already-earned banner whose event data has since been removed. Earned banners use their frozen
     * snapshot (so they still render after the event is gone); events not yet earned show as locked.
     */
    private fun buildSeasonalBannerDisplays(flags: PlayerFlags): List<SeasonalBannerDisplay> {
        val earnedById = flags.seasonalBannersEarned.associateBy { it.eventId }
        val yearFormat = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
        val allIds = gameData.seasonalEvents.keys + earnedById.keys
        return allIds.distinct().mapNotNull { id ->
            val earned = earnedById[id]
            val event = gameData.seasonalEvents[id]
            val label = when {
                event  != null -> "${event.displayName} ${yearFormat.format(java.util.Date(event.startMs))}"
                earned != null -> earned.displayText
                else            -> return@mapNotNull null
            }
            SeasonalBannerDisplay(
                eventId    = id,
                label      = label,
                bannerIcon = earned?.bannerIcon ?: event?.bannerIcon,
                earned     = earned != null,
                earnedAtMs = earned?.completedAtMs,
            )
        }.sortedByDescending { gameData.seasonalEvents[it.eventId]?.startMs ?: it.earnedAtMs ?: 0L }
    }

    // ------------------------------------------------------------------

    fun openSlotPicker(slot: String) = _extra.update { it.copy(pickingSlot = slot) }
    fun dismissSlotPicker()          = _extra.update { it.copy(pickingSlot = null) }

    fun equip(itemKey: String, slot: String) {
        viewModelScope.launch {
            val itemData     = gameData.equipment[itemKey]
            val requirements = itemData?.requirements ?: emptyMap()
            val skillLevels  = uiState.value.skillLevels
            val unmetReqs = requirements.entries.filter { (skill, lvl) -> (skillLevels[skill] ?: 1) < lvl }
            if (unmetReqs.isNotEmpty()) {
                val msg = unmetReqs.joinToString(", ") { (skill, lvl) ->
                    "${skill.replaceFirstChar { c -> c.uppercase() }} $lvl"
                }
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.inventory_requires_to_equip, msg)) }
                return@launch
            }
            val current = playerRepo.getEquipped().toMutableMap()
            current[slot] = itemKey
            if (slot in EquipSlot.WEAPON_SLOTS && itemData?.twoHanded == true) {
                current[EquipSlot.SHIELD] = null
            } else if (slot == EquipSlot.SHIELD) {
                for (weaponSlot in EquipSlot.WEAPON_SLOTS) {
                    val equipped = current[weaponSlot]
                    if (equipped != null && gameData.equipment[equipped]?.twoHanded == true) {
                        current[weaponSlot] = null
                    }
                }
            }
            playerRepo.updateEquipped(current)
            _extra.update { it.copy(pickingSlot = null) }
        }
    }

    fun unequip(slot: String) {
        viewModelScope.launch {
            val current = playerRepo.getEquipped().toMutableMap()
            current[slot] = null
            playerRepo.updateEquipped(current)
        }
    }

    fun equipBestGear() {
        viewModelScope.launch {
            val state = uiState.value
            val equipment = allEquipment
            val newEquipped = playerRepo.getEquipped().toMutableMap()

            val skillLevels = state.skillLevels
            for (slot in EquipSlot.ALL) {
                val style = EquipSlot.combatStyleForSlot(slot)
                val best = state.inventory.keys
                    .mapNotNull { equipment[it] }
                    .filter { item ->
                        if (style != null) item.slot == EquipSlot.WEAPON && item.combatStyle == style
                        else item.slot == slot
                    }
                    .filter { item -> item.requirements.all { (skill, lvl) -> (skillLevels[skill] ?: 1) >= lvl } }
                    .maxByOrNull { item ->
                        when (slot) {
                            EquipSlot.PICKAXE     -> item.miningEfficiency ?: 0f
                            EquipSlot.AXE         -> item.woodcuttingEfficiency ?: 0f
                            EquipSlot.FISHING_ROD -> item.fishingEfficiency ?: 0f
                            EquipSlot.HOE         -> item.farmingEfficiency ?: 0f
                            else -> if (slot in EquipSlot.WEAPON_SLOTS)
                            item.attackBonus * 1.5f + item.strengthBonus * 1.0f + item.defenseBonus * 0.5f
                        else
                            item.defenseBonus * 2.0f + item.attackBonus * 1.0f + item.strengthBonus * 0.5f
                        }
                    }

                val currentItemKey = newEquipped[slot]
                val currentItemValid = currentItemKey == null || run {
                    val it = equipment[currentItemKey]
                    it != null && it.requirements.all { (skill, lvl) -> (skillLevels[skill] ?: 1) >= lvl }
                }
                when {
                    best != null -> newEquipped[slot] = best.name
                    !currentItemValid -> newEquipped[slot] = null
                }
            }

            playerRepo.updateEquipped(newEquipped)
        }
    }

    fun equipFood(itemKey: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(equippedFood = flags.equippedFood + (itemKey to 1)))
        }
    }

    fun unequipFood(itemKey: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(equippedFood = flags.equippedFood - itemKey))
        }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race) }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    val allEquipment: Map<String, EquipmentData> get() = gameData.equipment
    val allPets: Map<String, PetData> get() = gameData.pets
    val cookingRecipes: Map<String, CookingRecipe> get() = gameData.cookingRecipes
    val foodHealValues: Map<String, Int> get() = gameData.foodHealValues
    val allSkillingDungeons: Map<String, SkillingDungeonData> get() = gameData.skillingDungeons
    val smithingRecipes: Map<String, SmithingRecipe> get() = gameData.smithingRecipes
    val fletchingRecipes: Map<String, FletchingRecipe> get() = gameData.fletchingRecipes
    val craftingRecipes: Map<String, CraftingRecipe> get() = gameData.craftingRecipes
    val herbloreRecipes: Map<String, HerbloreRecipe> get() = gameData.herbloreRecipes
    val ores: Map<String, OreData> get() = gameData.ores
    val trees: Map<String, TreeData> get() = gameData.trees
    val fish: Map<String, FishData> get() = gameData.fish
    val logs: Map<String, LogData> get() = gameData.logs
    val runes: Map<String, RuneData> get() = gameData.runes
    val crops: Map<String, CropData> get() = gameData.crops
    val tradeRoutes: List<TradeRouteData> get() = gameData.tradeRoutes
    val slayerTaskData: Map<String, SlayerTaskData> get() = gameData.slayerTasks
    val bones: Map<String, BoneData> get() = gameData.bones
    val agilityCourses: Map<String, AgilityCourseData> get() = gameData.agilityCourses
    val thievingNpcs: Map<String, ThievingNpcData> get() = gameData.thievingNpcs
    val constructionRecipes: Map<String, ConstructionRecipe> get() = gameData.constructionRecipes

    fun categoryFor(key: String): InventoryCategory {
        val equip = gameData.equipment[key]
        if (equip != null) return when (equip.slot) {
            EquipSlot.WEAPON                                                         -> InventoryCategory.WEAPONS
            EquipSlot.PICKAXE, EquipSlot.AXE, EquipSlot.FISHING_ROD, EquipSlot.HOE -> InventoryCategory.TOOLS
            else                                                                     -> InventoryCategory.ARMOUR
        }
        if (key in gameData.foodHealValues)  return InventoryCategory.FOOD
        if (key in gameData.potionEffects)   return InventoryCategory.POTIONS
        if (key.endsWith("_arrow"))          return InventoryCategory.AMMUNITION
        if (key in gameData.ores || key in gameData.gems || key.endsWith("_bar"))
                                             return InventoryCategory.ORES
        if (key in constructionItemKeys)     return InventoryCategory.CONSTRUCTION
        if (key.startsWith("raw_"))          return InventoryCategory.RAW_FOOD
        if (key.endsWith("_seed") || key in cropProduceKeys)
                                             return InventoryCategory.SEEDS
        if (key in gameData.logs || key in gameData.bones || key in gameData.runes ||
            key == "rune_essence" || key.endsWith("_ashes") || key == "ashes" ||
            key.endsWith("_herb")
        ) return InventoryCategory.MATERIALS
        return InventoryCategory.OTHER
    }

    private val constructionItemKeys: Set<String> by lazy {
        val keys = mutableSetOf<String>()
        gameData.constructionRecipes.forEach { (outputKey, recipe) ->
            keys.add(outputKey)
            keys.addAll(recipe.materials.keys)
        }
        keys
    }

    private val cropProduceKeys: Set<String> by lazy {
        gameData.crops.keys.toSet()
    }
}

enum class InventoryCategory {
    WEAPONS, ARMOUR, TOOLS, FOOD, RAW_FOOD, POTIONS, AMMUNITION, ORES, CONSTRUCTION, SEEDS, MATERIALS, OTHER
}

/** Ordered list of all skills for display (gathering → crafting → combat). */
val DISPLAY_SKILL_ORDER = Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.COMBAT

/** Human-readable label for an equip slot key. */
fun slotDisplayName(context: Context, slot: String): String =
    GameStrings.slotName(context, slot)
