package com.fantasyidler.repository

import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EnemySpawn
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
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.toolEfficiency
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a Tower floor can't start yet because an earlier floor is pending collection. */
private class TowerPendingCollectionException : Exception()

/**
 * Starts the next queued session using current player state.
 * Shared between ViewModels (on collect) and [com.fantasyidler.receiver.SessionAlarmReceiver]
 * (background auto-advance).
 */
@Singleton
class QueuedSessionStarter @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val townRepo: TownRepository,
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
        return playerRepo.playerMutex.withLock {
            mutex.withLock {
                val current = sessionRepo.getActiveSession()
                if (current != null && !current.completed) return@withLock false
                // A Tower floor blocked on pending collection is skipped and stashed rather
                // than parked at the front — otherwise it would permanently block every other
                // queued item behind it (issue #977). Bounded by the queue's own size so an
                // all-tower queue can't loop forever.
                val skippedTowerActions = mutableListOf<QueuedAction>()
                var maxAttempts = playerRepo.getFlags().sessionQueue.size
                while (maxAttempts-- >= 0) {
                    val next = playerRepo.dequeueNextActionUnlocked() ?: break
                    try {
                        startQueuedAction(next, backdateMs = backdateMs)
                        for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                        return@withLock true
                    } catch (_: TowerPendingCollectionException) {
                        skippedTowerActions += next
                    } catch (_: Exception) {
                        playerRepo.requeueActionAtFrontUnlocked(next)
                        for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                        return@withLock false
                    }
                }
                for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                false
            }
        }
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
     * provided its estimated duration fits within [remainingMs]. A Tower floor blocked
     * on pending collection is skipped and stashed rather than parked at the front,
     * matching [startNextQueued]'s handling, so it can't stall every other queued item
     * behind it during offline catch-up (issue #1037).
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
            val player = playerRepo.getOrCreatePlayer()
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val flags: PlayerFlags       = json.decodeFromString(player.flags)
            val agilityLevel    = levels[Skills.AGILITY] ?: 1
            val agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0
            val skippedTowerActions = mutableListOf<QueuedAction>()
            var maxAttempts = playerRepo.getFlags().sessionQueue.size
            while (maxAttempts-- >= 0) {
                val next = playerRepo.dequeueNextActionUnlocked() ?: break
                val duration = estimateDuration(next, agilityLevel, agilityPrestige)
                if (duration > remainingMs) {
                    playerRepo.requeueActionAtFrontUnlocked(next)
                    for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                    return 0L
                }
                try {
                    // backdateMs = remainingMs so each fast-forwarded session in the same
                    // catch-up burst gets a distinct startedAt (now - remainingMs), staying
                    // strictly ordered by queue position instead of all colliding on "now".
                    startQueuedAction(next, offline = true, backdateMs = remainingMs)
                    for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                    return duration
                } catch (_: TowerPendingCollectionException) {
                    skippedTowerActions += next
                } catch (_: Exception) {
                    playerRepo.requeueActionAtFrontUnlocked(next)
                    for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
                    return 0L
                }
            }
            for (skipped in skippedTowerActions.asReversed()) playerRepo.requeueActionAtFrontUnlocked(skipped)
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
        // Recorded on the session so collection can detect a mid-session prestige reset
        // (isSkillSessionStillEligible) instead of gating on an unrelated difficulty formula.
        val levelAtStart = when (action.skillName) {
            "boss", "combat", "tower" -> combatLevelFrom(levels)
            "expedition" -> gameData.skillingDungeons[action.activityKey]?.skill?.let { levels[it] } ?: 1
            else -> levels[action.skillName] ?: 1
        }

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
                    toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
                    petDropKey      = petDropKey(Skills.MINING),
                    petDropChance   = petDropChance(Skills.MINING),
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
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
                    toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
                    petDropKey      = petDropKey(Skills.WOODCUTTING),
                    petDropChance   = petDropChance(Skills.WOODCUTTING),
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
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
                    rodEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
                    petDropKey       = petDropKey(Skills.FISHING),
                    petDropChance    = petDropChance(Skills.FISHING),
                    fishingSkillData = gameData.fishingSkillData,
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
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
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, courseData.levelRequired),
                    petDropKey   = petDropKey(Skills.AGILITY),
                    petDropChance = petDropChance(Skills.AGILITY),
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
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
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK], EquipSlot.LOCKPICK, npc.levelRequired),
                )
                sessionRepo.startSession(
                    skillName         = Skills.THIEVING,
                    activityKey       = npcKey,
                    frames            = encodeFrames(result.frames),
                    durationMs        = result.durationMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                )
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val logData = gameData.logs[logKey] ?: return
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val ashKey  = ashForLog(logKey)
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, logData.levelRequired)
                val frames  = buildCraftFrames(xpMap[Skills.FIREMAKING] ?: 0L, qty, logData.xpPerLog.toDouble(), 1, ashKey,
                    efficiency = efficiency,
                    petDropKey = petDropKey(Skills.FIREMAKING), petDropChance = petDropChance(Skills.FIREMAKING))
                val perLogMs = (SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60L / efficiency).toLong()
                sessionRepo.startSession(
                    skillName         = Skills.FIREMAKING,
                    activityKey       = logKey,
                    frames            = encodeFrames(frames),
                    durationMs        = qty.toLong() * perLogMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                )
            }
            Skills.RUNECRAFTING -> {
                val runeKey  = action.activityKey
                val runeData = gameData.runes[runeKey] ?: return
                val qty      = action.qty.takeIf { it > 0 } ?: return
                val ashBonus = action.catalystKey?.let { ashRuneBonus(it) } ?: 0
                // Ash is already consumed at enqueue time (SkillsViewModel.startRunecraftingSession);
                // action.catalystQty carries that amount through for the refund-on-abandon path.
                val ashCost  = action.catalystQty
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
                sessionRepo.startSession(Skills.RUNECRAFTING, runeKey, encodeFrames(frames), qty.toLong() * perEssenceMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs,
                    catalystKey = action.catalystKey, catalystQty = ashCost, levelAtStart = levelAtStart)
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
                    levelAtStart      = levelAtStart,
                )
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.HAMMER], EquipSlot.HAMMER, r.levelRequired)
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    efficiency = efficiency,
                    petDropKey = petDropKey(Skills.SMITHING), petDropChance = petDropChance(Skills.SMITHING))
                val perItemMs = (SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60 / efficiency).toLong()
                sessionRepo.startSession(Skills.SMITHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.FRYING_PAN], EquipSlot.FRYING_PAN, r.levelRequired)
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem,
                    efficiency = efficiency)
                val perItemMs = (SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60 / efficiency).toLong()
                sessionRepo.startSession(Skills.COOKING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName,
                    petDropKey = petDropKey(Skills.FLETCHING), petDropChance = petDropChance(Skills.FLETCHING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.FLETCHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petDropKey = petDropKey(Skills.CRAFTING), petDropChance = petDropChance(Skills.CRAFTING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.CRAFTING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart)
            }
            Skills.CONSTRUCTION -> {
                val r   = gameData.constructionRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CONSTRUCTION] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petDropKey = petDropKey(Skills.CONSTRUCTION), petDropChance = petDropChance(Skills.CONSTRUCTION))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.CONSTRUCTION, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart)
            }
            Skills.HERBLORE -> {
                val r   = gameData.herbloreRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val catalystKey = action.catalystKey
                val outputKey   = if (catalystKey != null) "enhanced_${action.activityKey}" else action.activityKey
                if (catalystKey != null) playerRepo.consumeItemsUnlocked(mapOf(catalystKey to qty))
                val frames    = buildCraftFrames(xpMap[Skills.HERBLORE] ?: 0L, qty, r.xpPerItem, r.outputQuantity, outputKey,
                    petDropKey = petDropKey(Skills.HERBLORE), petDropChance = petDropChance(Skills.HERBLORE))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige) / 60
                sessionRepo.startSession(Skills.HERBLORE, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs,
                    catalystKey = catalystKey, catalystQty = if (catalystKey != null) qty else 0, levelAtStart = levelAtStart)
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
                    levelAtStart      = levelAtStart,
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
                    playerRepo.consumeItemsUnlocked(mapOf(action.potionKey to 1))
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
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[bossEquipped[it]]?.rangedStrengthBonus ?: 0 } + (bossWeapon?.rangedStrengthBonus ?: 0)
                } else 0
                val equippedFoodKeys  = flags.equippedFood.keys
                val prevFoodConsumed  = pendingFoodConsumed()
                val availableFood     = inventory.filterKeys { it in equippedFoodKeys }
                    .mapValues { (k, v) -> (v - (prevFoodConsumed[k] ?: 0)).coerceAtLeast(0) }
                    .filterValues { it > 0 }
                val spell = gameData.spells[bossSpellName]
                val preferredArrow = bossArrowKey?.takeIf { (inventory[it] ?: 0) > 0 }
                val orderedBossArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedBossArrowKeys.associateWith { inventory[it] ?: 0 }
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
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit        = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows    = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                    attackSpeedSec     = bossWeapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
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
                        val tickMs      = if (lastTicks > 0) frameMs / lastTicks else 2_400L
                        val lastFrameMs = if (lastTicks > 0) minOf(lastTicks * tickMs, frameMs) else frameMs
                        (bossFrames.size - 1).coerceAtLeast(0) * frameMs + lastFrameMs + 2_000L
                    } else null,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                )
            }
            "expedition" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.skillingDungeons[dungeonKey] ?: return
                val toolEfficiency: Float = when (dungeon.skill) {
                    Skills.MINING      -> gameData.toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     dungeon.levelRequired)
                    Skills.WOODCUTTING -> gameData.toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         dungeon.levelRequired)
                    Skills.FISHING     -> gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, dungeon.levelRequired)
                    else               -> 1.0f
                }
                val result = SkillingDungeonSimulator.simulate(
                    dungeonKey      = dungeonKey,
                    dungeon         = dungeon,
                    startXp         = xpMap[dungeon.skill] ?: 0L,
                    agilityLevel    = agilityLevel,
                    agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0,
                    toolEfficiency  = toolEfficiency,
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                val combatEquipped: Map<String, String?> = if (action.equippedSnapshot != null)
                    json.decodeFromString(action.equippedSnapshot) else equipped
                val combatArrowKey  = action.arrowsKey ?: flags.equippedArrows
                val combatSpellName = action.spellName ?: flags.activeSpell
                val combatPotBonuses = if (action.potionKey != null && (inventory[action.potionKey] ?: 0) > 0) {
                    playerRepo.consumeItemsUnlocked(mapOf(action.potionKey to 1))
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
                val orderedCombatArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedCombatArrowKeys.associateWith { inventory[it] ?: 0 }
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
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[combatEquipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
                } else 0
                val staffCoversRune = combatStyle == "magic" && spell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == spell.runeType)
                val queueRuneKey  = if (combatStyle == "magic" && spell != null && !staffCoversRune) spell.runeType else null
                val queueRuneCost = spell?.runeCost ?: 1
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
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    agilityPrestige     = pm[Skills.AGILITY] ?: 0,
                    petBoostPct         = combatPetBoost(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                    availableArrows     = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    runeKey             = queueRuneKey,
                    runeCostPerAttack   = queueRuneCost,
                    availableRunes      = if (queueRuneKey != null) inventory[queueRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
            }
            "tower" -> {
                // towerCurrentFloor only advances on collection, not completion, so starting
                // another floor while one is still sitting completed-but-uncollected would
                // recompute the same floor number below and silently re-run it. Bail out and
                // let the caller (startNextQueued/insertNextQueuedAsOffline) requeue this action
                // at the front to retry once the pending floor is collected.
                if (sessionRepo.getAllCompletedSessions().any { it.skillName == "tower" }) {
                    throw TowerPendingCollectionException()
                }
                // Floors must be attempted contiguously — the queued key is never trusted for
                // the actual floor number, since queue edits (cancel/reorder) could otherwise
                // let a player skip ahead without completing intermediate floors.
                val floor = flags.towerCurrentFloor + 1
                val dungeon = buildTowerFloorDungeon(floor)
                val activeWeaponSlot = flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey   = equipped[activeWeaponSlot]
                val weapon      = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus     = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus     = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
                } else 0
                val preferredArrow  = flags.equippedArrows?.takeIf { (inventory[it] ?: 0) > 0 }
                val orderedTowerArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedTowerArrowKeys.associateWith { inventory[it] ?: 0 }
                val spell           = gameData.spells[flags.activeSpell]
                val equippedFoodKeys = flags.equippedFood.keys
                val prevFoodConsumed = pendingFoodConsumed()
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                    .mapValues { (k, v) -> (v - (prevFoodConsumed[k] ?: 0)).coerceAtLeast(0) }
                    .filterValues { it > 0 }
                val staffCoversRune = combatStyle == "magic" && spell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == spell.runeType)
                val towerRuneKey  = if (combatStyle == "magic" && spell != null && !staffCoversRune) spell.runeType else null
                val towerRuneCost = spell?.runeCost ?: 1
                val pm = flags.skillPrestige
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = scaledTowerEnemies(floor),
                    playerAttack        = ((levels[Skills.ATTACK]   ?: 1) * combatCapeMult).toInt() + (pm[Skills.ATTACK]    ?: 0) * 5,
                    playerStrength      = ((levels[Skills.STRENGTH] ?: 1) * combatCapeMult).toInt() + (pm[Skills.STRENGTH]  ?: 0) * 5,
                    playerDefence       = ((levels[Skills.DEFENSE]  ?: 1) * combatCapeMult).toInt() + totalDefBonus + (pm[Skills.DEFENSE] ?: 0) * 5,
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + (pm[Skills.HITPOINTS] ?: 0) * 5 + flags.towerHpBonus,
                    blessingDefBonus    = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = ((levels[Skills.RANGED] ?: 1) * combatCapeMult).toInt() + (pm[Skills.RANGED] ?: 0) * 5,
                    playerMagic         = ((levels[Skills.MAGIC]  ?: 1) * combatCapeMult).toInt() + (pm[Skills.MAGIC]  ?: 0) * 5,
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    agilityPrestige     = pm[Skills.AGILITY] ?: 0,
                    petBoostPct         = combatPetBoost(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                    availableArrows     = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    runeKey             = towerRuneKey,
                    runeCostPerAttack   = towerRuneCost,
                    availableRunes      = if (towerRuneKey != null) inventory[towerRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )
                sessionRepo.startSession(
                    skillName         = "tower",
                    activityKey       = "tower_floor_$floor",
                    frames            = encodeFrames(result.frames),
                    durationMs        = result.durationMs,
                    skillDisplayName  = "Infinite Tower: Floor $floor",
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                )
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
                    agilityPrestige    = flags.skillPrestige[Skills.AGILITY] ?: 0,
                    tierBonus          = townRepo.idleTicketBonusChance(flags)
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
            }
        }
    }

    private suspend fun startSession(action: QueuedAction, result: SkillSimulator.Result, offline: Boolean = false, backdateMs: Long = 0L, levelAtStart: Int = 0) {
        sessionRepo.startSession(
            skillName         = action.skillName,
            activityKey       = action.activityKey,
            frames            = encodeFrames(result.frames),
            durationMs        = result.durationMs,
            skillDisplayName  = action.skillDisplayName,
            insertAsCompleted = offline,
            backdateMs        = backdateMs,
            levelAtStart      = levelAtStart,
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
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == skillKey || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    private fun combatPetBoost(petsJson: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill in Skills.COMBAT || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    private fun petDropKey(skillKey: String): String? =
        gameData.pets.values.firstOrNull { it.boostedSkill == skillKey }?.id

    private fun petDropChance(skillKey: String): Double =
        if (gameData.pets.values.any { it.boostedSkill == skillKey }) 1.0 / 1000.0 else 0.0

    private fun buildCraftFrames(
        startXp: Long,
        qty: Int,
        xpPerItem: Double,
        outputQty: Int,
        outputKey: String,
        efficiency: Float = 1.0f,
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
            val gain = (xpPerItem * itemsInBucket * efficiency).toInt()
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

    private val FLOOR_TIERS: List<Pair<IntRange, List<EnemySpawn>>> = listOf(
        (1..20)              to listOf(EnemySpawn("goblin", 40), EnemySpawn("skeleton", 30), EnemySpawn("zombie", 30)),
        (21..40)             to listOf(EnemySpawn("orc_warrior", 40), EnemySpawn("dark_wizard", 30), EnemySpawn("bandit", 30)),
        (41..60)             to listOf(EnemySpawn("cave_troll", 35), EnemySpawn("shadow_beast", 35), EnemySpawn("demon", 30)),
        (61..80)             to listOf(EnemySpawn("forge_demon", 35), EnemySpawn("shadow_assassin", 35), EnemySpawn("abyssal_leech", 30)),
        (81..100)            to listOf(EnemySpawn("void_stalker", 35), EnemySpawn("void_guardian", 35), EnemySpawn("abyssal_lord", 30)),
        (101..Int.MAX_VALUE) to listOf(EnemySpawn("void_archon", 35), EnemySpawn("eternal_sentinel", 35), EnemySpawn("abyssal_lord", 30)),
    )

    private fun towerTierFor(floor: Int): List<EnemySpawn> =
        FLOOR_TIERS.firstOrNull { (range, _) -> floor in range }?.second
            ?: FLOOR_TIERS.last().second

    private fun buildTowerFloorDungeon(floor: Int): DungeonData = DungeonData(
        name             = "tower_floor_$floor",
        displayName      = "Floor $floor",
        description      = "Infinite Tower floor $floor",
        recommendedLevel = (floor * 2).coerceAtMost(200),
        encounterRate    = 0.65,
        enemySpawns      = towerTierFor(floor),
    )

    /** Mirrors TowerViewModel.scaledEnemies — keep in sync if the scaling curve changes. */
    private fun scaledTowerEnemies(floor: Int): Map<String, EnemyData> {
        if (floor <= 100) return gameData.enemies
        val t = (floor.coerceIn(101, 250) - 100) / 150f
        val hpMult = 1f + t * 9f
        val statMult = 1f + t * 0.3f
        val relevantKeys = towerTierFor(floor).map { it.enemy }.toSet()
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

    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    private val COMBAT_CAPE_SKILLS = setOf(
        "attack", "strength", "defense", "ranged", "magic", "hp",
        "warriors", "archers", "mages",
    )

    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 7,
        "iron_arrow"       to 10,
        "steel_arrow"      to 16,
        "mithril_arrow"    to 22,
        "adamantite_arrow" to 31,
        "runite_arrow"     to 49,
    )
}
