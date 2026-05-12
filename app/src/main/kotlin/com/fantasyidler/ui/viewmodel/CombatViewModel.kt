package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
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
import kotlin.math.max
import kotlin.random.Random
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
)

data class CombatUiState(
    val isLoading: Boolean = true,
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val equipped: Map<String, String?> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
    val equippedWeapon: EquipmentData? = null,
    val selectedSpell: SpellData? = null,
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
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

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
            val weaponKey     = equipped[EquipSlot.WEAPON]
            val equippedWeapon = weaponKey?.let { gameData.equipment[it] }
            val flags: PlayerFlags         = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            val totalAtk = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 }
            val totalStr = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
            val totalDef = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 }
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
                combatSession           = combatSession,
                totalAttackBonus        = totalAtk,
                totalStrengthBonus      = totalStr,
                totalDefenseBonus       = totalDef,
                dungeonSurvivalRatings  = survivalRatings,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CombatUiState())

    val dungeonList: List<DungeonData> by lazy {
        gameData.dungeons.values.sortedBy { it.recommendedLevel }
    }

    val bossList: List<BossData> by lazy {
        gameData.bosses.values.sortedBy { it.combatLevelRequired }
    }

    // ------------------------------------------------------------------
    // Dungeon selection
    // ------------------------------------------------------------------

    fun selectDungeon(dungeon: DungeonData?) =
        _extra.update { it.copy(selectedDungeon = dungeon) }

    fun selectBoss(boss: BossData?) =
        _extra.update { it.copy(selectedBoss = boss) }

    fun selectSpell(spell: SpellData?) =
        _extra.update { it.copy(selectedSpell = spell) }

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
                val enqueued = playerRepo.enqueueAction(QueuedAction("combat", dungeonKey, dungeonName))
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

                val weaponKey  = equipped[EquipSlot.WEAPON]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }

                val totalAttackBonus   = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 }
                val totalStrengthBonus = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
                val totalDefenseBonus  = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 }

                // Ranged: find best arrow in inventory
                val bestArrow = ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
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
                val equippedFoodKeys   = flags.equippedFood.keys
                val availableFood      = inventory.filterKeys { it in equippedFoodKeys }
                val foodHealValues     = gameData.foodHealValues

                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = levels[Skills.ATTACK]    ?: 1,
                    playerStrength      = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence       = (levels[Skills.DEFENSE]  ?: 1) + totalDefenseBonus,
                    playerHp            = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus   = totalAttackBonus,
                    weaponStrengthBonus = totalStrengthBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = levels[Skills.RANGED]    ?: 1,
                    playerMagic         = levels[Skills.MAGIC]     ?: 1,
                    arrowStrengthBonus  = arrowStrengthBonus,
                    spellMaxHit         = selectedSpell?.maxHit    ?: 0,
                    agilityLevel        = levels[Skills.AGILITY]   ?: 1,
                    petBoostPct         = petBoostFor(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = foodHealValues,
                )

                val totalKills = result.frames.sumOf { it.kills }

                // Consume ranged ammo (best effort — no arrows = no bonus, not a block)
                if (combatStyle == "ranged" && bestArrow != null && totalKills > 0) {
                    val toConsume = minOf(totalKills, inventory[bestArrow] ?: 0)
                    if (toConsume > 0) playerRepo.consumeItems(mapOf(bestArrow to toConsume))
                }

                // Consume magic runes (blocks if insufficient; skip if staff provides infinite runes)
                if (combatStyle == "magic" && selectedSpell != null && totalKills > 0) {
                    val staffCoversRune = weapon?.infiniteRunes == selectedSpell.runeType
                    if (!staffCoversRune) {
                        val runesNeeded = totalKills * selectedSpell.runeCost
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
                sessionRepo.startSession(
                    skillName        = "combat",
                    activityKey      = dungeonKey,
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = dungeon.displayName,
                )
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
            } finally {
                _extra.update { it.copy(startingSession = false) }
            }
        }
    }

    fun startBossSession(bossKey: String) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val bossName = gameData.bosses[bossKey]?.displayName ?: bossKey
                val enqueued = playerRepo.enqueueAction(QueuedAction("boss", bossKey, bossName))
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
                val totalAtkBonus = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.attackBonus  ?: 0 }
                val totalStrBonus = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 }
                val totalDefBonus = EquipSlot.COMBAT_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 }

                val frame = simulateBoss(
                    boss              = boss,
                    playerAttack      = levels[Skills.ATTACK]    ?: 1,
                    playerStrength    = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence     = (levels[Skills.DEFENSE]  ?: 1) + totalDefBonus,
                    playerHp          = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus = totalAtkBonus,
                    weaponStrBonus    = totalStrBonus,
                )
                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    listOf(frame),
                )
                sessionRepo.startSession(
                    skillName        = "boss",
                    activityKey      = bossKey,
                    frames           = framesJson,
                    durationMs       = boss.durationMinutes * 60_000L,
                    skillDisplayName = boss.displayName,
                )
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = "Could not start boss fight: ${e.message}") }
            } finally {
                _extra.update { it.copy(startingSession = false) }
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
        val frame = frames.firstOrNull() ?: run { sessionRepo.deleteSession(session.sessionId); return }
        val won  = frame.kills > 0
        val boss = gameData.bosses[session.activityKey]

        if (won) {
            val allItems    = frame.items.toMutableMap()
            val coinsGained = allItems.remove("coins")?.toLong() ?: 0L
            val petIds      = gameData.pets.keys
            val petDrops    = allItems.filterKeys { it in petIds }
            val loot        = allItems.filterKeys { it !in petIds }
            playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coinsGained)
            for ((petId, _) in petDrops) {
                val petData = gameData.pets[petId] ?: continue
                playerRepo.addPetIfNew(petId, petData.boostPercent)
            }
            val allItemsDisplay = frame.items.toMutableMap()
            val coinsDisplay    = allItemsDisplay.remove("coins")?.toLong() ?: 0L
            sessionRepo.deleteSession(session.sessionId)
            _extra.update {
                it.copy(combatResult = CombatSessionResult(
                    dungeonDisplayName = boss?.let { b -> "${b.emoji} ${b.displayName}" } ?: session.activityKey,
                    xpPerSkill  = frame.xpBySkill,
                    itemsGained = allItemsDisplay,
                    coinsGained = coinsDisplay,
                    won         = true,
                ))
            }
        } else {
            // Consolation: 10% of base XP rewards, 10% of average coin drop
            val consolationXp = boss?.xpRewards
                ?.mapValues { (_, xp) -> maxOf(1L, (xp * 0.1).toLong()) }
                ?: emptyMap()
            val consolationCoins = boss?.commonLoot?.let {
                maxOf(1L, ((it.coinsMin + it.coinsMax) / 2 * 0.1).toLong())
            } ?: 0L
            if (consolationXp.isNotEmpty() || consolationCoins > 0) {
                playerRepo.applyMultiSkillResults(consolationXp, emptyMap(), consolationCoins)
            }
            sessionRepo.deleteSession(session.sessionId)
            _extra.update {
                it.copy(combatResult = CombatSessionResult(
                    dungeonDisplayName = boss?.let { b -> "${b.emoji} ${b.displayName}" } ?: session.activityKey,
                    xpPerSkill  = consolationXp,
                    itemsGained = emptyMap(),
                    coinsGained = consolationCoins,
                    won         = false,
                ))
            }
        }
    }

    private suspend fun collectDungeonSession(session: com.fantasyidler.data.model.SkillSession) {
        val frames: List<SessionFrame> = json.decodeFromString(session.frames)
        val playerDied = frames.any { it.died }

        val totalXpPerSkill = mutableMapOf<String, Long>()
        val allItems        = mutableMapOf<String, Int>()
        val allKillsByEnemy = mutableMapOf<String, Int>()
        val allFoodConsumed = mutableMapOf<String, Int>()
        for (frame in frames) {
            for ((skill, xp) in frame.xpBySkill)  totalXpPerSkill[skill] = (totalXpPerSkill[skill] ?: 0L) + xp
            for ((item, qty) in frame.items)       allItems[item]         = (allItems[item] ?: 0) + qty
            for ((enemy, kills) in frame.killsByEnemy) allKillsByEnemy[enemy] = (allKillsByEnemy[enemy] ?: 0) + kills
            for ((food, qty) in frame.foodConsumed) allFoodConsumed[food] = (allFoodConsumed[food] ?: 0) + qty
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

        playerRepo.applyMultiSkillResults(totalXpPerSkill, allItems, coinsGained)
        // Consume food from inventory (best effort)
        if (allFoodConsumed.isNotEmpty()) playerRepo.consumeItems(allFoodConsumed)
        if (!playerDied) {
            val combatStyle = detectCombatStyle(totalXpPerSkill)
            questRepo.recordCombat(session.activityKey, allKillsByEnemy, allItems, combatStyle)
        }
        sessionRepo.deleteSession(session.sessionId)

        _extra.update {
            it.copy(combatResult = CombatSessionResult(
                dungeonDisplayName = dungeon?.displayName ?: session.activityKey,
                xpPerSkill         = totalXpPerSkill,
                itemsGained        = allItems,
                coinsGained        = coinsGained,
                won                = !playerDied,
            ))
        }
    }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (session.skillName == "combat" || session.skillName == "boss") {
                sessionRepo.abandonSession(session.sessionId)
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

    // ------------------------------------------------------------------
    // Boss simulation
    // ------------------------------------------------------------------

    /**
     * Determines win/loss via a DPS race and rolls loot on victory.
     * Result is stored as a single [SessionFrame]; kills=1 means won, kills=0 means lost.
     */
    private fun simulateBoss(
        boss: BossData,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        playerHp: Int,
        weaponAttackBonus: Int,
        weaponStrBonus: Int,
    ): SessionFrame {
        // Player DPS
        val effStr      = playerStrength + weaponStrBonus
        val playerMax   = max(1, 1 + effStr * (weaponStrBonus + 64) / 640)
        val effAtk      = playerAttack + weaponAttackBonus
        val bossDefence = boss.defensiveStats.attackDefense
        val playerHit   = when {
            effAtk > bossDefence -> 1.0 - bossDefence / (2.0 * effAtk.coerceAtLeast(1))
            else                 -> effAtk / (2.0 * bossDefence.coerceAtLeast(1))
        }.coerceIn(0.10, 0.95)
        val playerDps = (playerMax / 2.0) * playerHit / 2.4

        // Boss DPS
        val bossEffStr = boss.combatStats.strengthLevel + boss.combatStats.strengthBonus
        val bossMax    = max(1, 1 + bossEffStr * (boss.combatStats.strengthBonus + 64) / 640)
        val bossEffAtk = boss.combatStats.attackLevel + boss.combatStats.attackBonus
        val bossHit    = when {
            bossEffAtk > playerDefence -> 1.0 - playerDefence / (2.0 * bossEffAtk.coerceAtLeast(1))
            else                       -> bossEffAtk / (2.0 * playerDefence.coerceAtLeast(1))
        }.coerceIn(0.10, 0.95)
        val bossDps = (bossMax / 2.0) * bossHit / 2.4

        val playerEffHp = playerHp * 10.0
        val playerTtk   = if (playerDps > 0) boss.hp / playerDps else Double.MAX_VALUE
        val bossTtk     = if (bossDps   > 0) playerEffHp / bossDps else Double.MAX_VALUE
        val won         = playerTtk <= bossTtk

        val items     = mutableMapOf<String, Int>()
        val xpBySkill = mutableMapOf<String, Long>()

        if (won) {
            items["coins"] = Random.nextInt(boss.commonLoot.coinsMin, boss.commonLoot.coinsMax + 1)
            for ((item, range) in boss.commonLoot.items) {
                items[item] = if (range.min >= range.max) range.min
                              else Random.nextInt(range.min, range.max + 1)
            }
            for (rare in boss.rareDrops) {
                if (Random.nextDouble() < rare.chance) items[rare.item] = (items[rare.item] ?: 0) + 1
            }
            boss.pet?.let { pet ->
                if (Random.nextDouble() < pet.chance) items[pet.id] = 1
            }
            for ((skill, xp) in boss.xpRewards) xpBySkill[skill] = xp.toLong()
        }

        val totalXp = xpBySkill.values.sum()
        return SessionFrame(
            minute       = 1,
            xpGain       = totalXp.toInt(),
            xpBefore     = 0L,
            xpAfter      = totalXp,
            levelBefore  = 0,
            levelAfter   = 0,
            items        = items,
            xpBySkill    = xpBySkill,
            kills        = if (won) 1 else 0,
            killsByEnemy = emptyMap(),
        )
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
