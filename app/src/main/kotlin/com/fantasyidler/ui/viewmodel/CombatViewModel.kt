package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.DungeonRunStats
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.Player
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
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.random.Random
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

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
    /** Non-null when a new pet was found; drives the pet-found dialog. Consumed by the UI. */
    val petFoundName: String? = null,
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
    val dungeonLastRunStats: Map<String, DungeonRunStats> = emptyMap(),
    val unlockedDungeons: List<String> = emptyList(),
    val skillPrestige: Map<String, Int> = emptyMap(),
    val towerBestFloor: Int = 0,
    val showSessionEndTime: Boolean = true,
    val bossKillCounts: Map<String, Int> = emptyMap(),
    /** Fight count chosen in the boss confirm sheet before starting/queueing. 1 = no repeat. */
    val selectedBossRepeatCount: Int = 1,
    /** 1-based index of the boss fight currently running within a multi-fight repeat request. 0 = not repeating. */
    val activeBossRepeatIndex: Int = 0,
    /** Total fights requested for the current boss repeat run. */
    val activeBossRepeatTotal: Int = 0,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class CombatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val slayerRepo: SlayerRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    val potionEffects: Map<String, Map<String, Int>> = gameData.potionEffects

    companion object {
        const val MAX_BOSS_REPEAT_COUNT = 100
    }

    private val _extra = MutableStateFlow(CombatUiState())
    private val _simulatedRatings = MutableStateFlow<Map<String, CombatSimulator.SurvivalRating>>(emptyMap())
    private var simJob: Job? = null
    private var lastSimFingerprint = ""

    init {
        // AlarmManager delivery can be deferred by Doze; while the app is open this
        // ticker ends overdue sessions on time regardless of the alarm.
        viewModelScope.launch {
            while (true) {
                try { sessionRepo.completeOverdueSessions(queuedSessionStarter) } catch (_: Exception) {}
                delay(1_000L)
            }
        }
    }

    init {
        viewModelScope.launch {
            playerRepo.playerFlow.collect { player ->
                if (player == null) return@collect
                val fp = buildCombatFingerprint(player)
                if (fp == lastSimFingerprint) return@collect
                lastSimFingerprint = fp
                simJob?.cancel()
                simJob = viewModelScope.launch(Dispatchers.Default) {
                    _simulatedRatings.value = simulateAllDungeons(player)
                }
            }
        }
    }

    val uiState: StateFlow<CombatUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
        _simulatedRatings,
    ) { player, session, extra, simRatings ->
        val combatSession = session?.takeIf { it.skillName == "combat" || it.skillName == "boss" || it.skillName == "tower" }
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
            val displayStyle = equippedWeapon?.combatStyle ?: "melee"
            val armorAtk = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
                eq.attackBonus + when (displayStyle) {
                    "ranged" -> eq.rangedAttackBonus ?: 0
                    "magic"  -> eq.magicAttackBonus  ?: 0
                    else     -> 0
                }
            }
            val armorStr = when (displayStyle) {
                "ranged" -> EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 }
                else     -> EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
            }
            val armorDef = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 }
            val totalAtk = armorAtk + (equippedWeapon?.attackBonus ?: 0) + when (displayStyle) {
                "ranged" -> equippedWeapon?.rangedAttackBonus ?: 0
                "magic"  -> equippedWeapon?.magicAttackBonus  ?: 0
                else     -> 0
            }
            val totalStr = armorStr + when (displayStyle) {
                "ranged" -> equippedWeapon?.rangedStrengthBonus ?: 0
                else     -> equippedWeapon?.strengthBonus ?: 0
            }
            val totalDef = armorDef + (equippedWeapon?.defenseBonus  ?: 0)
            extra.copy(
                isLoading               = false,
                skillLevels             = levels,
                skillXp                 = xpMap,
                equipped                = equipped,
                inventory               = inventory,
                equippedWeapon          = equippedWeapon,
                equippedWeapons         = equippedWeapons,
                selectedWeaponSlot      = activeWeaponSlot,
                combatSession           = combatSession,
                totalAttackBonus        = totalAtk,
                totalStrengthBonus      = totalStr,
                totalDefenseBonus       = totalDef,
                dungeonSurvivalRatings  = simRatings,
                equippedFood            = flags.equippedFood.keys
                    .associateWith { inventory[it] ?: 0 }
                    .filter { (_, qty) -> qty > 0 },
                availablePotions        = inventory.filterKeys { it in gameData.potionEffects }
                    .filter { (_, qty) -> qty > 0 },
                dungeonRuns             = flags.dungeonRuns,
                dungeonLastRunStats     = flags.dungeonLastRunStats,
                unlockedDungeons        = flags.unlockedDungeons,
                selectedArrowKey        = if (extra.selectedArrowKey == null) flags.equippedArrows else extra.selectedArrowKey,
                skillPrestige           = flags.skillPrestige,
                towerBestFloor          = flags.towerBestFloor,
                showSessionEndTime      = flags.showSessionEndTime,
                bossKillCounts          = flags.enemyKills,
                selectedSpell           = if (extra.selectedSpell == null) flags.activeSpell?.let { gameData.spells[it] } else extra.selectedSpell,
                selectedPotionKey       = if (extra.selectedPotionKey == null) flags.activePotionKey?.takeIf { (inventory[it] ?: 0) > 0 } else extra.selectedPotionKey,
                activeBossRepeatIndex   = flags.activeBossRepeatIndex,
                activeBossRepeatTotal   = flags.activeBossRepeatTotal,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CombatUiState())

    val dungeonList: List<DungeonData> by lazy {
        val activeEventId = seasonalEventRepo.activeEvent()?.id
        gameData.dungeons.values
            .filter { it.eventKey == null || it.eventKey == activeEventId }
            .sortedBy { it.recommendedLevel }
    }

    val bossList: List<BossData> by lazy {
        val activeEventId = seasonalEventRepo.activeEvent()?.id
        gameData.bosses.values
            .filter { it.eventKey == null || it.eventKey == activeEventId }
            .sortedBy { it.combatLevelRequired }
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
        _extra.update { it.copy(selectedBoss = boss, selectedBossRepeatCount = 1) }

    fun selectBossRepeatCount(count: Int) =
        _extra.update { it.copy(selectedBossRepeatCount = count.coerceIn(1, MAX_BOSS_REPEAT_COUNT)) }

    fun selectWeaponSlot(slot: String) {
        _extra.update { it.copy(selectedWeaponSlot = slot) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(activeWeaponSlot = slot))
        }
    }

    fun selectSpell(spell: SpellData?) {
        _extra.update { it.copy(selectedSpell = spell) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(activeSpell = spell?.name))
        }
    }

    fun selectArrow(key: String?) {
        _extra.update { it.copy(selectedArrowKey = key) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(equippedArrows = key))
        }
    }

    fun selectPotion(key: String?) {
        _extra.update { it.copy(selectedPotionKey = key) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(activePotionKey = key))
        }
    }

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
                val queuedLevels: Map<String, Int> = json.decodeFromString(player.skillLevels)
                val agility     = queuedLevels[Skills.AGILITY] ?: 1
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val dungeonFlags: PlayerFlags = json.decodeFromString(player.flags)
                val queuedWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: dungeonFlags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val queuedSpell = _extra.value.selectedSpell
                val queuedPotionKey = _extra.value.selectedPotionKey
                val previewXp = estimateDungeonPreviewXp(
                    dungeonKey    = dungeonKey,
                    weaponSlot    = queuedWeaponSlot,
                    equipped      = equipped,
                    levels        = queuedLevels,
                    flags         = dungeonFlags,
                    selectedSpell = queuedSpell,
                    potionKey     = queuedPotionKey,
                    petsJson      = player.pets,
                )
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "combat",
                        activityKey         = dungeonKey,
                        skillDisplayName    = dungeonName,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, dungeonFlags.skillPrestige[Skills.AGILITY] ?: 0),
                        estimatedXpGain     = previewXp,
                        equippedSnapshot    = player.equipped,
                        arrowsKey           = _extra.value.selectedArrowKey ?: dungeonFlags.equippedArrows,
                        spellName           = queuedSpell?.name ?: dungeonFlags.activeSpell,
                        potionKey           = queuedPotionKey,
                        weaponSlot          = queuedWeaponSlot,
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _extra.update {
                    it.copy(
                        snackbarMessage    = if (enqueued) context.getString(R.string.snackbar_added_to_queue, dungeonName) else context.getString(R.string.snackbar_queue_full),
                        selectedDungeon    = null,
                        selectedArrowKey   = null,
                        selectedPotionKey  = null,
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
                val flags: PlayerFlags = json.decodeFromString(player.flags)

                val activeWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: flags.activeWeaponSlot
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

                val totalAttackBonus   = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
                    eq.attackBonus + when (combatStyle) {
                        "ranged" -> eq.rangedAttackBonus ?: 0
                        "magic"  -> eq.magicAttackBonus  ?: 0
                        else     -> 0
                    }
                } + (weapon?.attackBonus ?: 0) + when (combatStyle) {
                    "ranged" -> weapon?.rangedAttackBonus ?: 0
                    "magic"  -> weapon?.magicAttackBonus  ?: 0
                    else     -> 0
                }
                val totalStrengthBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefenseBonus  = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
                } else 0
                val totalMagicDmgBonus = if (combatStyle == "magic") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
                } else 0

                // Ranged: use player's chosen arrow if available, else fall back to best in inventory
                val preferredArrow = _extra.value.selectedArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }

                // Magic: validate spell selection and level
                val selectedSpell = _extra.value.selectedSpell
                if (combatStyle == "magic") {
                    if (selectedSpell == null) {
                        _extra.update {
                            it.copy(snackbarMessage = context.getString(R.string.combat_select_spell), startingSession = false)
                        }
                        return@launch
                    }
                    val magicLevel = levels[Skills.MAGIC] ?: 1
                    if (magicLevel < selectedSpell.magicLevelRequired) {
                        _extra.update {
                            it.copy(
                                snackbarMessage = context.getString(R.string.combat_spell_level_required, selectedSpell.magicLevelRequired, selectedSpell.displayName),
                                startingSession = false,
                            )
                        }
                        return@launch
                    }
                }

                // Food: use whatever equipped food items exist in inventory
                playerRepo.updateFlags(flags.copy(
                    activeWeaponSlot = activeWeaponSlot,
                    activeSpell = if (combatStyle == "magic" && selectedSpell != null) selectedSpell.name else flags.activeSpell,
                ))
                val equippedFoodKeys   = flags.equippedFood.keys
                val availableFood      = inventory.filterKeys { it in equippedFoodKeys }
                val foodHealValues     = gameData.foodHealValues

                // Arrows: preferred type drains first, then the simulator falls back to other owned tiers
                val orderedArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedArrowKeys.associateWith { inventory[it] ?: 0 }

                // Runes: determine key and cost for simulator tracking; consumed upfront below
                val staffCoversRune = combatStyle == "magic" && selectedSpell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == selectedSpell.runeType)
                val simulatorRuneKey  = if (combatStyle == "magic" && selectedSpell != null && !staffCoversRune) selectedSpell.runeType else null
                val simulatorRuneCost = selectedSpell?.runeCost ?: 1

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
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit         = (selectedSpell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = levels[Skills.AGILITY]   ?: 1,
                    agilityPrestige     = prestigeMap[Skills.AGILITY] ?: 0,
                    petBoostPct         = petBoostFor(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = foodHealValues,
                    potionBonuses       = potionBonuses,
                    availableArrows     = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    runeKey             = simulatorRuneKey,
                    runeCostPerAttack   = simulatorRuneCost,
                    availableRunes      = if (simulatorRuneKey != null) inventory[simulatorRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )

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
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _extra.update { it.copy(startingSession = false, selectedPotionKey = null) }
            }
        }
    }

    fun startBossSession(bossKey: String) {
        viewModelScope.launch {
            val repeatCount = _extra.value.selectedBossRepeatCount.coerceIn(1, MAX_BOSS_REPEAT_COUNT)
            if (sessionRepo.getActiveSession() != null) {
                val bossName     = gameData.bosses[bossKey]?.displayName ?: bossKey
                val bossMs       = (gameData.bosses[bossKey]?.durationMinutes ?: 1) * 60_000L
                val queuedPlayer = playerRepo.getOrCreatePlayer()
                val queuedFlags: PlayerFlags          = json.decodeFromString(queuedPlayer.flags)
                val queuedEquipped: Map<String, String?> = json.decodeFromString(queuedPlayer.equipped)
                val bossWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: queuedFlags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { queuedEquipped[it] != null }
                    ?: EquipSlot.WEAPON_ATK
                val bossQueuedSpell = _extra.value.selectedSpell
                val bossQueuedWeapon = queuedEquipped[bossWeaponSlot]?.let { gameData.equipment[it] }
                val bossQueuedPotionKey = _extra.value.selectedPotionKey
                val bossQueuedLevels: Map<String, Int> = json.decodeFromString(queuedPlayer.skillLevels)
                val bossPreviewXp = estimateBossPreviewXp(
                    bossKey       = bossKey,
                    weaponSlot    = bossWeaponSlot,
                    equipped      = queuedEquipped,
                    levels        = bossQueuedLevels,
                    flags         = queuedFlags,
                    selectedSpell = bossQueuedSpell,
                    potionKey     = bossQueuedPotionKey,
                )
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "boss",
                        activityKey         = bossKey,
                        skillDisplayName    = bossName,
                        estimatedDurationMs = bossMs,
                        estimatedXpGain     = bossPreviewXp,
                        equippedSnapshot    = queuedPlayer.equipped,
                        arrowsKey           = _extra.value.selectedArrowKey ?: queuedFlags.equippedArrows,
                        spellName           = if (bossQueuedWeapon?.combatStyle == "magic" && bossQueuedSpell != null) bossQueuedSpell.name else queuedFlags.activeSpell,
                        potionKey           = bossQueuedPotionKey,
                        weaponSlot          = bossWeaponSlot,
                        repeatCount         = repeatCount,
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _extra.update {
                    it.copy(
                        snackbarMessage    = if (enqueued) context.getString(R.string.snackbar_added_to_queue, bossName) else context.getString(R.string.snackbar_queue_full),
                        selectedBoss       = null,
                        selectedArrowKey   = null,
                        selectedPotionKey  = null,
                        selectedBossRepeatCount = 1,
                    )
                }
                return@launch
            }
            _extra.update { it.copy(startingSession = true, selectedBoss = null, selectedBossRepeatCount = 1) }
            try {
                val boss    = gameData.bosses[bossKey] ?: error("Unknown boss: $bossKey")
                val player  = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int>       = json.decodeFromString(player.skillLevels)
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
                val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
                val activeWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val bossWeapon = equipped[activeWeaponSlot]?.let { gameData.equipment[it] }
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
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
                    eq.attackBonus + when (combatStyle) {
                        "ranged" -> eq.rangedAttackBonus ?: 0
                        "magic"  -> eq.magicAttackBonus  ?: 0
                        else     -> 0
                    }
                } + (bossWeapon?.attackBonus ?: 0) + when (combatStyle) {
                    "ranged" -> bossWeapon?.rangedAttackBonus ?: 0
                    "magic"  -> bossWeapon?.magicAttackBonus  ?: 0
                    else     -> 0
                }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val bossRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (bossWeapon?.rangedStrengthBonus ?: 0)
                } else 0
                val bossMagicDmgBonus = if (combatStyle == "magic") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (bossWeapon?.magicDamageBonus ?: 0)
                } else 0
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
                val orderedArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedArrowKeys.associateWith { inventory[it] ?: 0 }

                val bossStaffCoversRune = combatStyle == "magic" && selectedSpell != null && (bossWeapon?.infiniteRunes == "all" || bossWeapon?.infiniteRunes == selectedSpell.runeType)
                val bossRuneKey  = if (combatStyle == "magic" && selectedSpell != null && !bossStaffCoversRune) selectedSpell.runeType else null
                val bossRuneCost = selectedSpell?.runeCost ?: 1

                playerRepo.updateFlags(flags.copy(
                    activeWeaponSlot = activeWeaponSlot,
                    activeSpell = if (combatStyle == "magic" && selectedSpell != null) selectedSpell.name else flags.activeSpell,
                ))
                val equippedFoodKeys  = flags.equippedFood.keys
                val availableFood     = inventory.filterKeys { it in equippedFoodKeys }

                val prestigeMapBoss = flags.skillPrestige
                val bossFrames = CombatSimulator.simulateBoss(
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
                    rangedGearStrengthBonus = bossRangedStrBonus,
                    spellMaxHit        = (selectedSpell?.maxHit ?: 0) + bossMagicDmgBonus,
                    availableArrows    = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = ChurchRepository.defBonus(flags),
                    runeKey            = bossRuneKey,
                    runeCostPerAttack  = bossRuneCost,
                    availableRunes     = if (bossRuneKey != null) inventory[bossRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec     = bossWeapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )

                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    bossFrames,
                )
                val agilityLevel   = levels[Skills.AGILITY] ?: 1
                val frameMs        = SkillSimulator.sessionDurationMs(agilityLevel, flags.skillPrestige[Skills.AGILITY] ?: 0) / 60L
                val bossDurationMs = boss.durationMinutes * frameMs
                sessionRepo.startSession(
                    skillName        = "boss",
                    activityKey      = bossKey,
                    frames           = framesJson,
                    durationMs       = bossDurationMs,
                    skillDisplayName = boss.displayName,
                    // endsAt is cosmetic (full duration, no outcome spoiler); the alarm
                    // ends the session at the exact death tick within the final frame.
                    alarmOffsetMs    = if (bossFrames.size < boss.durationMinutes) {
                        val lastTicks   = bossFrames.lastOrNull()?.let { maxOf(it.playerHits.size, it.enemyHits.size) } ?: 0
                        val tickMs      = if (lastTicks > 0) frameMs / lastTicks else 2_400L
                        val lastFrameMs = if (lastTicks > 0) minOf(lastTicks * tickMs, frameMs) else frameMs
                        (bossFrames.size - 1).coerceAtLeast(0) * frameMs + lastFrameMs + 2_000L
                    } else null,
                )
                if (repeatCount > 1) {
                    val bossSnapshot = QueuedAction(
                        skillName        = "boss",
                        activityKey      = bossKey,
                        skillDisplayName = boss.displayName,
                        equippedSnapshot = player.equipped,
                        arrowsKey        = _extra.value.selectedArrowKey ?: flags.equippedArrows,
                        spellName        = if (combatStyle == "magic" && selectedSpell != null) selectedSpell.name else flags.activeSpell,
                        potionKey        = potionKey,
                        weaponSlot       = activeWeaponSlot,
                        repeatCount      = repeatCount,
                    )
                    playerRepo.updateFlags(playerRepo.getFlags().copy(
                        activeBossRepeatIndex    = 1,
                        activeBossRepeatTotal    = repeatCount,
                        activeBossRepeatSnapshot = bossSnapshot,
                    ))
                } else {
                    playerRepo.clearActiveBossRepeat()
                }
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.combat_start_failed, e.message ?: "")) }
            } finally {
                _extra.update { it.copy(startingSession = false, selectedPotionKey = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Session collect / abandon
    // ------------------------------------------------------------------

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (session.skillName == "combat" || session.skillName == "boss") {
                abandonCombatSession(session)
                queuedSessionStarter.startNextQueued()
            }
        }
    }

    /**
     * Consumes arrows actually fired so far (frames are pre-simulated, so nothing is deducted
     * until abandon/collect time) and deletes the session. Shared by [abandonSession] and
     * [prestigeSkill] — prestiging a combat skill mid-fight must abandon the same way a manual
     * abandon does, or the fight keeps running against stats that no longer match its frames
     * (issue #1144).
     */
    private suspend fun abandonCombatSession(session: SkillSession) {
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
        if (session.skillName == "boss") playerRepo.clearActiveBossRepeat()
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun snackbarConsumed()  = _extra.update { it.copy(snackbarMessage = null) }
    fun petDialogConsumed() = _extra.update { it.copy(petFoundName = null) }

    fun prestigeSkill(skillName: String) {
        viewModelScope.launch {
            val activeSession = sessionRepo.getActiveSession()
            // A combat/boss fight's frames are locked in at start using the pre-prestige stats
            // (e.g. Hitpoints level). Prestiging resets that skill to level 1, so an active fight
            // left running would later fail its post-completion combat-level eligibility check
            // and get silently voided -- but still show a misleading "Defeated by X" result
            // (issue #1144). Abandoning it here, matching a manual abandon, avoids that entirely.
            val abandonedCombatSession = activeSession?.takeIf {
                skillName in Skills.COMBAT && (it.skillName == "combat" || it.skillName == "boss")
            }
            val abandonedSession = abandonedCombatSession
                ?: activeSession?.takeIf { it.skillName == skillName }
            if (abandonedCombatSession != null) {
                abandonCombatSession(abandonedCombatSession)
            } else if (abandonedSession != null) {
                val frames: List<SessionFrame> = json.decodeFromString(abandonedSession.frames)
                playerSessionMaterials(abandonedSession.skillName, abandonedSession.activityKey, frames.sumOf { it.kills }, gameData)
                    ?.let { playerRepo.addItems(it) }
                sessionRepo.abandonSession(abandonedSession.sessionId)
            }
            val evicted = playerRepo.evictQueueForSkill(skillName)
            for (action in evicted) {
                if (action.coinRefund > 0) playerRepo.addCoins(action.coinRefund)
                playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                    ?.let { playerRepo.addItems(it) }
            }
            playerRepo.prestigeSkill(skillName)
            if (abandonedSession != null) queuedSessionStarter.startNextQueued()
        }
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
        return context.getString(R.string.home_congratulations_received, names)
    }

    // ------------------------------------------------------------------
    // Dungeon survival simulation
    // ------------------------------------------------------------------

    private fun buildCombatFingerprint(player: Player): String {
        val levels    = try { json.decodeFromString<Map<String, Int>>(player.skillLevels) } catch (_: Exception) { emptyMap() }
        val flags     = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
        val inventory = try { json.decodeFromString<Map<String, Int>>(player.inventory) } catch (_: Exception) { emptyMap() }
        val foodQtys  = flags.equippedFood.keys.sorted().joinToString(",") { "$it=${inventory[it] ?: 0}" }
        val combatLevels = listOf(
            Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
            Skills.HITPOINTS, Skills.RANGED, Skills.MAGIC, Skills.AGILITY,
        ).joinToString(",") { "${it}=${levels[it] ?: 1}" }
        return "$combatLevels|${player.equipped}|${flags.equippedFood}|$foodQtys|${flags.skillPrestige}|${flags.activeWeaponSlot}|${flags.activeSpell}|${flags.activeBlessingKey}|${flags.activeBlessingExpiresAt}"
    }

    private fun simulateAllDungeons(player: Player): Map<String, CombatSimulator.SurvivalRating> {
        val levels    = try { json.decodeFromString<Map<String, Int>>(player.skillLevels) } catch (_: Exception) { emptyMap() }
        val equipped  = try { json.decodeFromString<Map<String, String?>>(player.equipped) } catch (_: Exception) { emptyMap() }
        val flags     = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
        val inventory = try { json.decodeFromString<Map<String, Int>>(player.inventory) } catch (_: Exception) { emptyMap() }

        val activeWeaponSlot = flags.activeWeaponSlot
            ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
            ?: EquipSlot.WEAPON_ATK
        val weapon       = equipped[activeWeaponSlot]?.let { gameData.equipment[it] }
        val combatStyle  = when (weapon?.combatStyle) {
            "ranged" -> "ranged"; "magic" -> "magic"; "strength" -> "strength"; else -> "attack"
        }
        val prestigeMap  = flags.skillPrestige

        val armorAtk = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
            val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
            eq.attackBonus + when (combatStyle) {
                "ranged" -> eq.rangedAttackBonus ?: 0; "magic" -> eq.magicAttackBonus ?: 0; else -> 0
            }
        }
        val armorStr = when (combatStyle) {
            "ranged" -> EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 }
            else     -> EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
        }
        val armorDef = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus ?: 0 }

        val totalAtk = armorAtk + (weapon?.attackBonus ?: 0) + when (combatStyle) {
            "ranged" -> weapon?.rangedAttackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> 0
        }
        val totalStr = armorStr + when (combatStyle) {
            "ranged" -> weapon?.rangedStrengthBonus ?: 0; else -> weapon?.strengthBonus ?: 0
        }
        val totalDef = armorDef + (weapon?.defenseBonus ?: 0)

        val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }.associate { it to (inventory[it] ?: 0) }

        val activeSpell = flags.activeSpell?.let { gameData.spells[it] }
        val spellMaxHit = if (combatStyle == "magic") activeSpell?.maxHit ?: 0 else 0

        val foodQtys = flags.equippedFood.keys.associateWith { inventory[it] ?: 0 }

        val atk     = (levels[Skills.ATTACK]    ?: 1) + (prestigeMap[Skills.ATTACK]    ?: 0) * 5
        val str     = (levels[Skills.STRENGTH]  ?: 1) + (prestigeMap[Skills.STRENGTH]  ?: 0) * 5
        val def     = (levels[Skills.DEFENSE]   ?: 1) + totalDef + (prestigeMap[Skills.DEFENSE] ?: 0) * 5
        val hp      = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMap[Skills.HITPOINTS] ?: 0) * 5
        val rng     = (levels[Skills.RANGED]    ?: 1) + (prestigeMap[Skills.RANGED]    ?: 0) * 5
        val mgc     = (levels[Skills.MAGIC]     ?: 1) + (prestigeMap[Skills.MAGIC]     ?: 0) * 5
        val agility = levels[Skills.AGILITY]    ?: 1

        return gameData.dungeons.mapValues { (_, dungeon) ->
            val result = CombatSimulator.simulateDungeon(
                dungeon             = dungeon,
                enemies             = gameData.enemies,
                playerAttack        = atk,
                playerStrength      = str,
                playerDefence       = def,
                blessingDefBonus    = ChurchRepository.defBonus(flags),
                playerHp            = hp,
                weaponAttackBonus   = totalAtk,
                weaponStrengthBonus = totalStr,
                combatStyle         = combatStyle,
                playerRanged        = rng,
                playerMagic         = mgc,
                rangedGearStrengthBonus = if (combatStyle == "ranged") totalStr else 0,
                spellMaxHit         = spellMaxHit,
                agilityLevel        = agility,
                agilityPrestige     = prestigeMap[Skills.AGILITY] ?: 0,
                equippedFood        = foodQtys,
                foodHealValues      = gameData.foodHealValues,
                availableArrows     = availableArrows,
                arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                random              = Random(42),
            )
            val deathFrame = result.frames.indexOfFirst { it.died }
            when {
                deathFrame < 0   -> CombatSimulator.SurvivalRating.LIKELY
                deathFrame >= 45 -> CombatSimulator.SurvivalRating.RISKY
                else             -> CombatSimulator.SurvivalRating.UNLIKELY
            }
        }
    }

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
        "bronze_arrow"     to 7,
        "iron_arrow"       to 10,
        "steel_arrow"      to 16,
        "mithril_arrow"    to 22,
        "adamantite_arrow" to 31,
        "runite_arrow"     to 49,
    )

    /** Returns the combined XP boost % from all "combat" and "all" pets the player owns. */
    private fun petBoostFor(petsJson: String): Int {
        val pets = try {
            json.decodeFromString<List<OwnedPet>>(petsJson)
        } catch (_: Exception) { return 0 }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == "combat" || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    // ------------------------------------------------------------------
    // Queue preview: throwaway simulation runs used only to estimate the
    // "Up Next" XP number for a session that hasn't started yet (issue #1078).
    // Food/arrows/runes are treated as unlimited since this never consumes
    // inventory — it's a best-case display estimate, not the real session.
    // ------------------------------------------------------------------

    private fun estimateDungeonPreviewXp(
        dungeonKey: String,
        weaponSlot: String,
        equipped: Map<String, String?>,
        levels: Map<String, Int>,
        flags: PlayerFlags,
        selectedSpell: SpellData?,
        potionKey: String?,
        petsJson: String,
    ): Long {
        val dungeon = gameData.dungeons[dungeonKey] ?: return 0L
        val weapon  = equipped[weaponSlot]?.let { gameData.equipment[it] }
        val combatStyle = when (weapon?.combatStyle) {
            "ranged"   -> "ranged"
            "magic"    -> "magic"
            "strength" -> "strength"
            else       -> "attack"
        }
        if (combatStyle == "magic" && selectedSpell == null) return 0L

        val totalAttackBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
            val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
            eq.attackBonus + when (combatStyle) {
                "ranged" -> eq.rangedAttackBonus ?: 0
                "magic"  -> eq.magicAttackBonus  ?: 0
                else     -> 0
            }
        } + (weapon?.attackBonus ?: 0) + when (combatStyle) {
            "ranged" -> weapon?.rangedAttackBonus ?: 0
            "magic"  -> weapon?.magicAttackBonus  ?: 0
            else     -> 0
        }
        val totalStrengthBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
        val totalDefenseBonus  = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
        val totalRangedStrBonus = if (combatStyle == "ranged")
            EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
        else 0
        val totalMagicDmgBonus = if (combatStyle == "magic")
            EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
        else 0

        val potionBonuses = potionKey?.let { gameData.potionEffects[it] } ?: emptyMap()
        val prestigeMap   = flags.skillPrestige
        val staffCoversRune  = combatStyle == "magic" && selectedSpell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == selectedSpell.runeType)
        val simulatorRuneKey = if (combatStyle == "magic" && selectedSpell != null && !staffCoversRune) selectedSpell.runeType else null

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
            rangedGearStrengthBonus = totalRangedStrBonus,
            spellMaxHit         = (selectedSpell?.maxHit ?: 0) + totalMagicDmgBonus,
            agilityLevel        = levels[Skills.AGILITY]   ?: 1,
            agilityPrestige     = prestigeMap[Skills.AGILITY] ?: 0,
            petBoostPct         = petBoostFor(petsJson),
            equippedFood        = flags.equippedFood.keys.associateWith { Int.MAX_VALUE },
            foodHealValues      = gameData.foodHealValues,
            potionBonuses       = potionBonuses,
            availableArrows     = ARROW_TIERS.associateWith { Int.MAX_VALUE },
            arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
            runeKey             = simulatorRuneKey,
            runeCostPerAttack   = selectedSpell?.runeCost ?: 1,
            availableRunes      = Int.MAX_VALUE,
            attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
        )
        return result.frames.sumOf { it.xpGain.toLong() }
    }

    private fun estimateBossPreviewXp(
        bossKey: String,
        weaponSlot: String,
        equipped: Map<String, String?>,
        levels: Map<String, Int>,
        flags: PlayerFlags,
        selectedSpell: SpellData?,
        potionKey: String?,
    ): Long {
        val boss   = gameData.bosses[bossKey] ?: return 0L
        val weapon = equipped[weaponSlot]?.let { gameData.equipment[it] }
        val combatStyle = when (weapon?.combatStyle) {
            "ranged"   -> "ranged"
            "magic"    -> "magic"
            "strength" -> "strength"
            else       -> "melee"
        }
        if (combatStyle == "magic" && selectedSpell == null) return 0L
        val magicLevel = levels[Skills.MAGIC] ?: 1
        if (combatStyle == "magic" && selectedSpell != null && magicLevel < selectedSpell.magicLevelRequired) return 0L

        val potionBonuses = potionKey?.let { gameData.potionEffects[it] } ?: emptyMap()
        val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
            val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
            eq.attackBonus + when (combatStyle) {
                "ranged" -> eq.rangedAttackBonus ?: 0
                "magic"  -> eq.magicAttackBonus  ?: 0
                else     -> 0
            }
        } + (weapon?.attackBonus ?: 0) + when (combatStyle) {
            "ranged" -> weapon?.rangedAttackBonus ?: 0
            "magic"  -> weapon?.magicAttackBonus  ?: 0
            else     -> 0
        }
        val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
        val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
        val bossRangedStrBonus = if (combatStyle == "ranged")
            EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
        else 0
        val bossMagicDmgBonus = if (combatStyle == "magic")
            EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
        else 0

        val prestigeMapBoss = flags.skillPrestige
        val staffCoversRune  = combatStyle == "magic" && selectedSpell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == selectedSpell.runeType)
        val bossRuneKey      = if (combatStyle == "magic" && selectedSpell != null && !staffCoversRune) selectedSpell.runeType else null

        val bossFrames = CombatSimulator.simulateBoss(
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
            rangedGearStrengthBonus = bossRangedStrBonus,
            spellMaxHit        = (selectedSpell?.maxHit ?: 0) + bossMagicDmgBonus,
            availableArrows    = ARROW_TIERS.associateWith { Int.MAX_VALUE },
            arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
            equippedFood       = flags.equippedFood.keys.associateWith { Int.MAX_VALUE },
            foodHealValues     = gameData.foodHealValues,
            blessingDefBonus   = ChurchRepository.defBonus(flags),
            runeKey            = bossRuneKey,
            runeCostPerAttack  = selectedSpell?.runeCost ?: 1,
            availableRunes     = Int.MAX_VALUE,
            attackSpeedSec     = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
        )
        return bossFrames.sumOf { it.xpGain.toLong() }
    }
}

/** Returns the fraction of consumed ammo/runes a player recoups: 25% at level 1, 75% at level 99. */
private fun reclaimChance(level: Int): Double = 0.25 + (level - 1) / 98.0 * 0.50
