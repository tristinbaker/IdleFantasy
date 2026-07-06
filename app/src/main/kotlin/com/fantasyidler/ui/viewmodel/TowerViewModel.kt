package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EnemySpawn
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import kotlin.math.roundToInt

data class TowerUiState(
    val isLoading: Boolean = true,
    val currentFloor: Int = 0,
    val nextFloorToQueue: Int = 1,
    val enemyStrengthPct: Int = 0,
    val bestFloor: Int = 0,
    val towerSession: SkillSession? = null,
    val claimableMilestones: List<Int> = emptyList(),
    val claimedMilestones: List<Int> = emptyList(),
    val snackbarMessage: String? = null,
    val startingSession: Boolean = false,
    val selectedWeaponSlot: String? = null,
    val equippedWeapons: Map<String, EquipmentData> = emptyMap(),
)

data class TowerMilestone(
    val floor: Int,
    val description: String,
)

@HiltViewModel
class TowerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val json: Json,
) : ViewModel() {

    companion object {
        private val ARROW_TIERS = listOf(
            "runite_arrow", "adamantite_arrow", "mithril_arrow",
            "steel_arrow", "iron_arrow", "bronze_arrow",
        )
        private val ARROW_STRENGTH_BONUS = mapOf(
            "bronze_arrow"     to 0,
            "iron_arrow"       to 2,
            "steel_arrow"      to 4,
            "mithril_arrow"    to 6,
            "adamantite_arrow" to 8,
            "runite_arrow"     to 10,
        )

        private val FLOOR_TIERS: List<Pair<IntRange, List<EnemySpawn>>> = listOf(
            (1..20)   to listOf(EnemySpawn("goblin", 40), EnemySpawn("skeleton", 30), EnemySpawn("zombie", 30)),
            (21..40)  to listOf(EnemySpawn("orc_warrior", 40), EnemySpawn("dark_wizard", 30), EnemySpawn("bandit", 30)),
            (41..60)  to listOf(EnemySpawn("cave_troll", 35), EnemySpawn("shadow_beast", 35), EnemySpawn("demon", 30)),
            (61..80)  to listOf(EnemySpawn("forge_demon", 35), EnemySpawn("shadow_assassin", 35), EnemySpawn("abyssal_leech", 30)),
            (81..100) to listOf(EnemySpawn("void_stalker", 35), EnemySpawn("void_guardian", 35), EnemySpawn("abyssal_lord", 30)),
            (101..Int.MAX_VALUE) to listOf(EnemySpawn("void_archon", 35), EnemySpawn("eternal_sentinel", 35), EnemySpawn("abyssal_lord", 30)),
        )

        val MILESTONES: List<TowerMilestone> = listOf(
            TowerMilestone(10,  "Tower Ring (attack +6, strength +6)"),
            TowerMilestone(20,  "+1% XP all skills"),
            TowerMilestone(30,  "5,000 coins"),
            TowerMilestone(40,  "Tower Shield (defense +80)"),
            TowerMilestone(50,  "Tower Amulet (attack +15, strength +15, defense +10, all styles)"),
            TowerMilestone(60,  "+5 max HP"),
            TowerMilestone(70,  "+2% XP all skills"),
            TowerMilestone(80,  "25,000 coins"),
            TowerMilestone(90,  "Tower Helm (defense +82, strength +8)"),
            TowerMilestone(100, "Tower Pet (5% combat XP)"),
            TowerMilestone(110, "+1% coin drops"),
            TowerMilestone(120, "Tower Plate (defense +132)"),
            TowerMilestone(130, "+2% XP all skills"),
            TowerMilestone(140, "100,000 coins"),
            TowerMilestone(150, "Tower Legs (defense +125)"),
            TowerMilestone(160, "+5 max HP"),
            TowerMilestone(170, "+1% coin drops"),
            TowerMilestone(180, "Tower Sword (attack +72, strength +75)"),
            TowerMilestone(190, "+2% XP all skills"),
            TowerMilestone(200, "Tower Cape (attack/str/def +14, ranged/magic +12) + 500,000 coins"),
            TowerMilestone(210, "+5 max HP"),
            TowerMilestone(220, "Tower Crossbow (ranged attack +78, ranged strength +52)"),
            TowerMilestone(230, "+1% coin drops"),
            TowerMilestone(240, "+2% XP all skills"),
            TowerMilestone(250, "Void Staff (magic attack +68, magic damage +18, infinite runes) + 1,000,000 coins"),
        )
    }

    private val _extra = MutableStateFlow(TowerUiState())

    val uiState = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, session, extra ->
        val towerSession = session?.takeIf { it.skillName == "tower" }
        if (player == null) {
            extra.copy(towerSession = towerSession)
        } else {
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            val equipped: Map<String, String?> = try { json.decodeFromString(player.equipped) } catch (_: Exception) { emptyMap() }
            val equippedWeapons = EquipSlot.WEAPON_SLOTS.mapNotNull { slot ->
                val key = equipped[slot] ?: return@mapNotNull null
                val data = gameData.equipment[key] ?: return@mapNotNull null
                slot to data
            }.toMap()
            val claimable = MILESTONES.map { it.floor }.filter { floor ->
                floor <= flags.towerBestFloor && floor !in flags.towerMilestonesClaimed
            }
            val lastQueuedFloor = flags.sessionQueue
                .lastOrNull { it.skillName == "tower" }
                ?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
            val runningFloor = lastQueuedFloor
                ?: towerSession?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
                ?: flags.towerCurrentFloor
            val enemyStrengthPct = if (flags.towerCurrentFloor > 0) {
                val baseline = tierPower(gameData.enemies, 1)
                val current  = tierPower(scaledEnemies(flags.towerCurrentFloor), flags.towerCurrentFloor)
                if (baseline > 0) (((current / baseline) - 1.0) * 100).roundToInt() else 0
            } else 0
            extra.copy(
                isLoading           = false,
                currentFloor        = flags.towerCurrentFloor,
                nextFloorToQueue    = runningFloor + 1,
                enemyStrengthPct    = enemyStrengthPct,
                bestFloor           = flags.towerBestFloor,
                towerSession        = towerSession,
                claimableMilestones = claimable,
                claimedMilestones   = flags.towerMilestonesClaimed,
                equippedWeapons     = equippedWeapons,
                selectedWeaponSlot  = extra.selectedWeaponSlot ?: flags.activeWeaponSlot,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TowerUiState())

    private fun tierFor(floor: Int): List<EnemySpawn> =
        FLOOR_TIERS.firstOrNull { (range, _) -> floor in range }?.second
            ?: FLOOR_TIERS.last().second

    private fun buildFloorDungeon(floor: Int): DungeonData = DungeonData(
        name             = "tower_floor_$floor",
        displayName      = "Floor $floor",
        description      = "Infinite Tower floor $floor",
        recommendedLevel = (floor * 2).coerceAtMost(200),
        encounterRate    = 0.65,
        enemySpawns      = tierFor(floor),
    )

    /**
     * Floors 1-100 use the fixed tier stats from FLOOR_TIERS as-is. Beyond floor 100, the
     * (fixed) 101+ tier's enemies keep scaling smoothly up to floor 250: hp grows toward
     * ~10x (landing near void_sovereign, the game's current hardest raid boss) while
     * attack/defense only grow up to +30%, so higher floors take longer to clear rather
     * than becoming more lethal.
     */
    private fun scaledEnemies(floor: Int): Map<String, EnemyData> {
        if (floor <= 100) return gameData.enemies
        val t = (floor.coerceIn(101, 250) - 100) / 150f
        val hpMult = 1f + t * 9f
        val statMult = 1f + t * 0.3f
        val relevantKeys = tierFor(floor).map { it.enemy }.toSet()
        return gameData.enemies.mapValues { (key, enemy) ->
            if (key !in relevantKeys) return@mapValues enemy
            enemy.copy(
                hp = (enemy.hp * hpMult).toInt().coerceAtLeast(1),
                combatStats = enemy.combatStats.copy(
                    attackBonus   = (enemy.combatStats.attackBonus   * statMult).toInt(),
                    strengthBonus = (enemy.combatStats.strengthBonus * statMult).toInt(),
                ),
                defensiveStats = enemy.defensiveStats.copy(
                    attackDefense   = (enemy.defensiveStats.attackDefense   * statMult).toInt(),
                    strengthDefense = (enemy.defensiveStats.strengthDefense * statMult).toInt(),
                    rangedDefense   = (enemy.defensiveStats.rangedDefense   * statMult).toInt(),
                    magicDefense    = (enemy.defensiveStats.magicDefense    * statMult).toInt(),
                ),
            )
        }
    }

    /** Effective power heuristic used to compute the "Enemy strength" display, relative to floor 1. */
    private fun tierPower(enemies: Map<String, EnemyData>, floor: Int): Double =
        tierFor(floor).mapNotNull { spawn -> enemies[spawn.enemy] }.sumOf { e ->
            e.hp.toDouble() * (e.combatStats.attackBonus + e.combatStats.strengthBonus).coerceAtLeast(1)
        }

    private fun petBoostFor(petsJson: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == "combat" || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    fun startFloor() {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val player  = playerRepo.getOrCreatePlayer()
                val agility = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
                val activeSession = sessionRepo.getActiveSession()
                val lastQueuedFloor = flags.sessionQueue
                    .lastOrNull { it.skillName == "tower" }
                    ?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
                val runningFloor = lastQueuedFloor
                    ?: activeSession?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
                    ?: flags.towerCurrentFloor
                val nextFloor = runningFloor + 1
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "tower",
                        activityKey         = "tower_floor_$nextFloor",
                        skillDisplayName    = "Infinite Tower: Floor $nextFloor",
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, flags.skillPrestige[Skills.AGILITY] ?: 0),
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued)
                            context.getString(R.string.snackbar_added_to_queue, "Infinite Tower")
                        else
                            context.getString(R.string.snackbar_queue_full),
                    )
                }
                return@launch
            }
            _extra.update { it.copy(startingSession = true) }
            try {
                val player     = playerRepo.getOrCreatePlayer()
                val levels:    Map<String, Int>      = json.decodeFromString(player.skillLevels)
                val equipped:  Map<String, String?>  = json.decodeFromString(player.equipped)
                val inventory: Map<String, Int>      = json.decodeFromString(player.inventory)
                val flags: PlayerFlags               = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }

                val floor = flags.towerCurrentFloor + 1

                val activeWeaponSlot = _extra.value.selectedWeaponSlot
                    ?: flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey = equipped[activeWeaponSlot]
                val weapon    = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }

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
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
                } else 0
                val totalMagicDmgBonus = if (combatStyle == "magic") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
                } else 0

                val selectedSpell = flags.activeSpell?.let { gameData.spells[it] }
                val bestArrow       = ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowStrBonus   = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0

                val prestigeMap  = flags.skillPrestige
                val towerHpBonus = flags.towerHpBonus

                val dungeon    = buildFloorDungeon(floor)
                val enemies    = scaledEnemies(floor)
                val foodHeal   = gameData.foodHealValues
                val availableFood   = inventory.filterKeys { it in flags.equippedFood.keys }
                val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }.associate { it to (inventory[it] ?: 0) }

                val staffCoversRune = combatStyle == "magic" && selectedSpell != null &&
                    (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == selectedSpell.runeType)
                val runeKey  = if (combatStyle == "magic" && selectedSpell != null && !staffCoversRune) selectedSpell.runeType else null
                val runeCost = selectedSpell?.runeCost ?: 1

                val neededRunes = 60 * CombatSimulator.TICKS_PER_FRAME * runeCost
                if (runeKey != null && (inventory[runeKey] ?: 0) < neededRunes) {
                    _extra.update { it.copy(
                        snackbarMessage = context.getString(R.string.tower_not_enough_runes, GameStrings.itemName(context, runeKey)),
                        startingSession = false,
                    ) }
                    return@launch
                }

                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = enemies,
                    playerAttack        = (levels[Skills.ATTACK]    ?: 1) + (prestigeMap[Skills.ATTACK]    ?: 0) * 5,
                    playerStrength      = (levels[Skills.STRENGTH]  ?: 1) + (prestigeMap[Skills.STRENGTH]  ?: 0) * 5,
                    playerDefence       = (levels[Skills.DEFENSE]   ?: 1) + totalDefenseBonus + (prestigeMap[Skills.DEFENSE] ?: 0) * 5,
                    blessingDefBonus    = ChurchRepository.defBonus(flags),
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMap[Skills.HITPOINTS] ?: 0) * 5 + towerHpBonus,
                    weaponAttackBonus   = totalAttackBonus,
                    weaponStrengthBonus = totalStrengthBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = (levels[Skills.RANGED] ?: 1) + (prestigeMap[Skills.RANGED] ?: 0) * 5,
                    playerMagic         = (levels[Skills.MAGIC]  ?: 1) + (prestigeMap[Skills.MAGIC]  ?: 0) * 5,
                    arrowStrengthBonus  = arrowStrBonus + totalRangedStrBonus,
                    spellMaxHit         = (selectedSpell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = levels[Skills.AGILITY] ?: 1,
                    agilityPrestige     = prestigeMap[Skills.AGILITY] ?: 0,
                    petBoostPct         = petBoostFor(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = foodHeal,
                    potionBonuses       = emptyMap(),
                    availableArrows     = availableArrows,
                    runeKey             = runeKey,
                    runeCostPerAttack   = runeCost,
                )

                // Runes are consumed after the session, not upfront.
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
                    skillName        = "tower",
                    activityKey      = "tower_floor_$floor",
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = "Infinite Tower: Floor $floor",
                    alarmOffsetMs    = alarmOffsetMs,
                )
            } catch (e: Exception) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _extra.update { it.copy(startingSession = false) }
            }
        }
    }

    fun selectWeaponSlot(slot: String) {
        _extra.update { it.copy(selectedWeaponSlot = slot) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            playerRepo.updateFlags(flags.copy(activeWeaponSlot = slot))
        }
    }

    fun collectFloor() {
        viewModelScope.launch {
            val latest = sessionRepo.getActiveSession()
            if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt) {
                sessionRepo.markCompleted(latest.sessionId)
            }
            val session = sessionRepo.getAllCompletedSessions().firstOrNull { it.skillName == "tower" }
                ?: return@launch

            val frames: List<SessionFrame> = json.decodeFromString(session.frames)
            val playerDied = frames.any { it.died }

            val totalXpPerSkill = mutableMapOf<String, Long>()
            val allItems        = mutableMapOf<String, Int>()
            val allFoodConsumed = mutableMapOf<String, Int>()
            val allKillsByEnemy = mutableMapOf<String, Int>()
            val allArrowsConsumed = mutableMapOf<String, Int>()
            val allRunesConsumed  = mutableMapOf<String, Int>()
            for (frame in frames) {
                for ((skill, xp) in frame.xpBySkill)      totalXpPerSkill[skill] = (totalXpPerSkill[skill] ?: 0L) + xp
                for ((item,  qty) in frame.items)          allItems[item]         = (allItems[item] ?: 0) + qty
                for ((food,  qty) in frame.foodConsumed)   allFoodConsumed[food]  = (allFoodConsumed[food] ?: 0) + qty
                for ((enemy, qty) in frame.killsByEnemy)   allKillsByEnemy[enemy] = (allKillsByEnemy[enemy] ?: 0) + qty
                for ((arrow, qty) in frame.arrowsConsumed) allArrowsConsumed[arrow] = (allArrowsConsumed[arrow] ?: 0) + qty
                for ((rune,  qty) in frame.runesConsumed)  allRunesConsumed[rune]   = (allRunesConsumed[rune] ?: 0) + qty
            }

            if (!playerDied && allKillsByEnemy.isNotEmpty()) {
                val combatStyle = detectCombatStyle(totalXpPerSkill)
                questRepo.recordCombat(
                    dungeonKey        = session.activityKey,
                    killsByEnemy      = allKillsByEnemy,
                    loot              = allItems,
                    combatStyle       = combatStyle,
                    foodConsumedTotal = allFoodConsumed.values.sum(),
                )
                playerRepo.recordDailyKills(allKillsByEnemy)
                guildRepo.recordGuildCombat(allKillsByEnemy, combatStyle)
            }

            if (playerDied) {
                totalXpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                allItems.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                allItems.entries.removeIf { it.value == 0 }
            }

            var coinsGained = allItems.remove("coins")?.toLong() ?: 0L

            val flags         = playerRepo.getFlags()
            val towerXpMult   = 1.0 + flags.towerXpBonusPct / 100.0
            val towerCoinMult = 1.0 + flags.towerCoinBonusPct / 100.0

            // Apply only the tower bonus here; applyMultiSkillResults handles boost/blessing internally
            val xpForRepo = totalXpPerSkill.mapValues { (_, xp) -> (xp * towerXpMult).toLong() }
            coinsGained   = (coinsGained * towerCoinMult).toLong()

            playerRepo.applyMultiSkillResults(xpForRepo, allItems, coinsGained)

            val skillLevels = json.decodeFromString<Map<String, Int>>(playerRepo.getOrCreatePlayer().skillLevels)
            val rangedLevel = skillLevels[Skills.RANGED] ?: 1
            val magicLevel  = skillLevels[Skills.MAGIC] ?: 1

            val arrowsReclaimed = allArrowsConsumed.mapValues { (_, qty) -> (qty * reclaimChance(rangedLevel)).toInt() }.filterValues { it > 0 }
            val runesReclaimed  = allRunesConsumed.mapValues { (_, qty) -> (qty * reclaimChance(magicLevel)).toInt() }.filterValues { it > 0 }

            val finalArrowsConsumed = allArrowsConsumed.mapValues { (k, v) -> v - (arrowsReclaimed[k] ?: 0) }.filterValues { it > 0 }
            val finalRunesConsumed  = allRunesConsumed.mapValues { (k, v) -> v - (runesReclaimed[k] ?: 0) }.filterValues { it > 0 }

            val totalConsumables = allFoodConsumed + finalArrowsConsumed + finalRunesConsumed
            if (totalConsumables.isNotEmpty()) playerRepo.consumeItems(totalConsumables)

            val floor = session.activityKey.removePrefix("tower_floor_").toIntOrNull() ?: 1

            val updatedFlags = playerRepo.getFlags()
            if (playerDied) {
                playerRepo.updateFlags(updatedFlags.copy(towerCurrentFloor = 0))
                sessionRepo.deleteSession(session.sessionId)
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.tower_death_reset, floor)) }
            } else {
                val newBest  = maxOf(updatedFlags.towerBestFloor, floor)
                val isNewBest = floor > updatedFlags.towerBestFloor
                val msg = if (isNewBest)
                    context.getString(R.string.tower_new_best, floor)
                else
                    context.getString(R.string.tower_floor_cleared, floor)
                playerRepo.updateFlags(updatedFlags.copy(
                    towerCurrentFloor = floor,
                    towerBestFloor    = newBest,
                ))
                sessionRepo.deleteSession(session.sessionId)
                _extra.update { it.copy(snackbarMessage = msg) }
            }
            queuedSessionStarter.startNextQueued()
        }
    }

    fun claimMilestone(floor: Int) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            if (floor in flags.towerMilestonesClaimed || floor > flags.towerBestFloor) return@launch

            var newFlags = flags.copy(towerMilestonesClaimed = flags.towerMilestonesClaimed + floor)

            when (floor) {
                10  -> playerRepo.addItems(mapOf("tower_ring" to 1))
                20  -> newFlags = newFlags.copy(towerXpBonusPct = newFlags.towerXpBonusPct + 1)
                30  -> playerRepo.addCoins(5_000L)
                40  -> playerRepo.addItems(mapOf("tower_shield" to 1))
                50  -> playerRepo.addItems(mapOf("tower_amulet" to 1))
                60  -> newFlags = newFlags.copy(towerHpBonus = newFlags.towerHpBonus + 5)
                70  -> newFlags = newFlags.copy(towerXpBonusPct = newFlags.towerXpBonusPct + 2)
                80  -> playerRepo.addCoins(25_000L)
                90  -> playerRepo.addItems(mapOf("tower_helm" to 1))
                100 -> {
                    val existingPets: List<OwnedPet> = try {
                        json.decodeFromString(playerRepo.getOrCreatePlayer().pets)
                    } catch (_: Exception) { emptyList() }
                    if (existingPets.none { it.id == "tower_pet" }) {
                        playerRepo.updatePets(existingPets + OwnedPet("tower_pet"))
                    }
                }
                110 -> newFlags = newFlags.copy(towerCoinBonusPct = newFlags.towerCoinBonusPct + 1)
                120 -> playerRepo.addItems(mapOf("tower_body" to 1))
                130 -> newFlags = newFlags.copy(towerXpBonusPct = newFlags.towerXpBonusPct + 2)
                140 -> playerRepo.addCoins(100_000L)
                150 -> playerRepo.addItems(mapOf("tower_legs" to 1))
                160 -> newFlags = newFlags.copy(towerHpBonus = newFlags.towerHpBonus + 5)
                170 -> newFlags = newFlags.copy(towerCoinBonusPct = newFlags.towerCoinBonusPct + 1)
                180 -> playerRepo.addItems(mapOf("tower_sword" to 1))
                190 -> newFlags = newFlags.copy(towerXpBonusPct = newFlags.towerXpBonusPct + 2)
                200 -> {
                    playerRepo.addItems(mapOf("tower_cape" to 1))
                    playerRepo.addCoins(500_000L)
                }
                210 -> newFlags = newFlags.copy(towerHpBonus = newFlags.towerHpBonus + 5)
                220 -> playerRepo.addItems(mapOf("tower_crossbow" to 1))
                230 -> newFlags = newFlags.copy(towerCoinBonusPct = newFlags.towerCoinBonusPct + 1)
                240 -> newFlags = newFlags.copy(towerXpBonusPct = newFlags.towerXpBonusPct + 2)
                250 -> {
                    playerRepo.addItems(mapOf("void_staff" to 1))
                    playerRepo.addCoins(1_000_000L)
                }
            }
            playerRepo.updateFlags(newFlags)
        }
    }

    fun snackbarConsumed() {
        _extra.update { it.copy(snackbarMessage = null) }
    }

    /** Returns the fraction of consumed ammo/runes a player recoups: 25% at level 1, 75% at level 99. */
    private fun reclaimChance(level: Int): Double = 0.25 + (level - 1) / 98.0 * 0.50
}
