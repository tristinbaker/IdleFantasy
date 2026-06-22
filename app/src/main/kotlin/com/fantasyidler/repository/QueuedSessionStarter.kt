package com.fantasyidler.repository

import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CarnivalSimulator
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.MercantileSimulator
import com.fantasyidler.simulator.SkillingDungeonSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.XpTable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the next queued session using current player state.
 * Shared between ViewModels (on collect) and [com.fantasyidler.receiver.SessionAlarmReceiver]
 * (background auto-advance).
 */
@Singleton
class QueuedSessionStarter @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {
    private val mutex = Mutex()

    /**
     * Pops the first item from the queue and starts it as a new session.
     * Returns true if a session was started, false if the queue was empty or the
     * session couldn't be started (e.g. missing materials).
     */
    suspend fun startNextQueued(backdateMs: Long = 0L): Boolean {
        // Mutex covers the full dequeue + session-start so concurrent callers (alarm
        // receiver, recoverActiveSession, collectSession) can't both pass the "no running
        // session" check and dequeue separate actions before either inserts a DB row.
        mutex.withLock {
            val current = sessionRepo.getActiveSession()
            if (current != null && !current.completed) return false
            val next = playerRepo.dequeueNextAction() ?: return false
            return try {
                startQueuedAction(next, backdateMs = backdateMs)
                true
            } catch (_: Exception) {
                playerRepo.requeueActionAtFront(next)
                false
            }
        }
        return false
    }

    /**
     * Estimates how long [action] would take without running the full simulation.
     * Used to decide whether a queued session fits within remaining catch-up time.
     */
    private fun estimateDuration(action: QueuedAction, agilityLevel: Int, agilityPrestige: Int = 0): Long {
        val base = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige)
        val perItem = base / 60L
        return when (action.skillName) {
            Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
            Skills.AGILITY, "expedition", "combat" -> base
            Skills.SMITHING, Skills.COOKING, Skills.FLETCHING,
            Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING,
            Skills.RUNECRAFTING, Skills.PRAYER, Skills.CONSTRUCTION -> action.qty.toLong() * perItem
            "boss" -> gameData.bosses[action.activityKey]
                          ?.durationMinutes?.let { it * (base / 60L) } ?: base
            Skills.MERCANTILE -> action.estimatedDurationMs.takeIf { it > 0 } ?: base
            else -> base
        }
    }

    /**
     * Pops the next queued action and inserts it as an already-completed session,
     * provided its estimated duration fits within [remainingMs].
     *
     * Returns the estimated duration of the inserted session, or 0L if:
     * - the queue is empty
     * - the next session wouldn't have finished within [remainingMs]
     * - the session failed to start (re-queued at front)
     *
     * Called from [SessionRepository.recoverActiveSession] to reconstruct offline progress.
     */
    suspend fun insertNextQueuedAsOffline(remainingMs: Long): Long {
        mutex.withLock {
            val next = playerRepo.dequeueNextAction() ?: return 0L
            val player = playerRepo.getOrCreatePlayer()
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val flags: PlayerFlags       = json.decodeFromString(player.flags)
            val agilityLevel    = levels[Skills.AGILITY] ?: 1
            val agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0
            val duration = estimateDuration(next, agilityLevel, agilityPrestige)
            if (duration > remainingMs) {
                playerRepo.requeueActionAtFront(next)
                return 0L
            }
            return try {
                startQueuedAction(next, offline = true)
                duration
            } catch (_: Exception) {
                playerRepo.requeueActionAtFront(next)
                0L
            }
        }
        return 0L
    }

    private suspend fun startQueuedAction(action: QueuedAction, offline: Boolean = false, backdateMs: Long = 0L) {
        val player    = playerRepo.getOrCreatePlayer()
        val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
        val xpMap:    Map<String, Long>    = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val agilityLevel    = levels[Skills.AGILITY] ?: 1
        val agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0
        val equippedCapeData = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        val combatCapeMult   = if (equippedCapeData?.capeSkill in COMBAT_CAPE_SKILLS) 1f + (equippedCapeData?.capeBonus ?: 0f) else 1f
        val prayerCapeMult   = if (equippedCapeData?.capeSkill == "prayer") 1f + (equippedCapeData?.capeBonus ?: 0f) else 1f

        when (action.skillName) {
            Skills.MINING -> {
                val oreKey  = action.activityKey
                val oreData = gameData.ores[oreKey] ?: return
                val result  = SkillSimulator.simulateMining(
                    oreKey          = oreKey,
                    oreData         = oreData,
                    gems            = gameData.gems,
                    startXp         = xpMap[Skills.MINING] ?: 0L,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = agilityPrestige,
                    petBoostPct     = gatheringPetBoost(player.pets, Skills.MINING),
                    toolEfficiency  = toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
                    petDropKey      = petDropKey(Skills.MINING),
                    petDropChance   = petDropChance(Skills.MINING),
                )
                startSession(action, result, offline, backdateMs)
            }
            Skills.WOODCUTTING -> {
                val treeKey  = action.activityKey
                val treeData = gameData.trees[treeKey] ?: return
                val result   = SkillSimulator.simulateWoodcutting(
                    treeData        = treeData,
                    startXp         = xpMap[Skills.WOODCUTTING] ?: 0L,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = agilityPrestige,
                    petBoostPct     = gatheringPetBoost(player.pets, Skills.WOODCUTTING),
                    toolEfficiency  = toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
                    petDropKey      = petDropKey(Skills.WOODCUTTING),
                    petDropChance   = petDropChance(Skills.WOODCUTTING),
                )
                startSession(action, result, offline, backdateMs)
            }
            Skills.FISHING -> {
                val fishKey  = action.activityKey
                val fishData = gameData.fish[fishKey] ?: return
                val result   = SkillSimulator.simulateFishing(
                    fishKey          = fishKey,
                    fishData         = fishData,
                    startXp          = xpMap[Skills.FISHING] ?: 0L,
                    agilityLevel     = agilityLevel,
                    agilityPrestige  = agilityPrestige,
                    petBoostPct      = gatheringPetBoost(player.pets, Skills.FISHING),
                    rodEfficiency    = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
                    petDropKey       = petDropKey(Skills.FISHING),
                    petDropChance    = petDropChance(Skills.FISHING),
                    fishingSkillData = gameData.fishingSkillData,
                )
                startSession(action, result, offline, backdateMs)
            }
            Skills.AGILITY -> {
                val courseKey  = action.activityKey
                val courseData = gameData.agilityCourses[courseKey] ?: return
                val result     = SkillSimulator.simulateAgility(
                    courseData      = courseData,
                    startXp         = xpMap[Skills.AGILITY] ?: 0L,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = agilityPrestige,
                    petBoostPct  = gatheringPetBoost(player.pets, Skills.AGILITY),
                    petDropKey   = petDropKey(Skills.AGILITY),
                    petDropChance = petDropChance(Skills.AGILITY),
                )
                startSession(action, result, offline, backdateMs)
            }
            Skills.THIEVING -> {
                val npcKey  = action.activityKey
                val npc     = gameData.thievingNpcs[npcKey] ?: return
                val result  = ThievingSimulator.simulate(
                    npcKey          = npcKey,
                    npc             = npc,
                    startXp         = xpMap[Skills.THIEVING] ?: 0L,
                    thievingLevel   = levels[Skills.THIEVING] ?: 1,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = agilityPrestige,
                    petBoostPct   = gatheringPetBoost(player.pets, Skills.THIEVING),
                    petDropKey    = petDropKey(Skills.THIEVING),
                    petDropChance = petDropChance(Skills.THIEVING),
                )
                sessionRepo.startSession(
                    skillName         = Skills.THIEVING,
                    activityKey       = npcKey,
                    frames            = encodeFrames(result.frames),
                    durationMs        = result.durationMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                )
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val logData = gameData.logs[logKey] ?: return
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val ashKey  = ashForLog(logKey)
                val frames  = buildCraftFrames(xpMap[Skills.FIREMAKING] ?: 0L, qty, logData.xpPerLog.toDouble(), 1, ashKey,
                    petDropKey = petDropKey(Skills.FIREMAKING), petDropChance = petDropChance(Skills.FIREMAKING))
                val perLogMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60L
                sessionRepo.startSession(
                    skillName         = Skills.FIREMAKING,
                    activityKey       = logKey,
                    frames            = encodeFrames(frames),
                    durationMs        = qty.toLong() * perLogMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                )
            }
            Skills.RUNECRAFTING -> {
                val runeKey  = action.activityKey
                val runeData = gameData.runes[runeKey] ?: return
                val qty      = action.qty.takeIf { it > 0 } ?: return
                val ashBonus = action.catalystKey?.let { ashRuneBonus(it) } ?: 0
                val ashCost  = if (ashBonus > 0) (qty + 9) / 10 else 0
                if (ashCost > 0) playerRepo.consumeItems(mapOf(action.catalystKey!! to ashCost))
                val currentXp = xpMap[Skills.RUNECRAFTING] ?: 0L
                val rcPetDropKey = petDropKey(Skills.RUNECRAFTING)
                val rcPetDropChance = petDropChance(Skills.RUNECRAFTING)
                val frames = mutableListOf<SessionFrame>().also { list ->
                    var xp = currentXp
                    for (i in 1..qty) {
                        val before = XpTable.levelForXp(xp)
                        val multiplier = when {
                            before >= 75 -> 3
                            before >= 50 -> 2
                            else         -> 1
                        } + ashBonus
                        val gain = (runeData.xpPerRune * multiplier).toInt()
                        xp += gain
                        list.add(SessionFrame(
                            minute      = i,
                            xpGain      = gain,
                            xpBefore    = xp - gain,
                            xpAfter     = xp,
                            levelBefore = before,
                            levelAfter  = XpTable.levelForXp(xp),
                            items       = mapOf(runeKey to multiplier),
                            kills       = 1,
                        ))
                    }
                }
                if (rcPetDropKey != null && rcPetDropChance > 0.0 && frames.isNotEmpty()) {
                    val dropped = (0 until 60).any { Random.nextDouble() < rcPetDropChance }
                    if (dropped) {
                        val last = frames.last()
                        frames[frames.size - 1] = last.copy(items = last.items + (rcPetDropKey to 1))
                    }
                }
                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.RUNECRAFTING, runeKey, encodeFrames(frames), qty.toLong() * perEssenceMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.PRAYER -> {
                val boneKey = action.activityKey
                val bone    = gameData.bones[boneKey] ?: return
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val currentXp = xpMap[Skills.PRAYER] ?: 0L
                val frameCount = minOf(qty, 60)
                val frames = buildList {
                    var xp = currentXp
                    for (bucket in 0 until frameCount) {
                        val bonesInBucket = ((bucket.toLong() + 1) * qty / frameCount - bucket.toLong() * qty / frameCount).toInt()
                        val before = XpTable.levelForXp(xp)
                        val gain   = bone.xpPerBone.toInt() * bonesInBucket
                        xp        += gain
                        add(SessionFrame(
                            minute      = bucket + 1,
                            xpGain      = gain,
                            xpBefore    = xp - gain,
                            xpAfter     = xp,
                            levelBefore = before,
                            levelAfter  = XpTable.levelForXp(xp),
                            kills       = bonesInBucket,
                        ))
                    }
                }
                val perBoneMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(
                    skillName         = Skills.PRAYER,
                    activityKey       = boneKey,
                    frames            = encodeFrames(frames),
                    durationMs        = qty.toLong() * perBoneMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                )
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petDropKey = petDropKey(Skills.SMITHING), petDropChance = petDropChance(Skills.SMITHING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.SMITHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem)
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.COOKING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName,
                    petDropKey = petDropKey(Skills.FLETCHING), petDropChance = petDropChance(Skills.FLETCHING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.FLETCHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petDropKey = petDropKey(Skills.CRAFTING), petDropChance = petDropChance(Skills.CRAFTING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.CRAFTING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.CONSTRUCTION -> {
                val r   = gameData.constructionRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CONSTRUCTION] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petDropKey = petDropKey(Skills.CONSTRUCTION), petDropChance = petDropChance(Skills.CONSTRUCTION))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.CONSTRUCTION, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.HERBLORE -> {
                val r   = gameData.herbloreRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val catalystKey = action.catalystKey
                val outputKey   = if (catalystKey != null) "enhanced_${action.activityKey}" else action.activityKey
                if (catalystKey != null) playerRepo.consumeItems(mapOf(catalystKey to qty))
                val frames    = buildCraftFrames(xpMap[Skills.HERBLORE] ?: 0L, qty, r.xpPerItem, r.outputQuantity, outputKey,
                    petDropKey = petDropKey(Skills.HERBLORE), petDropChance = petDropChance(Skills.HERBLORE))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.HERBLORE, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs)
            }
            Skills.MERCANTILE -> {
                val route = gameData.tradeRoutes.firstOrNull { it.id == action.activityKey } ?: return
                val result = MercantileSimulator.simulate(
                    route           = route,
                    startXp         = xpMap[Skills.MERCANTILE] ?: 0L,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = agilityPrestige,
                    petDropKey    = petDropKey(Skills.MERCANTILE),
                    petDropChance = petDropChance(Skills.MERCANTILE),
                )
                sessionRepo.startSession(
                    skillName         = action.skillName,
                    activityKey       = action.activityKey,
                    frames            = encodeFrames(result.frames),
                    durationMs        = result.durationMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                )
            }
            "boss" -> {
                val bossKey = action.activityKey
                val boss    = gameData.bosses[bossKey] ?: return
                val bossEquipped: Map<String, String?> = if (action.equippedSnapshot != null)
                    json.decodeFromString(action.equippedSnapshot) else equipped
                val bossArrowKey  = action.arrowsKey ?: flags.equippedArrows
                val bossSpellName = action.spellName ?: flags.activeSpell
                val bossPotionBonuses = if (action.potionKey != null && (inventory[action.potionKey] ?: 0) > 0) {
                    playerRepo.consumeItems(mapOf(action.potionKey to 1))
                    gameData.potionEffects[action.potionKey] ?: emptyMap()
                } else emptyMap()
                val bossWeaponSlot = action.weaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { bossEquipped[it] != null }
                    ?: EquipSlot.WEAPON
                val bossWeapon = bossEquipped[bossWeaponSlot]?.let { gameData.equipment[it] }
                val combatStyle = when (bossWeapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "melee"
                }
                val totalAtkBonus    = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[bossEquipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> bossWeapon?.rangedAttackBonus ?: bossWeapon?.attackBonus ?: 0; "magic" -> bossWeapon?.magicAttackBonus ?: 0; else -> bossWeapon?.attackBonus ?: 0 }
                val totalStrBonus    = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[bossEquipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val totalDefBonus    = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[bossEquipped[it]]?.defenseBonus  ?: 0 } + (bossWeapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[bossEquipped[it]]?.magicDamageBonus ?: 0 } + (bossWeapon?.magicDamageBonus ?: 0) else 0
                val equippedFoodKeys  = flags.equippedFood.keys
                val prevFoodConsumed  = pendingFoodConsumed()
                val availableFood     = inventory.filterKeys { it in equippedFoodKeys }
                    .mapValues { (k, v) -> (v - (prevFoodConsumed[k] ?: 0)).coerceAtLeast(0) }
                    .filterValues { it > 0 }
                val spell = gameData.spells[bossSpellName]
                val preferredArrow = bossArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()
                val pmBoss = flags.skillPrestige
                val bossFrames = CombatSimulator.simulateBoss(
                    boss               = boss,
                    bossKey            = bossKey,
                    playerAttack       = ((levels[Skills.ATTACK]   ?: 1) * combatCapeMult).toInt() + (pmBoss[Skills.ATTACK]    ?: 0) * 5 + (bossPotionBonuses["attack"]   ?: 0),
                    playerStrength     = ((levels[Skills.STRENGTH] ?: 1) * combatCapeMult).toInt() + (pmBoss[Skills.STRENGTH]  ?: 0) * 5 + (bossPotionBonuses["strength"] ?: 0),
                    playerDefence      = ((levels[Skills.DEFENSE]  ?: 1) * combatCapeMult).toInt() + totalDefBonus + (pmBoss[Skills.DEFENSE] ?: 0) * 5 + (bossPotionBonuses["defense"] ?: 0),
                    playerHp           = (levels[Skills.HITPOINTS] ?: 1) + (pmBoss[Skills.HITPOINTS] ?: 0) * 5,
                    weaponAttackBonus  = totalAtkBonus,
                    weaponStrBonus     = totalStrBonus,
                    combatStyle        = combatStyle,
                    playerRanged       = ((levels[Skills.RANGED] ?: 1) * combatCapeMult).toInt() + (pmBoss[Skills.RANGED] ?: 0) * 5 + (bossPotionBonuses["ranged"] ?: 0),
                    playerMagic        = ((levels[Skills.MAGIC]  ?: 1) * combatCapeMult).toInt() + (pmBoss[Skills.MAGIC]  ?: 0) * 5 + (bossPotionBonuses["magic"]  ?: 0),
                    arrowStrengthBonus = arrowBonus,
                    spellMaxHit        = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows    = availableArrows,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                )
                val frameMs        = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60L
                val bossDurationMs = boss.durationMinutes * frameMs
                sessionRepo.startSession(
                    skillName         = "boss",
                    activityKey       = bossKey,
                    frames            = encodeFrames(bossFrames),
                    durationMs        = bossDurationMs,
                    skillDisplayName  = action.skillDisplayName,
                    // endsAt is cosmetic (full duration, no outcome spoiler); the alarm
                    // ends the session at the exact death tick within the final frame.
                    alarmOffsetMs     = if (bossFrames.size < boss.durationMinutes) {
                        val lastTicks   = bossFrames.lastOrNull()?.let { maxOf(it.playerHits.size, it.enemyHits.size) } ?: 0
                        val lastFrameMs = if (lastTicks > 0) minOf(lastTicks * 2_400L, frameMs) else frameMs
                        (bossFrames.size - 1).coerceAtLeast(0) * frameMs + lastFrameMs + 2_000L
                    } else null,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                )
            }
            "expedition" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.skillingDungeons[dungeonKey] ?: return
                val toolEfficiency: Float = when (dungeon.skill) {
                    Skills.MINING      -> toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     dungeon.levelRequired)
                    Skills.WOODCUTTING -> toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         dungeon.levelRequired)
                    Skills.FISHING     -> toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, dungeon.levelRequired)
                    else               -> 1.0f
                }
                val result = SkillingDungeonSimulator.simulate(
                    dungeonKey     = dungeonKey,
                    dungeon        = dungeon,
                    startXp        = xpMap[dungeon.skill] ?: 0L,
                    agilityLevel   = agilityLevel,
                    toolEfficiency = toolEfficiency,
                )
                startSession(action, result, offline, backdateMs)
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                val combatEquipped: Map<String, String?> = if (action.equippedSnapshot != null)
                    json.decodeFromString(action.equippedSnapshot) else equipped
                val combatArrowKey  = action.arrowsKey ?: flags.equippedArrows
                val combatSpellName = action.spellName ?: flags.activeSpell
                val combatPotBonuses = if (action.potionKey != null && (inventory[action.potionKey] ?: 0) > 0) {
                    playerRepo.consumeItems(mapOf(action.potionKey to 1))
                    gameData.potionEffects[action.potionKey] ?: emptyMap()
                } else emptyMap()
                val activeWeaponSlot = action.weaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { combatEquipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey  = combatEquipped[activeWeaponSlot]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val preferredArrow = combatArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()
                val equippedFoodKeys  = flags.equippedFood.keys
                val prevFoodConsumed  = pendingFoodConsumed()
                val availableFood     = inventory.filterKeys { it in equippedFoodKeys }
                    .mapValues { (k, v) -> (v - (prevFoodConsumed[k] ?: 0)).coerceAtLeast(0) }
                    .filterValues { it > 0 }
                val spell = gameData.spells[combatSpellName]
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[combatEquipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[combatEquipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[combatEquipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[combatEquipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val pm = flags.skillPrestige
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = ((levels[Skills.ATTACK]   ?: 1) * combatCapeMult).toInt() + (pm[Skills.ATTACK]    ?: 0) * 5 + (combatPotBonuses["attack"]   ?: 0),
                    playerStrength      = ((levels[Skills.STRENGTH] ?: 1) * combatCapeMult).toInt() + (pm[Skills.STRENGTH]  ?: 0) * 5 + (combatPotBonuses["strength"] ?: 0),
                    playerDefence       = ((levels[Skills.DEFENSE]  ?: 1) * combatCapeMult).toInt() + totalDefBonus + (pm[Skills.DEFENSE] ?: 0) * 5 + (combatPotBonuses["defense"] ?: 0),
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + (pm[Skills.HITPOINTS] ?: 0) * 5,
                    blessingDefBonus    = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = ((levels[Skills.RANGED] ?: 1) * combatCapeMult).toInt() + (pm[Skills.RANGED] ?: 0) * 5 + (combatPotBonuses["ranged"] ?: 0),
                    playerMagic         = ((levels[Skills.MAGIC]  ?: 1) * combatCapeMult).toInt() + (pm[Skills.MAGIC]  ?: 0) * 5 + (combatPotBonuses["magic"]  ?: 0),
                    arrowStrengthBonus  = arrowBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    petBoostPct         = combatPetBoost(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                    availableArrows     = availableArrows,
                )
                val totalKills = result.frames.sumOf { it.kills }
                if (combatStyle == "magic" && spell != null && totalKills > 0) {
                    val staffCoversRune = weapon?.infiniteRunes == spell.runeType
                    if (!staffCoversRune)
                        playerRepo.consumeItems(mapOf(spell.runeType to totalKills * spell.runeCost))
                }
                startSession(action, result, offline, backdateMs)
            }
            "carnival" -> {
                val relevantSkillLevel = when (action.activityKey) {
                    "archery_range"         -> levels[Skills.RANGED]   ?: 1
                    "strongman_competition" -> levels[Skills.STRENGTH] ?: 1
                    "wizards_duel"          -> levels[Skills.MAGIC]    ?: 1
                    "fishing_derby"         -> levels[Skills.FISHING]  ?: 1
                    else                    -> 1
                }
                val result = CarnivalSimulator.simulate(
                    activityKey        = action.activityKey,
                    relevantSkillLevel = relevantSkillLevel,
                    petBoostPct        = gatheringPetBoost(player.pets, CarnivalSimulator.relevantSkill(action.activityKey)),
                    agilityLevel       = agilityLevel,
                    fairgroundsTier    = flags.townBuildingTiers["fairgrounds"] ?: 0,
                )
                startSession(action, result, offline, backdateMs)
            }
        }
    }

    private suspend fun startSession(action: QueuedAction, result: SkillSimulator.Result, offline: Boolean = false, backdateMs: Long = 0L) {
        sessionRepo.startSession(
            skillName         = action.skillName,
            activityKey       = action.activityKey,
            frames            = encodeFrames(result.frames),
            durationMs        = result.durationMs,
            skillDisplayName  = action.skillDisplayName,
            insertAsCompleted = offline,
            backdateMs        = backdateMs,
        )
    }

    private fun encodeFrames(frames: List<SessionFrame>): String =
        json.encodeToString(json.serializersModule.serializer<List<SessionFrame>>(), frames)

    /**
     * Returns the total food consumed by the most recent player session if it is
     * completed but not yet collected (food not yet deducted from inventory).
     * Used so the next queued combat session doesn't get the full pre-battle food supply.
     */
    private suspend fun pendingFoodConsumed(): Map<String, Int> {
        val session = sessionRepo.getActiveSession() ?: return emptyMap()
        if (!session.completed || session.skillName !in listOf("combat", "boss")) return emptyMap()
        val frames = try { json.decodeFromString<List<SessionFrame>>(session.frames) } catch (_: Exception) { return emptyMap() }
        val result = mutableMapOf<String, Int>()
        for (frame in frames) frame.foodConsumed.forEach { (k, v) -> result[k] = (result[k] ?: 0) + v }
        return result
    }

    private fun gatheringPetBoost(petsJson: String, skillKey: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        val id   = pets.firstOrNull { gameData.pets[it.id]?.boostedSkill == skillKey || gameData.pets[it.id]?.boostedSkill == "all" } ?: return 0
        return gameData.pets[id.id]?.boostPercent ?: 0
    }

    private fun combatPetBoost(petsJson: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        val id   = pets.firstOrNull { gameData.pets[it.id]?.boostedSkill in Skills.COMBAT || gameData.pets[it.id]?.boostedSkill == "all" } ?: return 0
        return gameData.pets[id.id]?.boostPercent ?: 0
    }

    private fun petDropKey(skillKey: String): String? =
        gameData.pets.values.firstOrNull { it.boostedSkill == skillKey }?.id

    private fun petDropChance(skillKey: String): Double =
        if (gameData.pets.values.any { it.boostedSkill == skillKey }) 1.0 / 1000.0 else 0.0

    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

    private fun tierIndex(level: Int): Int = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    private fun toolEfficiency(itemKey: String?, slot: String, resourceLevelRequired: Int = 0): Float {
        if (itemKey == null) return 1.0f
        val eq   = gameData.equipment[itemKey] ?: return 1.0f
        val base = when (slot) {
            EquipSlot.PICKAXE     -> eq.miningEfficiency      ?: 1.0f
            EquipSlot.AXE         -> eq.woodcuttingEfficiency ?: 1.0f
            EquipSlot.FISHING_ROD -> eq.fishingEfficiency     ?: 1.0f
            else                  -> 1.0f
        }
        if (resourceLevelRequired <= 0) return base
        val skillKey = when (slot) {
            EquipSlot.PICKAXE     -> Skills.MINING
            EquipSlot.AXE         -> Skills.WOODCUTTING
            EquipSlot.FISHING_ROD -> Skills.FISHING
            else                  -> return base
        }
        val toolReqLevel = eq.requirements[skillKey] ?: 1
        val tierDiff     = tierIndex(toolReqLevel) - tierIndex(resourceLevelRequired)
        return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
    }

    private fun buildCraftFrames(
        startXp: Long,
        qty: Int,
        xpPerItem: Double,
        outputQty: Int,
        outputKey: String,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        random: Random = Random.Default,
    ): List<SessionFrame> {
        var xp = startXp
        val frameCount = minOf(qty, 60)
        val frames = mutableListOf<SessionFrame>()
        for (bucket in 0 until frameCount) {
            val itemsInBucket = ((bucket.toLong() + 1) * qty / frameCount - bucket.toLong() * qty / frameCount).toInt()
            val levelBefore = XpTable.levelForXp(xp)
            val gain = (xpPerItem * itemsInBucket).toInt()
            xp += gain
            val levelAfter = XpTable.levelForXp(xp)
            frames.add(SessionFrame(
                minute = bucket + 1, xpGain = gain, xpBefore = xp - gain, xpAfter = xp,
                levelBefore = levelBefore, levelAfter = levelAfter,
                items = mapOf(outputKey to outputQty * itemsInBucket),
                leveledUp = levelAfter > levelBefore,
                kills = itemsInBucket,
            ))
        }
        if (petDropKey != null && petDropChance > 0.0 && frames.isNotEmpty()) {
            val dropped = (0 until 60).any { random.nextDouble() < petDropChance }
            if (dropped) {
                val last = frames.last()
                frames[frames.size - 1] = last.copy(items = last.items + (petDropKey to 1))
            }
        }
        return frames
    }

    private fun ashForLog(logKey: String): String = when (logKey) {
        "oak_log"     -> "oak_ashes"
        "willow_log"  -> "willow_ashes"
        "maple_log"   -> "maple_ashes"
        "yew_log"     -> "yew_ashes"
        "magic_log"   -> "magic_ashes"
        "redwood_log" -> "redwood_ashes"
        else          -> "ashes"
    }

    private fun ashRuneBonus(ashKey: String): Int = when (ashKey) {
        "ashes"         -> 1
        "oak_ashes"     -> 2
        "willow_ashes"  -> 3
        "maple_ashes"   -> 4
        "yew_ashes"     -> 5
        "magic_ashes"   -> 6
        "redwood_ashes" -> 7
        else            -> 0
    }

    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    private val COMBAT_CAPE_SKILLS = setOf(
        "attack", "strength", "defense", "ranged", "magic", "hp",
        "warriors", "archers", "mages",
    )

    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 0,
        "iron_arrow"       to 2,
        "steel_arrow"      to 4,
        "mithril_arrow"    to 6,
        "adamantite_arrow" to 8,
        "runite_arrow"     to 10,
    )
}
