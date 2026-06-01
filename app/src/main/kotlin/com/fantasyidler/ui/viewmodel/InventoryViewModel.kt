package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
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

@HiltViewModel
class InventoryViewModel @Inject constructor(
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
                isLoading   = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    // ------------------------------------------------------------------

    fun openSlotPicker(slot: String) = _extra.update { it.copy(pickingSlot = slot) }
    fun dismissSlotPicker()          = _extra.update { it.copy(pickingSlot = null) }

    fun equip(itemKey: String, slot: String) {
        viewModelScope.launch {
            val requirements = gameData.equipment[itemKey]?.requirements ?: emptyMap()
            val skillLevels  = uiState.value.skillLevels
            val unmet = requirements.entries.firstOrNull { (skill, lvl) -> (skillLevels[skill] ?: 1) < lvl }
            if (unmet != null) {
                val (skill, lvl) = unmet
                _extra.update {
                    it.copy(snackbarMessage = "Requires ${skill.replaceFirstChar { c -> c.uppercase() }} $lvl to equip.")
                }
                return@launch
            }
            val current = playerRepo.getEquipped().toMutableMap()
            current[slot] = itemKey
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
                            else -> (item.attackBonus + item.strengthBonus + item.defenseBonus).toFloat()
                        }
                    }

                if (best != null) newEquipped[slot] = best.name
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

    fun categoryFor(key: String): InventoryCategory {
        val equip = gameData.equipment[key]
        if (equip != null) return when (equip.slot) {
            EquipSlot.WEAPON                                                         -> InventoryCategory.WEAPONS
            EquipSlot.PICKAXE, EquipSlot.AXE, EquipSlot.FISHING_ROD, EquipSlot.HOE -> InventoryCategory.TOOLS
            else                                                                     -> InventoryCategory.ARMOUR
        }
        if (key in gameData.foodHealValues) return InventoryCategory.FOOD
        if (key in gameData.potionEffects)  return InventoryCategory.POTIONS
        if (key in gameData.ores || key in gameData.gems || key in gameData.logs ||
            key in gameData.bones || key in gameData.runes || key == "rune_essence" ||
            key.endsWith("_bar") || key.endsWith("_arrow") || key.startsWith("raw_") ||
            key.endsWith("_ashes") || key == "ashes" || key.endsWith("_herb") || key.endsWith("_seed")
        ) return InventoryCategory.MATERIALS
        return InventoryCategory.OTHER
    }
}

enum class InventoryCategory {
    WEAPONS, ARMOUR, TOOLS, FOOD, POTIONS, MATERIALS, OTHER
}

/** Ordered list of all skills for display (gathering → crafting → combat). */
val DISPLAY_SKILL_ORDER = Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.COMBAT

/** Human-readable label for an equip slot key. */
fun slotDisplayName(slot: String): String = when (slot) {
    EquipSlot.WEAPON_ATK    -> "Weapon (Atk)"
    EquipSlot.WEAPON_STR    -> "Weapon (Str)"
    EquipSlot.WEAPON_RANGED -> "Weapon (Ranged)"
    EquipSlot.WEAPON_MAGIC  -> "Weapon (Magic)"
    EquipSlot.HEAD        -> "Head"
    EquipSlot.BODY        -> "Body"
    EquipSlot.LEGS        -> "Legs"
    EquipSlot.BOOTS       -> "Boots"
    EquipSlot.CAPE        -> "Cape"
    EquipSlot.RING        -> "Ring"
    EquipSlot.NECKLACE    -> "Necklace"
    EquipSlot.SHIELD      -> "Shield"
    EquipSlot.PICKAXE     -> "Pickaxe"
    EquipSlot.AXE         -> "Axe"
    EquipSlot.FISHING_ROD -> "Fishing Rod"
    else                  -> slot.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
