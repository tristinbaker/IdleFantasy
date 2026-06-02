package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class CombatSessionResult(
    val dungeonDisplayName: String,
    val xpPerSkill: Map<String, Long>,
    val itemsGained: Map<String, Int>,
    val coinsGained: Long,
    val won: Boolean = true,
    val killsByEnemy: Map<String, Int> = emptyMap(),
    val foodConsumed: Map<String, Int> = emptyMap(),
    val xpBlessingBonusBySkill: Map<String, Long> = emptyMap(),
    val coinBlessingBonus: Long = 0L,
    val boostWasActive: Boolean = false,
)

data class CombatUiState(
    val isLoading: Boolean = true,
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val equipped: Map<String, String?> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
    val equippedWeapon: EquipmentData? = null,
    /** All four weapon slots that have something equipped: slot key -> EquipmentData. */
    val equippedWeapons: Map<String, EquipmentData> = emptyMap(),
    /** The weapon slot the player explicitly chose for the next dungeon; null = use default. */
    val selectedWeaponSlot: String? = null,
    val selectedSpell: SpellData? = null,
    val selectedArrowKey: String? = null,
    val combatSession: SkillSession? = null,
    val selectedDungeon: DungeonData? = null,
    val selectedBoss: BossData? = null,
    val startingSession: Boolean = false,
    val snackbarMessage: String? = null,
    val combatResult: CombatSessionResult? = null,
    val totalAttackBonus: Int = 0,
    val totalStrengthBonus: Int = 0,
    val totalDefenseBonus: Int = 0,
    val dungeonSurvivalRatings: Map<String, CombatSimulator.SurvivalRating> = emptyMap(),
    val noFoodWarningPending: Boolean = false,
    val pendingDungeonKey: String? = null,
    val equippedFood: Map<String, Int> = emptyMap(),
    val selectedPotionKey: String? = null,
    val availablePotions: Map<String, Int> = emptyMap(),
    val dungeonRuns: Map<String, Int> = emptyMap(),
    val unlockedDungeons: List<String> = emptyList(),
    val skillPrestige: Map<String, Int> = emptyMap(),
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class CombatViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val slayerRepo: SlayerRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    val potionEffects: Map<String, Map<String, Int>> = gameData.potionEffects

    private val _extra = MutableStateFlow(CombatUiState())

    val uiState: StateFlow<CombatUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, session, extra ->
        val combatSession = session?.takeIf { it.skillName == "combat" || it.skillName == "boss" }
        if (player == null) {
            extra.copy(combatSession = combatSession)
        } else {
            val levels:   Map<String, Int>    = json.decodeFromString(player.skillLevels)
            val xpMap:    Map<String, Long>   = json.decodeFromString(player.skillXp)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
            val flags: PlayerFlags         = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            val activeWeaponSlot = extra.selectedWeaponSlot
                ?: flags.activeWeaponSlot
                ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                ?: EquipSlot.WEAPON
            val weaponKey      = equipped[activeWeaponSlot]
            val equippedWeapon = weaponKey?.let { gameData.equipment[it] }
            val equippedWeapons = EquipSlot.WEAPON_SLOTS
                .mapNotNull { slot -> equipped[slot]?.let { key -> gameData.equipment[key]?.let { slot to it } } }
                .toMap()
            val armorAtk = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 }
            val armorStr = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
            val armorDef = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 }
            val totalAtk = armorAtk + (equippedWeapon?.attackBonus  ?: 0)
            val totalStr = armorStr + (equippedWeapon?.strengthBonus ?: 0)
            val totalDef = armorDef + (equippedWeapon?.defenseBonus  ?: 0)
            val defenceLevel  = levels[Skills.DEFENSE]   ?: 1
            val hpLevel       = levels[Skills.HITPOINTS] ?: 1
            val totalFoodHeal = flags.equippedFood.keys.sumOf { key ->
                (inventory[key] ?: 0) * (gameData.foodHealValues[key] ?: 0)
            }
            val survivalRatings = gameData.dungeons.mapValues { (_, dungeon) ->
                CombatSimulator.estimateSurvival(
                    dungeon       = dungeon,
                    enemies       = gameData.enemies,
                    playerDefence = defenceLevel + totalDef,
                    playerHp      = hpLevel,
                    totalFoodHeal = totalFoodHeal,
                )
            }
            extra.copy(
                isLoading               = false,
                skillLevels             = levels,
                skillXp                 = xpMap,
                equipped                = equipped,
                inventory               = inventory,
                equippedWeapon          = equippedWeapon,
                equippedWeapons         = equippedWeapons,
                combatSession           = combatSession,
                totalAttackBonus        = totalAtk,
                totalStrengthBonus      = totalStr,
                totalDefenseBonus       = totalDef,
                dungeonSurvivalRatings  = survivalRatings,
                equippedFood            = flags.equippedFood.keys
                    .associateWith { inventory[it] ?: 0 }
                    .filter { (_, qty) -> qty > 0 },
                availablePotions        = inventory.filterKeys { it in gameData.potionEffects }
                    .filter { (_, qty) -> qty > 0 },
                dungeonRuns             = flags.dungeonRuns,
                unlockedDungeons        = flags.unlockedDungeons,
                selectedArrowKey        = if (extra.selectedArrowKey == null) flags.equippedArrows else extra.selectedArrowKey,
                skillPrestige           = flags.skillPrestige,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CombatUiState())

    val dungeonList: List<DungeonData> by lazy {
        gameData.dungeons.values.sortedBy { it.recommendedLevel }
    }

    val bossList: List<BossData> by lazy {
        gameData.bosses.values.sortedBy { it.combatLevelRequired }
    }

    val enemyMap: Map<String, EnemyData> by lazy { gameData.enemies }

    val foodHealValues: Map<String, Int> by lazy { gameData.foodHealValues }

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

    // ------------------------------------------------------------------
    // Dungeon selection
    // ------------------------------------------------------------------

    fun selectDungeon(dungeon: DungeonData?) =
        _extra.update { it.copy(selectedDungeon = dungeon) }

    fun selectBoss(boss: BossData?) =
        _extra.update { it.copy(selectedBoss = boss) }

    fun selectWeaponSlot(slot: String) =
        _extra.update { it.copy(selectedWeaponSlot = slot) }

    fun selectSpell(spell: SpellData?) =
        _extra.update { it.copy(selectedSpell = spell) }

    fun selectArrow(key: String?) {
        _extra.update { it.copy(selectedArrowKey = key) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(equippedArrows = key))
        }
    }

    fun selectPotion(key: String?) =
        _extra.update { it.copy(selectedPotionKey = key) }

    /** Returns spells available at the player's current magic level. */
    fun availableSpells(skillLevels: Map<String, Int>): List<SpellData> {
        val magicLevel = skillLevels[Skills.MAGIC] ?: 1
        return gameData.spells.values
            .filter { it.magicLevelRequired <= magicLevel }
            .sortedBy { it.magicLevelRequired }
    }

    // ------------------------------------------------------------------
    // Session start
    // ------------------------------------------------------------------

    fun startDungeonSession(dungeonKey: String, bypassFoodWarning: Boolean = false) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val dungeonName = gameData.dungeons[dungeonKey]?.displayName ?: dungeonKey
                val player      = playerRepo.getOrCreatePlayer()
                val agility     = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val queuedWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val queuedWeapon = equipped[queuedWeaponSlot]?.let { gameData.equipment[it] }
                val queuedSpell = _extra.value.selectedSpell
                if (queuedWeapon != null || queuedSpell != null) {
                    val flags: PlayerFlags = json.decodeFromString(player.flags)
                    playerRepo.updateFlags(flags.copy(
                        activeWeaponSlot = queuedWeaponSlot,
                        activeSpell = if (queuedWeapon?.combatStyle == "magic" && queuedSpell != null) queuedSpell.name else flags.activeSpell,
                    ))
                }
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "combat",
                        activityKey         = dungeonKey,
                        skillDisplayName    = dungeonName,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility),
                    )
                )
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: $dungeonName." else "Queue is full (3/3).",
                        selectedDungeon = null,
                    )
                }
                return@launch
            }

            if (!bypassFoodWarning) {
                val p   = playerRepo.getOrCreatePlayer()
                val f: PlayerFlags      = try { json.decodeFromString(p.flags) } catch (_: Exception) { PlayerFlags() }
                val inv: Map<String, Int> = json.decodeFromString(p.inventory)
                if (f.equippedFood.keys.none { (inv[it] ?: 0) > 0 }) {
                    _extra.update { it.copy(noFoodWarningPending = true, pendingDungeonKey = dungeonKey) }
                    return@launch
                }
            }

            _extra.update { it.copy(startingSession = true, selectedDungeon = null) }
            try {
                val dungeon   = gameData.dungeons[dungeonKey] ?: error("Unknown dungeon: $dungeonKey")
                val player    = playerRepo.getOrCreatePlayer()
                val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)

                val activeWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey  = equipped[activeWeaponSlot]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }

                val totalAttackBonus   = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 } + (weapon?.attackBonus  ?: 0)
                val totalStrengthBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefenseBonus  = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)

                // Ranged: use player's chosen arrow if available, else fall back to best in inventory
                val preferredArrow = _extra.value.selectedArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowStrengthBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0

                // Magic: validate spell selection and level
                val selectedSpell = _extra.value.selectedSpell
                if (combatStyle == "magic") {
                    if (selectedSpell == null) {
                        _extra.update {
                            it.copy(snackbarMessage = "Select a spell before entering.", startingSession = false)
                        }
                        return@launch
                    }
                    val magicLevel = levels[Skills.MAGIC] ?: 1
                    if (magicLevel < selectedSpell.magicLevelRequired) {
                        _extra.update {
                            it.copy(
                                snackbarMessage = "Need Magic ${selectedSpell.magicLevelRequired} for ${selectedSpell.displayName}.",
                                startingSession = false,
                            )
                        }
                        return@launch
                    }
                }

                // Food: use whatever equipped food items exist in inventory
                val flags: PlayerFlags = json.decodeFromString(player.flags)
                playerRepo.updateFlags(flags.copy(
                    activeWeaponSlot = activeWeaponSlot,
                    activeSpell = if (combatStyle == "magic" && selectedSpell != null) selectedSpell.name else flags.activeSpell,
                ))
                val equippedFoodKeys   = flags.equippedFood.keys
                val availableFood      = inventory.filterKeys { it in equippedFoodKeys }
                val foodHealValues     = gameData.foodHealValues

                // Arrows: pass current supply to simulator; consumed at collect time from frames
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()

                // Potion: consume immediately on dungeon start, pass bonuses to simulator
                val potionKey     = _extra.value.selectedPotionKey
                val potionBonuses = if (potionKey != null && (inventory[potionKey] ?: 0) > 0) {
                    playerRepo.consumeItems(mapOf(potionKey to 1))
                    gameData.potionEffects[potionKey] ?: emptyMap()
                } else emptyMap()

                val prestigeMap = flags.skillPrestige
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = (levels[Skills.ATTACK]    ?: 1) + (prestigeMap[Skills.ATTACK]    ?: 0) * 5,
                    playerStrength      = (levels[Skills.STRENGTH]  ?: 1) + (prestigeMap[Skills.STRENGTH]  ?: 0) * 5,
                    playerDefence       = (levels[Skills.DEFENSE]   ?: 1) + totalDefenseBonus + (prestigeMap[Skills.DEFENSE] ?: 0) * 5,
                    blessingDefBonus    = ChurchRepository.defBonus(flags),
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMap[Skills.HITPOINTS] ?: 0) * 5,
                    weaponAttackBonus   = totalAttackBonus,
                    weaponStrengthBonus = totalStrengthBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = (levels[Skills.RANGED]    ?: 1) + (prestigeMap[Skills.RANGED]    ?: 0) * 5,
                    playerMagic         = (levels[Skills.MAGIC]     ?: 1) + (prestigeMap[Skills.MAGIC]     ?: 0) * 5,
                    arrowStrengthBonus  = arrowStrengthBonus,
                    spellMaxHit         = selectedSpell?.maxHit    ?: 0,
                    agilityLevel        = levels[Skills.AGILITY]   ?: 1,
                    petBoostPct         = petBoostFor(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = foodHealValues,
                    potionBonuses       = potionBonuses,
                    availableArrows     = availableArrows,
                )

                val totalAttacks = result.frames.size * CombatSimulator.TICKS_PER_FRAME

                // Consume magic runes: 1 cast per attack attempt (hit or miss)
                if (combatStyle == "magic" && selectedSpell != null && totalAttacks > 0) {
                    val staffCoversRune = weapon?.infiniteRunes == selectedSpell.runeType
                    if (!staffCoversRune) {
                        val runesNeeded = totalAttacks * selectedSpell.runeCost
                        val ok = playerRepo.consumeItems(mapOf(selectedSpell.runeType to runesNeeded))
                        if (!ok) {
                            _extra.update {
                                it.copy(
                                    snackbarMessage = "Not enough ${selectedSpell.displayName.substringBefore(" ")} runes (need $runesNeeded).",
                                    startingSession = false,
                                )
                            }
                            return@launch
                        }
                    }
                }

                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    result.frames,
                )
                val deathFrameIdx = result.frames.indexOfFirst { it.died }
                val alarmOffsetMs = if (deathFrameIdx >= 0) {
                    val perFrameMs = result.durationMs / 60L
                    perFrameMs * (deathFrameIdx + 1)
                } else null
                sessionRepo.startSession(
                    skillName        = "combat",
                    activityKey      = dungeonKey,
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = dungeon.displayName,
                    alarmOffsetMs    = alarmOffsetMs,
                )
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
            } finally {
                _extra.update { it.copy(startingSession = false, selectedPotionKey = null, selectedWeaponSlot = null) }
            }
        }
    }

    fun startBossSession(bossKey: String) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val bossName = gameData.bosses[bossKey]?.displayName ?: bossKey
                val bossMs   = (gameData.bosses[bossKey]?.durationMinutes ?: 1) * 60_000L
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "boss",
                        activityKey         = bossKey,
                        skillDisplayName    = bossName,
                        estimatedDurationMs = bossMs,
                    )
                )
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: $bossName." else "Queue is full (3/3).",
                        selectedBoss = null,
                    )
                }
                return@launch
            }
            _extra.update { it.copy(startingSession = true, selectedBoss = null) }
            try {
                val boss    = gameData.bosses[bossKey] ?: error("Unknown boss: $bossKey")
                val player  = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int>       = json.decodeFromString(player.skillLevels)
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
                val activeWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val bossWeapon = equipped[activeWeaponSlot]?.let { gameData.equipment[it] }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 } + (bossWeapon?.attackBonus  ?: 0)
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (bossWeapon?.defenseBonus  ?: 0)

                val potionKey     = _extra.value.selectedPotionKey
                val potionBonuses = if (potionKey != null && (inventory[potionKey] ?: 0) > 0) {
                    playerRepo.consumeItems(mapOf(potionKey to 1))
                    gameData.potionEffects[potionKey] ?: emptyMap()
                } else emptyMap()

                val combatStyle = when (bossWeapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "melee"
                }
                val selectedSpell = _extra.value.selectedSpell
                if (combatStyle == "magic" && selectedSpell == null) {
                    _extra.update { it.copy(snackbarMessage = "Select a spell before entering.", startingSession = false) }
                    return@launch
                }
                val magicLevel = levels[Skills.MAGIC] ?: 1
                if (combatStyle == "magic" && selectedSpell != null && magicLevel < selectedSpell.magicLevelRequired) {
                    _extra.update { it.copy(snackbarMessage = "Need Magic ${selectedSpell.magicLevelRequired} for ${selectedSpell.displayName}.", startingSession = false) }
                    return@launch
                }
                val preferredArrow = _extra.value.selectedArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowStrengthBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()

                val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
                playerRepo.updateFlags(flags.copy(
                    activeWeaponSlot = activeWeaponSlot,
                    activeSpell = if (combatStyle == "magic" && selectedSpell != null) selectedSpell.name else flags.activeSpell,
                ))
                val equippedFoodKeys  = flags.equippedFood.keys
                val availableFood     = inventory.filterKeys { it in equippedFoodKeys }

                val prestigeMapBoss = flags.skillPrestige
                val bossFrames = simulateBoss(
                    boss               = boss,
                    bossKey            = bossKey,
                    playerAttack       = (levels[Skills.ATTACK]    ?: 1) + (potionBonuses["attack"]   ?: 0) + (prestigeMapBoss[Skills.ATTACK]    ?: 0) * 5,
                    playerStrength     = (levels[Skills.STRENGTH]  ?: 1) + (potionBonuses["strength"] ?: 0) + (prestigeMapBoss[Skills.STRENGTH]  ?: 0) * 5,
                    playerDefence      = (levels[Skills.DEFENSE]   ?: 1) + totalDefBonus + (potionBonuses["defense"] ?: 0) + (prestigeMapBoss[Skills.DEFENSE] ?: 0) * 5,
                    playerHp           = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMapBoss[Skills.HITPOINTS] ?: 0) * 5,
                    weaponAttackBonus  = totalAtkBonus,
                    weaponStrBonus     = totalStrBonus,
                    combatStyle        = combatStyle,
                    playerRanged       = (levels[Skills.RANGED] ?: 1) + (potionBonuses["ranged"] ?: 0) + (prestigeMapBoss[Skills.RANGED] ?: 0) * 5,
                    playerMagic        = magicLevel + (potionBonuses["magic"] ?: 0) + (prestigeMapBoss[Skills.MAGIC] ?: 0) * 5,
                    arrowStrengthBonus = arrowStrengthBonus,
                    spellMaxHit        = selectedSpell?.maxHit ?: 0,
                    availableArrows    = availableArrows,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = ChurchRepository.defBonus(flags),
                )

                val totalAttacks = bossFrames.size * CombatSimulator.TICKS_PER_FRAME
                if (combatStyle == "magic" && selectedSpell != null && totalAttacks > 0) {
                    val staffCoversRune = bossWeapon?.infiniteRunes == selectedSpell.runeType
                    if (!staffCoversRune) {
                        val runesNeeded = totalAttacks * selectedSpell.runeCost
                        val ok = playerRepo.consumeItems(mapOf(selectedSpell.runeType to runesNeeded))
                        if (!ok) {
                            _extra.update { it.copy(snackbarMessage = "Not enough ${selectedSpell.displayName.substringBefore(" ")} runes (need $runesNeeded).", startingSession = false) }
                            return@launch
                        }
                    }
                }
                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    bossFrames,
                )
                val agilityLevel   = levels[Skills.AGILITY] ?: 1
                val frameMs        = SkillSimulator.sessionDurationMs(agilityLevel) / 60L
                val bossDurationMs = boss.durationMinutes * frameMs
                val animPerFrameMs = bossDurationMs / 60L
                val bossAlarmMs    = if (bossFrames.size < boss.durationMinutes)
                    (bossFrames.size - 1) * animPerFrameMs + 5_000L else null
                sessionRepo.startSession(
                    skillName        = "boss",
                    activityKey      = bossKey,
                    frames           = framesJson,
                    durationMs       = bossDurationMs,
                    skillDisplayName = boss.displayName,
                    alarmOffsetMs    = bossAlarmMs,
                )
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = "Could not start boss fight: ${e.message}") }
            } finally {
                _extra.update { it.copy(startingSession = false, selectedPotionKey = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Session collect / abandon
    // ------------------------------------------------------------------

    fun collectSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (!session.completed && System.currentTimeMillis() < session.endsAt) return@launch

            when (session.skillName) {
                "boss" -> collectBossSession(session)
                "combat" -> collectDungeonSession(session)
            }

            // Auto-start next queued session, if any
            queuedSessionStarter.startNextQueued()
        }
    }

    private suspend fun collectBossSession(session: com.fantasyidler.data.model.SkillSession) {
        val frames: List<SessionFrame> = json.decodeFromString(session.frames)
        val last = frames.lastOrNull() ?: run { sessionRepo.deleteSession(session.sessionId); return }
        val won  = last.kills > 0
        val boss = gameData.bosses[session.activityKey]

        val allItems    = last.items.toMutableMap()
        val coinsGained = allItems.remove("coins")?.toLong() ?: 0L
        val petIds      = gameData.pets.keys
        val petDrops    = allItems.filterKeys { it in petIds }
        val loot        = allItems.filterKeys { it !in petIds }
        val allFoodConsumed = mutableMapOf<String, Int>()
        for (frame in frames) frame.foodConsumed.forEach { (k, v) -> allFoodConsumed[k] = (allFoodConsumed[k] ?: 0) + v }

        val bossFlags         = playerRepo.getFlags()
        val blessingXpMult    = ChurchRepository.xpMultiplier(bossFlags)
        val blessingCoinMult  = ChurchRepository.coinMultiplier(bossFlags)
        val boostActive       = bossFlags.xpBoostExpiresAt > System.currentTimeMillis()
        val boostMult         = if (boostActive) 2L else 1L
        val capes = playerRepo.applyMultiSkillResults(last.xpBySkill, loot, coinsGained)
        if (allFoodConsumed.isNotEmpty()) playerRepo.consumeItems(allFoodConsumed)
        for ((petId, _) in petDrops) {
            val petData = gameData.pets[petId] ?: continue
            playerRepo.addPetIfNew(petId, petData.boostPercent)
        }
        if (won) {
            questRepo.recordCombat(
                dungeonKey   = session.activityKey,
                killsByEnemy = mapOf(session.activityKey to 1),
                loot         = loot,
            )
            playerRepo.recordDailyKills(mapOf(session.activityKey to 1))
            guildRepo.recordGuildCombat(mapOf(session.activityKey to 1), detectCombatStyle(last.xpBySkill))
        }
        val xpDisplayBySkill = last.xpBySkill.mapValues { (_, xp) -> xp * boostMult }
        val xpBlessingBonusBySkill = xpDisplayBySkill
            .mapValues { (_, xp) -> (xp.toDouble() * (blessingXpMult - 1)).toLong() }
            .filter { (_, bonus) -> bonus > 0 }
        val coinBlessingBonus = (coinsGained.toDouble() * (blessingCoinMult - 1)).toLong()
        val itemsDisplay = last.items.toMutableMap().also { it.remove("coins") }
        sessionRepo.deleteSession(session.sessionId)
        val bossRecentFlags = playerRepo.getFlags()
        playerRepo.updateFlags(bossRecentFlags.copy(
            recentSessions = (listOf(com.fantasyidler.data.model.RecentSession(
                skillName = session.skillName,
                activityDisplayName = boss?.displayName ?: session.activityKey,
            )) + bossRecentFlags.recentSessions).take(10),
        ))
        _extra.update {
            it.copy(
                combatResult = CombatSessionResult(
                    dungeonDisplayName     = boss?.let { b -> "${b.emoji} ${b.displayName}" } ?: session.activityKey,
                    xpPerSkill             = xpDisplayBySkill,
                    itemsGained            = itemsDisplay,
                    coinsGained            = coinsGained,
                    won                    = won,
                    killsByEnemy           = if (won) mapOf(session.activityKey to 1) else emptyMap(),
                    xpBlessingBonusBySkill = xpBlessingBonusBySkill,
                    coinBlessingBonus      = coinBlessingBonus,
                    boostWasActive         = boostActive,
                ),
                snackbarMessage = buildCapeMessage(capes),
            )
        }
    }

    private suspend fun collectDungeonSession(session: com.fantasyidler.data.model.SkillSession) {
        val frames: List<SessionFrame> = json.decodeFromString(session.frames)
        val playerDied = frames.any { it.died }

        val totalXpPerSkill = mutableMapOf<String, Long>()
        val allItems        = mutableMapOf<String, Int>()
        val allKillsByEnemy = mutableMapOf<String, Int>()
        val allFoodConsumed   = mutableMapOf<String, Int>()
        val allArrowsConsumed = mutableMapOf<String, Int>()
        for (frame in frames) {
            for ((skill, xp) in frame.xpBySkill)      totalXpPerSkill[skill] = (totalXpPerSkill[skill] ?: 0L) + xp
            for ((item, qty) in frame.items)           allItems[item]         = (allItems[item] ?: 0) + qty
            for ((enemy, kills) in frame.killsByEnemy) allKillsByEnemy[enemy] = (allKillsByEnemy[enemy] ?: 0) + kills
            for ((food, qty) in frame.foodConsumed)    allFoodConsumed[food]  = (allFoodConsumed[food] ?: 0) + qty
            for ((arrow, qty) in frame.arrowsConsumed) allArrowsConsumed[arrow] = (allArrowsConsumed[arrow] ?: 0) + qty
        }

        // On death, scale everything down to 10%
        if (playerDied) {
            totalXpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
            allItems.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
            allItems.entries.removeIf { it.value == 0 }
        }

        val coinsGained = (allItems.remove("coins")?.toLong() ?: 0L).let {
            if (playerDied) maxOf(0L, (it * 0.1).toLong()) else it
        }
        val dungeon = gameData.dungeons[session.activityKey]

        // Accumulate Slayer XP for kills that match the active task (skipped on death)
        if (!playerDied) {
            var slayerXp = 0L
            for ((enemy, kills) in allKillsByEnemy) {
                slayerXp += slayerRepo.recordKills(enemy, kills)
            }
            if (slayerXp > 0L) totalXpPerSkill[Skills.SLAYER] = (totalXpPerSkill[Skills.SLAYER] ?: 0L) + slayerXp
        }

        val dungeonFlags      = playerRepo.getFlags()
        val blessingXpMult    = ChurchRepository.xpMultiplier(dungeonFlags)
        val blessingCoinMult  = ChurchRepository.coinMultiplier(dungeonFlags)
        val boostActive       = dungeonFlags.xpBoostExpiresAt > System.currentTimeMillis()
        val boostMult         = if (boostActive) 2L else 1L
        val capes = playerRepo.applyMultiSkillResults(totalXpPerSkill, allItems, coinsGained)
        if (allFoodConsumed.isNotEmpty())   playerRepo.consumeItems(allFoodConsumed)
        if (allArrowsConsumed.isNotEmpty()) playerRepo.consumeItems(allArrowsConsumed)
        if (!playerDied) {
            val combatStyle = detectCombatStyle(totalXpPerSkill)
            questRepo.recordCombat(
                dungeonKey         = session.activityKey,
                killsByEnemy       = allKillsByEnemy,
                loot               = allItems,
                combatStyle        = combatStyle,
                foodConsumedTotal  = allFoodConsumed.values.sum(),
            )
            if (allKillsByEnemy.isNotEmpty()) {
                playerRepo.recordDailyKills(allKillsByEnemy)
                guildRepo.recordGuildCombat(allKillsByEnemy, combatStyle)
            }
            playerRepo.incrementDungeonRun(session.activityKey)
        }
        val xpDisplayBySkill = totalXpPerSkill.mapValues { (_, xp) -> xp * boostMult }
        val xpBlessingBonusBySkill = xpDisplayBySkill
            .mapValues { (_, xp) -> (xp.toDouble() * (blessingXpMult - 1)).toLong() }
            .filter { (_, bonus) -> bonus > 0 }
        val coinBlessingBonus = (coinsGained.toDouble() * (blessingCoinMult - 1)).toLong()
        sessionRepo.deleteSession(session.sessionId)
        val dungeonRecentFlags = playerRepo.getFlags()
        playerRepo.updateFlags(dungeonRecentFlags.copy(
            recentSessions = (listOf(com.fantasyidler.data.model.RecentSession(
                skillName = session.skillName,
                activityDisplayName = dungeon?.displayName ?: session.activityKey,
            )) + dungeonRecentFlags.recentSessions).take(10),
        ))

        _extra.update {
            it.copy(
                combatResult = CombatSessionResult(
                    dungeonDisplayName     = dungeon?.displayName ?: session.activityKey,
                    xpPerSkill             = xpDisplayBySkill,
                    itemsGained            = allItems,
                    coinsGained            = coinsGained,
                    won                    = !playerDied,
                    killsByEnemy           = allKillsByEnemy,
                    foodConsumed           = allFoodConsumed,
                    xpBlessingBonusBySkill = xpBlessingBonusBySkill,
                    coinBlessingBonus      = coinBlessingBonus,
                    boostWasActive         = boostActive,
                ),
                snackbarMessage = buildCapeMessage(capes),
            )
        }
    }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (session.skillName == "combat" || session.skillName == "boss") {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                val totalMs = (session.endsAt - session.startedAt).coerceAtLeast(1L)
                val perFrameMs = totalMs / frames.size.coerceAtLeast(1)
                val elapsed = System.currentTimeMillis() - session.startedAt
                val framesElapsed = (elapsed / perFrameMs).toInt().coerceIn(0, frames.size)
                val arrowsUsed = mutableMapOf<String, Int>()
                for (frame in frames.take(framesElapsed)) {
                    for ((arrow, qty) in frame.arrowsConsumed) arrowsUsed[arrow] = (arrowsUsed[arrow] ?: 0) + qty
                }
                if (arrowsUsed.isNotEmpty()) playerRepo.consumeItems(arrowsUsed)
                sessionRepo.abandonSession(session.sessionId)
                queuedSessionStarter.startNextQueued()
            }
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun resultConsumed()   = _extra.update { it.copy(combatResult = null) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    fun prestigeSkill(skillName: String) {
        viewModelScope.launch { playerRepo.prestigeSkill(skillName) }
    }

    fun confirmStartWithoutFood() {
        val key = _extra.value.pendingDungeonKey ?: return
        _extra.update { it.copy(noFoodWarningPending = false, pendingDungeonKey = null) }
        startDungeonSession(key, bypassFoodWarning = true)
    }

    fun dismissNoFoodWarning() {
        _extra.update { it.copy(noFoodWarningPending = false, pendingDungeonKey = null) }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildCapeMessage(capes: List<String>): String? {
        if (capes.isEmpty()) return null
        val names = capes.joinToString(", ") { gameData.itemDisplayName(it) }
        return "Congratulations! You received: $names"
    }

    // ------------------------------------------------------------------
    // Boss simulation
    // ------------------------------------------------------------------

    private fun simulateBoss(
        boss: BossData,
        bossKey: String,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        playerHp: Int,
        weaponAttackBonus: Int,
        weaponStrBonus: Int,
        combatStyle: String = "melee",
        playerRanged: Int = 1,
        playerMagic: Int = 1,
        arrowStrengthBonus: Int = 0,
        spellMaxHit: Int = 0,
        availableArrows: Map<String, Int> = emptyMap(),
        equippedFood: Map<String, Int> = emptyMap(),
        foodHealValues: Map<String, Int> = emptyMap(),
        blessingDefBonus: Int = 0,
    ): List<SessionFrame> = CombatSimulator.simulateBoss(
        boss               = boss,
        bossKey            = bossKey,
        playerAttack       = playerAttack,
        playerStrength     = playerStrength,
        playerDefence      = playerDefence,
        playerHp           = playerHp,
        weaponAttackBonus  = weaponAttackBonus,
        weaponStrBonus     = weaponStrBonus,
        combatStyle        = combatStyle,
        playerRanged       = playerRanged,
        playerMagic        = playerMagic,
        arrowStrengthBonus = arrowStrengthBonus,
        spellMaxHit        = spellMaxHit,
        availableArrows    = availableArrows,
        equippedFood       = equippedFood,
        foodHealValues     = foodHealValues,
        blessingDefBonus  = blessingDefBonus,
    )

    // ------------------------------------------------------------------
    // Arrow tables
    // ------------------------------------------------------------------

    /** Arrow tiers from best to worst (for picking the strongest available). */
    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    /** Strength bonus each arrow tier contributes to the max-hit formula. */
    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 0,
        "iron_arrow"       to 2,
        "steel_arrow"      to 4,
        "mithril_arrow"    to 6,
        "adamantite_arrow" to 8,
        "runite_arrow"     to 10,
    )

    /** Returns the pet XP boost % for any combat pet the player owns. */
    private fun petBoostFor(petsJson: String): Int {
        val pets = try {
            json.decodeFromString<List<OwnedPet>>(petsJson)
        } catch (_: Exception) { return 0 }
        val petId = pets.firstOrNull { pet ->
            gameData.pets[pet.id]?.boostedSkill in Skills.COMBAT
        } ?: return 0
        return gameData.pets[petId.id]?.boostPercent ?: 0
    }
}
