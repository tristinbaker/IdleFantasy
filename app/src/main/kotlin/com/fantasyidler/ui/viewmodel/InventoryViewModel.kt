package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.EquipSlot
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
    ) {
        val totalLevel: Int get() = skillLevels.values.sum()

        /** Items in inventory that can go into [pickingSlot]. */
        fun candidatesFor(slot: String, allEquipment: Map<String, EquipmentData>): List<EquipmentData> =
            inventory.keys.mapNotNull { allEquipment[it] }.filter { it.slot == slot }
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
            extra.copy(
                coins       = player.coins,
                inventory   = inventory.entries
                    .sortedByDescending { it.value }
                    .associate { it.key to it.value },
                skillLevels = json.decodeFromString(player.skillLevels),
                skillXp     = json.decodeFromString(player.skillXp),
                equipped    = json.decodeFromString(player.equipped),
                ownedPetIds = pets.map { it.id }.toSet(),
                isLoading   = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    // ------------------------------------------------------------------

    fun openSlotPicker(slot: String) = _extra.update { it.copy(pickingSlot = slot) }
    fun dismissSlotPicker()          = _extra.update { it.copy(pickingSlot = null) }

    fun equip(itemKey: String, slot: String) {
        viewModelScope.launch {
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

            for (slot in EquipSlot.ALL) {
                val best = state.inventory.keys
                    .mapNotNull { equipment[it] }
                    .filter { it.slot == slot }
                    .maxByOrNull { item ->
                        when (slot) {
                            EquipSlot.PICKAXE     -> item.miningEfficiency ?: 0f
                            EquipSlot.AXE         -> item.woodcuttingEfficiency ?: 0f
                            EquipSlot.FISHING_ROD -> item.fishingEfficiency ?: 0f
                            else -> (item.attackBonus + item.strengthBonus + item.defenseBonus).toFloat()
                        }
                    }

                if (best != null) newEquipped[slot] = best.name
            }

            playerRepo.updateEquipped(newEquipped)
        }
    }

    val allEquipment: Map<String, EquipmentData> get() = gameData.equipment
    val allPets: Map<String, PetData> get() = gameData.pets
}

/** Ordered list of all skills for display (gathering → crafting → combat). */
val DISPLAY_SKILL_ORDER = Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.COMBAT

/** Human-readable label for an equip slot key. */
fun slotDisplayName(slot: String): String = when (slot) {
    EquipSlot.WEAPON      -> "Weapon"
    EquipSlot.HEAD        -> "Head"
    EquipSlot.BODY        -> "Body"
    EquipSlot.LEGS        -> "Legs"
    EquipSlot.CAPE        -> "Cape"
    EquipSlot.RING        -> "Ring"
    EquipSlot.NECKLACE    -> "Necklace"
    EquipSlot.SHIELD      -> "Shield"
    EquipSlot.PICKAXE     -> "Pickaxe"
    EquipSlot.AXE         -> "Axe"
    EquipSlot.FISHING_ROD -> "Fishing Rod"
    else                  -> slot.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
