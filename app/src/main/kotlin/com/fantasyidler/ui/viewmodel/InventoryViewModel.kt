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
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.TitleRepository
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.withAppLocale
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
import com.fantasyidler.repository.resolveCapeMultiplier
import dagger.hilt.android.qualifiers.ApplicationContext

/** One row on the Profile Banners tab — either an earned banner or a locked placeholder for a known event. */
data class SeasonalBannerDisplay(
    val eventId: String,
    val label: String,
    val bannerIcon: String?,
    val earned: Boolean,
    val earnedAtMs: Long?,
    /** Short event name (e.g. "Sunspire Solstice"), used to build this event's Title. */
    val titleName: String,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val titleRepo: TitleRepository,
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
        val characterSkinTone: Int = 1,
        val characterHairStyle: Int = 1,
        val characterHairColor: String = "a",
        val characterEyeStyle: Int = 1,
        val characterBeardStyle: Int = 0,
        val characterBeardColor: String = "a",
        val snackbarMessage: String? = null,
        val skillingDungeonNotes: Map<String, Int> = emptyMap(),
        val unlockedDungeons: List<String> = emptyList(),
        val xpBoostExpiresAt: Long = 0L,
        val ironman: Boolean = false,
        val activeBlessingKey: String = "",
        val activeBlessingExpiresAt: Long = 0L,
        val prayerCapeMult: Float = 1f,
        val activeBlessingXpPct: Int = 0,
        val towerXpBonusPct: Int = 0,
        val towerCoinBonusPct: Int = 0,
        val towerHpBonus: Int = 0,
        val skillPrestige: Map<String, Int> = emptyMap(),
        val townBuildingTiers: Map<String, Int> = emptyMap(),
        val seasonalBanners: List<SeasonalBannerDisplay> = emptyList(),
        val unlockedTitles: Set<String> = emptySet(),
        val equippedTitle: String? = null,
        val displayName: String = "",
        /** The weapon slot driving the currently active combat style. */
        val activeWeaponSlot: String? = null,
        /** Global "start eating" threshold as % of max HP. */
        val foodEatThresholdPct: Int = 50,
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
        viewModelScope.launch { titleRepo.syncUnlockedTitles() }
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
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val equippedCape = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
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
                characterSkinTone     = flags.characterSkinTone,
                characterHairStyle    = flags.characterHairStyle,
                characterHairColor    = flags.characterHairColor,
                characterEyeStyle     = flags.characterEyeStyle,
                characterBeardStyle   = flags.characterBeardStyle,
                characterBeardColor   = flags.characterBeardColor,
                skillingDungeonNotes  = flags.skillingDungeonNotes,
                unlockedDungeons      = flags.unlockedDungeons,
                xpBoostExpiresAt        = flags.xpBoostExpiresAt,
                ironman                 = flags.ironman,
                activeBlessingKey       = flags.activeBlessingKey,
                activeBlessingExpiresAt = flags.activeBlessingExpiresAt,
                activeBlessingXpPct     = run {
                    val b = ChurchRepository.activeBlessing(flags) ?: return@run 0
                    if (b.type == BlessingType.XP) {
                        val churchMult = ChurchRepository.xpMultiplier(flags, equipped, inventory.keys, gameData.equipment)
                        ((churchMult - 1f) * 100 + 0.5f).toInt()
                    } else 0
                },
                prayerCapeMult = resolveCapeMultiplier(Skills.PRAYER, equippedCape, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment, flags.ironman),
                towerXpBonusPct         = flags.towerXpBonusPct,
                towerCoinBonusPct       = flags.towerCoinBonusPct,
                towerHpBonus            = flags.towerHpBonus,
                skillPrestige           = flags.skillPrestige,
                townBuildingTiers       = flags.townBuildingTiers,
                seasonalBanners         = buildSeasonalBannerDisplays(flags),
                unlockedTitles          = flags.unlockedTitles,
                equippedTitle           = flags.equippedTitle,
                activeWeaponSlot        = flags.activeWeaponSlot,
                foodEatThresholdPct     = flags.foodEatThresholdPct,
                displayName             = run {
                    val baseName = flags.characterName.ifBlank { context.withAppLocale().getString(R.string.profile_unnamed) }
                    val titleName = titleRepo.displayName(context, flags.equippedTitle, flags)
                    if (titleName != null) context.withAppLocale().getString(R.string.character_name_with_title, baseName, titleName) else baseName
                },
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
            val eventName = event?.let { GameStrings.seasonalEventName(context, id, it.displayName) }
            val label = when {
                eventName != null -> "$eventName ${yearFormat.format(java.util.Date(event.startMs))}"
                earned    != null -> earned.displayText
                else              -> return@mapNotNull null
            }
            SeasonalBannerDisplay(
                eventId    = id,
                label      = label,
                bannerIcon = earned?.bannerIcon ?: event?.bannerIcon,
                earned     = earned != null,
                earnedAtMs = earned?.completedAtMs,
                titleName  = eventName ?: earned?.eventDisplayName?.ifBlank { null } ?: label,
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
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.inventory_requires_to_equip, msg)) }
                return@launch
            }
            val before = playerRepo.getEquipped()
            val current = before.toMutableMap()
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
            recordArmorLoadoutChanges(before, current)
            _extra.update { it.copy(pickingSlot = null) }
        }
    }

    fun unequip(slot: String) {
        viewModelScope.launch {
            val before = playerRepo.getEquipped()
            val current = before.toMutableMap()
            current[slot] = null
            playerRepo.updateEquipped(current)
            recordArmorLoadoutChanges(before, current)
        }
    }

    /** The combat style whichever weapon slot is currently active belongs to; falls back to "attack". */
    private fun resolveActiveStyle(flags: PlayerFlags, equipped: Map<String, String?>): String {
        val slot = flags.activeWeaponSlot
            ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
            ?: EquipSlot.WEAPON_ATK
        return EquipSlot.combatStyleForSlot(slot) ?: "attack"
    }

    /**
     * Auto-records any ARMOR_SLOTS that changed between [before] and [after] into the currently
     * active style's remembered loadout — mirrors how each style already remembers its own weapon
     * slot, so casually equipping gear while playing a style is all it takes to "save" it (no
     * separate save step needed). Also catches the two-handed/shield auto-unequip side effect.
     */
    private suspend fun recordArmorLoadoutChanges(before: Map<String, String?>, after: Map<String, String?>) {
        val changedArmorSlots = EquipSlot.ARMOR_SLOTS.filter { before[it] != after[it] }
        if (changedArmorSlots.isEmpty()) return
        val flags = playerRepo.getFlags()
        val style = resolveActiveStyle(flags, after)
        // Full snapshot, not just the changed slots: sparse loadouts left unrecorded slots
        // inheriting whatever the previous style displayed, so the shown composition
        // depended on the tab path taken (issue #1224).
        val updatedStyle = EquipSlot.ARMOR_SLOTS.associateWith { after[it] }
        playerRepo.updateFlags(flags.copy(armorLoadouts = flags.armorLoadouts + (style to updatedStyle)))
    }

    private fun bestItemForSlot(
        slot: String,
        skillLevels: Map<String, Int>,
        inventory: Map<String, Int>,
        equipment: Map<String, EquipmentData>,
        activeStyle: String,
    ): EquipmentData? {
        val style = EquipSlot.combatStyleForSlot(slot)
        return inventory.entries
            .filter { (_, qty) -> qty > 0 }
            .mapNotNull { (key, _) -> equipment[key] }
            .filter { item ->
                if (style != null) item.slot == EquipSlot.WEAPON && item.combatStyle == style
                else item.slot == slot
            }
            .filter { item -> item.requirements.all { (skill, lvl) -> (skillLevels[skill] ?: 1) >= lvl } }
            .maxByOrNull { slotScore(it, slot, activeStyle) }
    }

    private fun slotScore(item: EquipmentData, slot: String, activeStyle: String): Float {
        val style = EquipSlot.combatStyleForSlot(slot)
        return when (slot) {
            EquipSlot.PICKAXE        -> item.miningEfficiency ?: 0f
            EquipSlot.AXE            -> item.woodcuttingEfficiency ?: 0f
            EquipSlot.FISHING_ROD    -> item.fishingEfficiency ?: 0f
            EquipSlot.HOE            -> item.farmingEfficiency ?: 0f
            EquipSlot.HAMMER         -> item.smithingEfficiency ?: 0f
            EquipSlot.TINDERBOX      -> item.firemakingEfficiency ?: 0f
            EquipSlot.GRAPPLING_HOOK -> item.agilityEfficiency ?: 0f
            EquipSlot.FRYING_PAN     -> item.cookingEfficiency ?: 0f
            EquipSlot.LOCKPICK       -> item.thievingEfficiency ?: 0f
            else -> if (slot in EquipSlot.WEAPON_SLOTS) {
                // Score by the slot's style with the same stat fallbacks the
                // simulator uses, then scale by attacks-per-second: a faster
                // weapon lands proportionally more hits (issue #1222).
                val base = when (style) {
                    "ranged"   -> (item.rangedStrengthBonus ?: 0) * 1.5f + (item.rangedAttackBonus ?: item.attackBonus) * 1.0f + item.defenseBonus * 0.5f
                    "magic"    -> (item.magicDamageBonus ?: 0) * 1.5f + (item.magicAttackBonus ?: 0) * 1.0f + item.defenseBonus * 0.5f
                    "strength" -> item.strengthBonus * 1.5f + item.attackBonus * 1.0f + item.defenseBonus * 0.5f
                    else       -> item.attackBonus * 1.5f + item.strengthBonus * 1.0f + item.defenseBonus * 0.5f
                }
                val speed = (item.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC)
                    .coerceIn(1.2, CombatSimulator.BASE_ATTACK_SPEED_SEC)
                base * (CombatSimulator.BASE_ATTACK_SPEED_SEC / speed).toFloat()
            }
            else when (activeStyle) {
                // Accuracy has diminishing returns once effective attack exceeds enemy
                // defense (see CombatSimulator's hit-chance curve), while damage bonus
                // adds to max hit linearly with no diminishing returns -- so the damage
                // stat outweighs the accuracy stat here (issue #1198). For the strength
                // style the accuracy stat is weighted below the damage gap too, so e.g.
                // +8 atk cannot outscore +3 str and +2 def (issues #1180, #1230). The
                // attack style keeps accuracy primary: that split reflects the player's
                // weapon-style choice, not this accuracy-vs-damage tradeoff.
                "ranged"   -> (item.rangedStrengthBonus ?: 0) * 1.5f + (item.rangedAttackBonus ?: 0) * 1.0f + item.defenseBonus * 0.5f
                "magic"    -> (item.magicDamageBonus ?: 0) * 1.5f + (item.magicAttackBonus ?: 0) * 1.0f + item.defenseBonus * 0.5f
                "strength" -> item.strengthBonus * 1.5f + item.attackBonus * 0.5f + item.defenseBonus * 0.5f
                else       -> item.attackBonus * 1.5f + item.strengthBonus * 1.0f + item.defenseBonus * 0.5f
            }
        }
    }

    private fun equipBestForSlots(slots: List<String>) {
        viewModelScope.launch {
            val state = uiState.value
            val equipment = allEquipment
            val before = playerRepo.getEquipped()
            val newEquipped = before.toMutableMap()
            val skillLevels = state.skillLevels
            val activeStyle = resolveActiveStyle(playerRepo.getFlags(), before)

            for (slot in slots) {
                val best = bestItemForSlot(slot, skillLevels, state.inventory, equipment, activeStyle)
                val currentItemKey = newEquipped[slot]
                val currentItemValid = currentItemKey == null || run {
                    val it = equipment[currentItemKey]
                    it != null && it.requirements.all { (skill, lvl) -> (skillLevels[skill] ?: 1) >= lvl }
                }
                when {
                    best != null -> {
                        // On a stat tie, keep what's equipped: swapping to the identically
                        // scored alternative would override a cosmetic choice (greaves vs skirt).
                        val current = currentItemKey?.let { equipment[it] }
                        val keepCurrent = current != null && currentItemValid &&
                            slotScore(current, slot, activeStyle) >= slotScore(best, slot, activeStyle)
                        if (!keepCurrent) newEquipped[slot] = best.name
                    }
                    !currentItemValid -> newEquipped[slot] = null
                }
            }

            playerRepo.updateEquipped(newEquipped)
            recordArmorLoadoutChanges(before, newEquipped)
        }
    }

    fun equipBestGear() = equipBestForSlots(EquipSlot.ALL)

    fun equipBestTools() = equipBestForSlots(EquipSlot.TOOL_SLOTS)

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

    fun setFoodEatThresholdPct(pct: Int) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(foodEatThresholdPct = pct.coerceIn(10, 90)))
        }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race) }
    }

    fun saveAppearance(
        skinTone: Int,
        hairStyle: Int,
        hairColor: String,
        eyeStyle: Int,
        beardStyle: Int,
        beardColor: String,
        race: String,
    ) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(
                characterSkinTone   = skinTone,
                characterHairStyle  = hairStyle,
                characterHairColor  = hairColor,
                characterEyeStyle   = eyeStyle,
                characterBeardStyle = beardStyle,
                characterBeardColor = beardColor,
                characterRace       = race,
            ))
        }
    }

    fun equipTitle(id: String?) {
        viewModelScope.launch { titleRepo.equipTitle(id) }
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
            EquipSlot.WEAPON -> InventoryCategory.WEAPONS
            EquipSlot.PICKAXE, EquipSlot.AXE, EquipSlot.FISHING_ROD, EquipSlot.HOE,
            EquipSlot.HAMMER, EquipSlot.TINDERBOX, EquipSlot.GRAPPLING_HOOK, EquipSlot.FRYING_PAN,
            EquipSlot.LOCKPICK -> InventoryCategory.TOOLS
            else -> InventoryCategory.ARMOUR
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

    fun debugAddXp(skill: String, xp: Long) {
        viewModelScope.launch {
            val key = skill.trim()
            if (key.isEmpty() || xp <= 0) return@launch

            if (key !in Skills.ALL) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            playerRepo.applySessionResults(key, xp, emptyMap())
            val name = GameStrings.skillName(context.withAppLocale(), key)
            _extra.update { it.copy(snackbarMessage = "$xp XP added to $name") }
        }
    }

    fun debugSetXp(skill: String, xp: Long) {
        viewModelScope.launch {
            val key = skill.trim()
            if (key.isEmpty() || xp <= 0) return@launch

            if (key !in Skills.ALL) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            val currXp = playerRepo.getSkillXp()[key] ?: 0L
            if (currXp > xp) {
                playerRepo.deductSkillXp(key, currXp - xp)
            } else {
                playerRepo.debugAddSkillXp(key, xp - currXp)
            }
            val name = GameStrings.skillName(context.withAppLocale(), key)
            _extra.update { it.copy(snackbarMessage = "Set $name XP to $xp") }
        }
    }

    fun debugRemoveXp(skill: String, xp: Long) {
        viewModelScope.launch {
            val key = skill.trim()
            if (key.isEmpty() || xp <= 0) return@launch

            if (key !in Skills.ALL) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            playerRepo.deductSkillXp(key, xp)
            val name = GameStrings.skillName(context.withAppLocale(), key)
            _extra.update { it.copy(snackbarMessage = "Removed up to $xp XP from $name") }
        }
    }

    fun debugAddItem(itemId: String, amount: Int) {
        viewModelScope.launch {
            val key = itemId.trim()
            if (key.isEmpty() || amount <= 0) return@launch

            if (!isKnownItemId(key)) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            playerRepo.addItem(key, amount)
            val name = GameStrings.itemName(context.withAppLocale(), key)
            _extra.update { it.copy(snackbarMessage = "$amount $name added") }
        }
    }

    fun debugSetItem(itemId: String, amount: Int) {
        viewModelScope.launch {
            val key = itemId.trim()
            if (key.isEmpty() || amount <= 0) return@launch

            if (!isKnownItemId(key)) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            val currAmount = playerRepo.getInventory()[itemId] ?: 0
            if (currAmount > amount) {
                playerRepo.sellItem(key, currAmount - amount, 0)
            } else {
                playerRepo.addItem(key, amount - currAmount)
            }
            val name = GameStrings.itemName(context.withAppLocale(), key)
            _extra.update { it.copy(snackbarMessage = "Set $name to $amount") }
        }
    }

    fun debugRemoveItem(itemId: String, amount: Int) {
        viewModelScope.launch {
            val key = itemId.trim()
            if (key.isEmpty() || amount <= 0) return@launch

            if (!isKnownItemId(key)) {
                _extra.update { it.copy(snackbarMessage = "$key not found") }
                return@launch
            }

            if (playerRepo.sellItem(key, amount, 0)) {
                val name = GameStrings.itemName(context.withAppLocale(), key)
                _extra.update { it.copy(snackbarMessage = "$amount $name removed") }
            } else {
                val name = GameStrings.itemName(context.withAppLocale(), key)
                _extra.update { it.copy(snackbarMessage = "Not enough $name to remove $amount items") }
            }
        }
    }

    private fun isKnownItemId(itemId: String): Boolean {
        val localeCtx = context.withAppLocale()
        return localeCtx.stringByName("item_${itemId}_name") != null ||
            localeCtx.stringByName("crop_${itemId}_name") != null ||
            itemId in gameData.usefulItemKeys
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
