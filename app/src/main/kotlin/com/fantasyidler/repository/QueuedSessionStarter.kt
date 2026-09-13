package com.fantasyidler.repository

import com.fantasyidler.util.withAppLocale

import android.content.Context
import com.fantasyidler.R
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.HeirloomStats
import com.fantasyidler.simulator.CarnivalSimulator
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.MercantileSimulator
import com.fantasyidler.simulator.SkillingDungeonSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.TowerScaling
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.screen.UNLOCK_TOLERANCE
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.toolEfficiency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a Tower floor can't start yet because an earlier floor is pending collection. */
private class TowerPendingCollectionException : Exception()

/** Thrown when the player no longer meets a queued activity's level gate (post-prestige). */
internal class ActionNoLongerQualifiesException : Exception()

/**
 * The level the player must currently have to start [action]'s activity manually, or null
 * when the activity has no known level gate (callers then keep the stricter legacy
 * XP-zeroing rule). Mirrors the manual-start gates: dungeons allow [UNLOCK_TOLERANCE] under
 * their recommended level, lore-unlocked dungeons and raid bosses have no level gate
 * (issue #1542), and Tower floors gate on contiguity, not level.
 */
internal fun queuedActionRequiredLevel(action: QueuedAction, gameData: GameDataRepository): Int? = when (action.skillName) {
    "boss"             -> gameData.bosses[action.activityKey]?.takeIf { !it.raid }?.combatLevelRequired ?: 0
    "combat"           -> gameData.dungeons[action.activityKey]?.takeIf { !it.loreUnlockOnly }?.let { it.recommendedLevel - UNLOCK_TOLERANCE } ?: 0
    "tower"            -> 0
    "expedition"       -> gameData.skillingDungeons[action.activityKey]?.levelRequired ?: 0
    Skills.MINING      -> gameData.ores[action.activityKey]?.levelRequired
    Skills.WOODCUTTING -> gameData.trees[action.activityKey]?.levelRequired
    Skills.FISHING     -> gameData.fish[action.activityKey]?.levelRequired
    Skills.AGILITY     -> gameData.agilityCourses[action.activityKey]?.levelRequired
    Skills.THIEVING    -> gameData.thievingNpcs[action.activityKey]?.levelRequired
    Skills.FIREMAKING  -> gameData.logs[action.activityKey]?.levelRequired
    Skills.RUNECRAFTING -> gameData.runes[action.activityKey]?.levelRequired
    Skills.PRAYER      -> 0
    Skills.SMITHING    -> gameData.smithingRecipes[action.activityKey]?.levelRequired
    Skills.COOKING     -> gameData.cookingRecipes[action.activityKey]?.levelRequired
    Skills.FLETCHING   -> gameData.fletchingRecipes[action.activityKey]?.levelRequired
    Skills.CRAFTING    -> gameData.craftingRecipes[action.activityKey]?.levelRequired
    Skills.CONSTRUCTION -> gameData.constructionRecipes[action.activityKey]?.levelRequired
    Skills.HERBLORE    -> gameData.herbloreRecipes[action.activityKey]?.levelRequired
    Skills.MERCANTILE  -> gameData.tradeRoutes.firstOrNull { it.id == action.activityKey }?.levelRequired
    else               -> null
}

/**
 * Starts the next queued session using current player state.
 * Shared between ViewModels (on collect) and [com.fantasyidler.receiver.SessionAlarmReceiver]
 * (background auto-advance).
 */
@Singleton
class QueuedSessionStarter @Inject constructor(
    private val boostRepo: BoostRepository,
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val townRepo: TownRepository,
    private val gameData: GameDataRepository,
    private val mercRepo: MercenaryRepository,
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
                // A boss queued with a fight-count > 1 (CombatViewModel.startBossSession) isn't
                // re-enqueued as N separate queue entries -- the progress lives in PlayerFlags
                // (activeBossRepeatIndex/Total/Snapshot) instead, so it survives here even while
                // the app is backgrounded (this fires from SessionAlarmReceiver too, not just
                // collectSession). A loss stops the chain; collectSession() still applies that
                // final fight's rewards independently once the app is reopened.
                if (current != null && current.completed && current.skillName == "boss") {
                    val repeatFlags = playerRepo.getFlagsUnlocked()
                    val snapshot = repeatFlags.activeBossRepeatSnapshot
                    if (snapshot != null && repeatFlags.activeBossRepeatIndex < repeatFlags.activeBossRepeatTotal) {
                        val frames: List<SessionFrame> = json.decodeFromString(current.frames)
                        val won = (frames.lastOrNull()?.kills ?: 0) > 0
                        if (won) {
                            try {
                                playerRepo.updateFlagsUnlocked(repeatFlags.copy(activeBossRepeatIndex = repeatFlags.activeBossRepeatIndex + 1))
                                startQueuedAction(snapshot, backdateMs = backdateMs)
                                return@withLock true
                            } catch (_: Exception) {
                                // fall through to clear repeat state below; fight 1's own reward
                                // is still collected normally, this only stops the chain.
                            }
                        }
                    }
                    if (snapshot != null) playerRepo.clearActiveBossRepeatUnlocked()
                }
                // Same idea as the boss repeat chain above, but for dungeon runs queued with a
                // run-count > 1 (CombatViewModel.startDungeonSession). A dungeon run "wins" simply
                // by not dying, unlike a boss fight's kill count.
                if (current != null && current.completed && current.skillName == "combat") {
                    val repeatFlags = playerRepo.getFlagsUnlocked()
                    val snapshot = repeatFlags.activeDungeonRepeatSnapshot
                    if (snapshot != null && repeatFlags.activeDungeonRepeatIndex < repeatFlags.activeDungeonRepeatTotal) {
                        val frames: List<SessionFrame> = json.decodeFromString(current.frames)
                        val survived = frames.lastOrNull()?.died != true
                        if (survived) {
                            try {
                                playerRepo.updateFlagsUnlocked(repeatFlags.copy(activeDungeonRepeatIndex = repeatFlags.activeDungeonRepeatIndex + 1))
                                startQueuedAction(snapshot, backdateMs = backdateMs)
                                return@withLock true
                            } catch (_: Exception) {
                                // fall through to clear repeat state below; this run's own reward
                                // is still collected normally, this only stops the chain.
                            }
                        }
                    }
                    if (snapshot != null) playerRepo.clearActiveDungeonRepeatUnlocked()
                }
                // A Tower floor blocked on pending collection is skipped and stashed rather
                // than parked at the front — otherwise it would permanently block every other
                // queued item behind it (issue #977). Bounded by the queue's own size so an
                // all-tower queue can't loop forever.
                // The scan runs against a local copy of the queue and persists at most once,
                // only when something actually starts. Dequeuing/requeuing through individual
                // DB writes here made the live queue card visibly shrink and rebuild every time
                // this ran while a Tower floor sat uncollected (issue #1183).
                val originalQueue = playerRepo.getFlagsUnlocked().sessionQueue
                var remaining = originalQueue
                val skippedTowerActions = mutableListOf<QueuedAction>()
                var droppedAny = false
                var maxAttempts = originalQueue.size
                while (maxAttempts-- >= 0) {
                    val next = remaining.firstOrNull() ?: break
                    remaining = remaining.drop(1)
                    try {
                        startQueuedAction(next, backdateMs = backdateMs)
                        if (next.skillName == "boss") playerRepo.stampBossRepeatStartUnlocked(next)
                        if (next.skillName == "combat") playerRepo.stampDungeonRepeatStartUnlocked(next)
                        val finalQueue = skippedTowerActions + remaining
                        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(sessionQueue = finalQueue))
                        return@withLock true
                    } catch (_: TowerPendingCollectionException) {
                        skippedTowerActions += next
                    } catch (_: ActionNoLongerQualifiesException) {
                        // The player no longer meets the activity's level gate (a prestige
                        // dropped their level): discard the action and move on (issue #1605).
                        droppedAny = true
                    } catch (_: Exception) {
                        // Nothing was ever written to the DB, so the queue is still exactly
                        // originalQueue -- no requeue needed.
                        return@withLock false
                    }
                }
                if (droppedAny) {
                    playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(
                        sessionQueue = skippedTowerActions + remaining))
                }
                false
            }
        }
    }

    /**
     * Estimates how long [action] would take without running the full simulation.
     * Used to decide whether a queued session fits within remaining catch-up time.
     */
    private fun estimateDuration(action: QueuedAction, agilityLevel: Int, floorReductionMin: Double = 0.0, chronosMult: Float = 1.0f): Long {
        val base = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult)
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
     * Catch-up budget charge for a just-inserted offline session: the ACTUAL length read
     * back from the inserted row, not the nominal estimate. Billing a boss fight its whole
     * durationMinutes starved overnight repeat chains (issue #1664), and billing a
     * tool-boosted craft session its unboosted duration (the estimate ignores frying pan,
     * tinderbox, and hammer efficiency) pushed the rest of the queue hours late after
     * offline catch-up (issue #1737). Clamped to the estimate so the accounting never
     * exceeds what the entry gate approved.
     */
    private suspend fun actualChargeMs(estimateMs: Long): Long {
        val session = sessionRepo.getActiveSession() ?: return estimateMs
        val endMs = if (session.skillName == "boss") sessionRepo.bossFightEndMs(session) else session.endsAt
        return (endMs - session.startedAt).coerceIn(1L, estimateMs)
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
     * Also fast-forwards an in-progress boss/dungeon repeat chain fight-by-fight, the same way
     * a normal queued session is fast-forwarded -- otherwise a long repeat chain left running in
     * the background could only ever advance one fight per real alarm delivery, which Doze can
     * defer for hours, silently stalling e.g. a 100-fight chain after just one or two wins
     * (issue #1189).
     *
     * Called from [SessionRepository.recoverActiveSession] to reconstruct offline progress.
     */
    suspend fun insertNextQueuedAsOffline(remainingMs: Long): Long {
        return playerRepo.playerMutex.withLock {
            mutex.withLock {
                val player = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
                val flags: PlayerFlags       = json.decodeFromString(player.flags)
                val agilityLevel    = levels[Skills.AGILITY] ?: 1
                val floorReductionMin = boostRepo.sessionFloorReductionMin(flags)
                val chronosMult     = townRepo.playerSessionDurationMultiplier(flags)
                // A boss repeat run (queued as one entry, tracked via PlayerFlags rather than N
                // separate queue entries) is advanced here one fight at a time, same as the live
                // (non-offline) chain in startNextQueued() -- returning before ever reaching the
                // sessionQueue scan below preserves the "don't let another queue item jump an
                // in-progress chain" guarantee from issue #1167.
                if (flags.activeBossRepeatSnapshot != null && flags.activeBossRepeatIndex < flags.activeBossRepeatTotal) {
                    val current = sessionRepo.getActiveSession()
                    if (current == null || !current.completed || current.skillName != "boss") return@withLock 0L
                    val won = (json.decodeFromString<List<SessionFrame>>(current.frames).lastOrNull()?.kills ?: 0) > 0
                    if (!won) {
                        playerRepo.clearActiveBossRepeatUnlocked()
                        return@withLock 0L
                    }
                    val snapshot = flags.activeBossRepeatSnapshot!!
                    val duration = estimateDuration(snapshot, agilityLevel, floorReductionMin, chronosMult)
                    if (duration > remainingMs) return@withLock 0L
                    return@withLock try {
                        startQueuedAction(snapshot, offline = true, backdateMs = remainingMs)
                        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(activeBossRepeatIndex = flags.activeBossRepeatIndex + 1))
                        actualChargeMs(duration)
                    } catch (_: Exception) {
                        playerRepo.clearActiveBossRepeatUnlocked()
                        0L
                    }
                }
                // Same idea as the boss repeat chain above, but for dungeon runs (issue #1167 / #1189).
                if (flags.activeDungeonRepeatSnapshot != null && flags.activeDungeonRepeatIndex < flags.activeDungeonRepeatTotal) {
                    val current = sessionRepo.getActiveSession()
                    if (current == null || !current.completed || current.skillName != "combat") return@withLock 0L
                    val survived = json.decodeFromString<List<SessionFrame>>(current.frames).lastOrNull()?.died != true
                    if (!survived) {
                        playerRepo.clearActiveDungeonRepeatUnlocked()
                        return@withLock 0L
                    }
                    val snapshot = flags.activeDungeonRepeatSnapshot!!
                    val duration = estimateDuration(snapshot, agilityLevel, floorReductionMin, chronosMult)
                    if (duration > remainingMs) return@withLock 0L
                    return@withLock try {
                        startQueuedAction(snapshot, offline = true, backdateMs = remainingMs)
                        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(activeDungeonRepeatIndex = flags.activeDungeonRepeatIndex + 1))
                        duration
                    } catch (_: Exception) {
                        playerRepo.clearActiveDungeonRepeatUnlocked()
                        0L
                    }
                }
                // Same reasoning as startNextQueued(): scan a local copy of the queue and persist
                // at most once, only when something actually starts (issue #1183).
                val originalQueue = flags.sessionQueue
                var remaining = originalQueue
                val skippedTowerActions = mutableListOf<QueuedAction>()
                var droppedAny = false
                var maxAttempts = originalQueue.size
                while (maxAttempts-- >= 0) {
                    val next = remaining.firstOrNull() ?: break
                    remaining = remaining.drop(1)
                    val duration = estimateDuration(next, agilityLevel, floorReductionMin, chronosMult)
                    if (duration > remainingMs) return@withLock 0L
                    try {
                        // backdateMs = remainingMs so each fast-forwarded session in the same
                        // catch-up burst gets a distinct startedAt (now - remainingMs), staying
                        // strictly ordered by queue position instead of all colliding on "now".
                        startQueuedAction(next, offline = true, backdateMs = remainingMs)
                        if (next.skillName == "boss") playerRepo.stampBossRepeatStartUnlocked(next)
                        if (next.skillName == "combat") playerRepo.stampDungeonRepeatStartUnlocked(next)
                        val finalQueue = skippedTowerActions + remaining
                        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(sessionQueue = finalQueue))
                        return@withLock actualChargeMs(duration)
                    } catch (_: TowerPendingCollectionException) {
                        skippedTowerActions += next
                    } catch (_: ActionNoLongerQualifiesException) {
                        // Same as startNextQueued(): the player no longer meets the activity's
                        // level gate (a prestige dropped their level) — discard and move on
                        // (issue #1605).
                        droppedAny = true
                    } catch (_: Exception) {
                        // Nothing was ever written to the DB, so the queue is still exactly
                        // originalQueue -- no requeue needed.
                        return@withLock 0L
                    }
                }
                if (droppedAny) {
                    playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(
                        sessionQueue = skippedTowerActions + remaining))
                }
                0L
            }
        }
    }

    /**
     * Debug helper: completes the active session and fast-forwards any remaining
     * boss/dungeon repeat runs as completed sessions so Collect gathers all of them.
     */
    suspend fun debugFinishActiveSessionWithRepeats() {
        playerRepo.playerMutex.withLock {
            mutex.withLock {
                sessionRepo.getActiveSession()?.let { if (!it.completed) sessionRepo.markCompleted(it.sessionId) }

                var flags = playerRepo.getFlagsUnlocked()
                val snapshot = flags.activeBossRepeatSnapshot ?: flags.activeDungeonRepeatSnapshot ?: return@withLock
                val isBoss = flags.activeBossRepeatSnapshot != null
                val remaining = (if (isBoss) flags.activeBossRepeatTotal - flags.activeBossRepeatIndex
                                 else flags.activeDungeonRepeatTotal - flags.activeDungeonRepeatIndex).coerceAtLeast(0)

                repeat(remaining.coerceAtMost(250)) { i ->
                    try {
                        startQueuedAction(snapshot, offline = true, backdateMs = (remaining - i).toLong() * 86_400_000L)
                        flags = if (isBoss) flags.copy(activeBossRepeatIndex = flags.activeBossRepeatIndex + 1)
                                else flags.copy(activeDungeonRepeatIndex = flags.activeDungeonRepeatIndex + 1)
                        playerRepo.updateFlagsUnlocked(flags)
                    } catch (_: Exception) {
                        return@withLock
                    }
                }
            }
        }
    }

    private suspend fun startQueuedAction(action: QueuedAction, offline: Boolean = false, backdateMs: Long = 0L) {
        val player    = playerRepo.getOrCreatePlayer()
        val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
        val xpMap:    Map<String, Long>    = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val equipMap = HeirloomStats.resolveAll(gameData.equipment, levels, flags.heirloomXp)
        val agilityLevel    = levels[Skills.AGILITY] ?: 1
        val floorReductionMin = boostRepo.sessionFloorReductionMin(flags)
        val chronosMult     = townRepo.playerSessionDurationMultiplier(flags)
        val equippedCapeData = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        val attackCapeMult   = resolveCapeMultiplier("attack", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        val strengthCapeMult = resolveCapeMultiplier("strength", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        val defenseCapeMult  = resolveCapeMultiplier("defense", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        val rangedCapeMult   = resolveCapeMultiplier("ranged", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        val magicCapeMult    = resolveCapeMultiplier("magic", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        val prayerCapeMult   = resolveCapeMultiplier("prayer", equippedCapeData, inventory.keys, flags.townBuildingTiers, boostRepo.capeScalingBySkill(flags), gameData.equipment, flags.ironman)
        // Recorded on the session so collection can detect a mid-session prestige reset
        // (isSkillSessionStillEligible) instead of gating on an unrelated difficulty formula.
        // A prestige between queueing and starting is gated like a manual start (issue #1605):
        // still qualifying at the current level means the session runs and pays out at that
        // level (it simulates with post-prestige stats, so nothing is laundered); no longer
        // qualifying drops the action. Activities without a known level gate keep the
        // queue-time floor, so their pre-prestige plans still pay out with XP zeroed.
        val currentLevel = when (action.skillName) {
            "boss", "combat", "tower" -> combatLevelFrom(levels)
            "expedition" -> gameData.skillingDungeons[action.activityKey]?.skill?.let { levels[it] } ?: 1
            else -> levels[action.skillName] ?: 1
        }
        val requiredLevel = queuedActionRequiredLevel(action, gameData)
        val levelAtStart = when {
            requiredLevel == null        -> maxOf(currentLevel, action.levelAtQueue)
            currentLevel < requiredLevel -> throw ActionNoLongerQualifiesException()
            else                         -> currentLevel
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
                    floorReductionMin = floorReductionMin,
                    petBoostPct     = boostRepo.boostedPetPct(Skills.MINING, flags, gatheringPetBoost(player.pets, Skills.MINING, flags.ironman)),
                    toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.MINING, flags, levels[Skills.MINING] ?: 1),
                    petDropKey      = petDropKey(Skills.MINING),
                    petDropChance   = petDropChance(Skills.MINING),
                    chronosMultiplier = chronosMult,
                    gemChanceMult   = boostRepo.bonusRollMultiplier(Skills.MINING, flags),
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
                    floorReductionMin = floorReductionMin,
                    petBoostPct     = boostRepo.boostedPetPct(Skills.WOODCUTTING, flags, gatheringPetBoost(player.pets, Skills.WOODCUTTING, flags.ironman)),
                    toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.WOODCUTTING, flags, levels[Skills.WOODCUTTING] ?: 1),
                    petDropKey      = petDropKey(Skills.WOODCUTTING),
                    petDropChance   = petDropChance(Skills.WOODCUTTING),
                    chronosMultiplier = chronosMult,
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
                    floorReductionMin  = floorReductionMin,
                    petBoostPct      = boostRepo.boostedPetPct(Skills.FISHING, flags, gatheringPetBoost(player.pets, Skills.FISHING, flags.ironman)),
                    rodEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.FISHING, flags, levels[Skills.FISHING] ?: 1),
                    petDropKey       = petDropKey(Skills.FISHING),
                    petDropChance    = petDropChance(Skills.FISHING),
                    fishingSkillData = gameData.fishingSkillData,
                    chronosMultiplier = chronosMult,
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
                    floorReductionMin = floorReductionMin,
                    petBoostPct  = boostRepo.boostedPetPct(Skills.AGILITY, flags, gatheringPetBoost(player.pets, Skills.AGILITY, flags.ironman)),
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, courseData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp),
                    petDropKey   = petDropKey(Skills.AGILITY),
                    petDropChance = petDropChance(Skills.AGILITY),
                    chronosMultiplier = chronosMult,
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
                    floorReductionMin = floorReductionMin,
                    petBoostPct   = boostRepo.boostedPetPct(Skills.THIEVING, flags, gatheringPetBoost(player.pets, Skills.THIEVING, flags.ironman)),
                    petDropKey    = petDropKey(Skills.THIEVING),
                    petDropChance = petDropChance(Skills.THIEVING),
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK], EquipSlot.LOCKPICK, npc.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp),
                    chronosMultiplier = chronosMult,
                    successBonus = boostRepo.thievingSuccessBonus(flags),
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
                 playerMutexHeld = true,)
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val logData = gameData.logs[logKey] ?: return
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val ashKey  = ashForLog(logKey)
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, logData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                val frames  = buildCraftFrames(xpMap[Skills.FIREMAKING] ?: 0L, qty, logData.xpPerLog.toDouble(), 1, ashKey,
                    efficiency = efficiency,
                    petDropKey = petDropKey(Skills.FIREMAKING), petDropChance = petDropChance(Skills.FIREMAKING))
                val perLogMs = (SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60L / efficiency).toLong()
                sessionRepo.startSession(
                    skillName         = Skills.FIREMAKING,
                    activityKey       = logKey,
                    frames            = encodeFrames(frames),
                    durationMs        = qty.toLong() * perLogMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                 playerMutexHeld = true,)
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
                val frameCount = minOf(qty, 60)
                val frames = mutableListOf<SessionFrame>().also { list ->
                    var xp = currentXp
                    for (bucket in 0 until frameCount) {
                        val itemsInBucket = ((bucket.toLong() + 1) * qty / frameCount - bucket.toLong() * qty / frameCount).toInt()
                        val levelBefore = XpTable.levelForXp(xp)
                        var bucketGain = 0
                        var bucketRunes = 0
                        repeat(itemsInBucket) {
                            val level = XpTable.levelForXp(xp)
                            val multiplier = when {
                                level >= 75 -> 3
                                level >= 50 -> 2
                                else         -> 1
                            } + ashBonus
                            val gain = (runeData.xpPerRune * multiplier).toInt()
                            xp += gain
                            bucketGain += gain
                            bucketRunes += multiplier
                        }
                        val levelAfter = XpTable.levelForXp(xp)
                        list.add(SessionFrame(
                            minute      = bucket + 1,
                            xpGain      = bucketGain,
                            xpBefore    = xp - bucketGain,
                            xpAfter     = xp,
                            levelBefore = levelBefore,
                            levelAfter  = levelAfter,
                            items       = mapOf(runeKey to bucketRunes),
                            leveledUp   = levelAfter > levelBefore,
                            kills       = itemsInBucket,
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
                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(Skills.RUNECRAFTING, runeKey, encodeFrames(frames), qty.toLong() * perEssenceMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs,
                    catalystKey = action.catalystKey, catalystQty = ashCost, levelAtStart = levelAtStart, playerMutexHeld = true)
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
                val perBoneMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(
                    skillName         = Skills.PRAYER,
                    activityKey       = boneKey,
                    frames            = encodeFrames(frames),
                    durationMs        = qty.toLong() * perBoneMs,
                    skillDisplayName  = action.skillDisplayName,
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                 playerMutexHeld = true,)
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.HAMMER], EquipSlot.HAMMER, r.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    efficiency = efficiency, petBoostPct = boostRepo.boostedPetPct(Skills.SMITHING, flags, gatheringPetBoost(player.pets, Skills.SMITHING, flags.ironman)),
                    petDropKey = petDropKey(Skills.SMITHING), petDropChance = petDropChance(Skills.SMITHING))
                val perItemMs = (SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60 / efficiency).toLong()
                sessionRepo.startSession(Skills.SMITHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val efficiency = gameData.toolEfficiency(equipped[EquipSlot.FRYING_PAN], EquipSlot.FRYING_PAN, r.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem,
                    efficiency = efficiency, petBoostPct = boostRepo.boostedPetPct(Skills.COOKING, flags, gatheringPetBoost(player.pets, Skills.COOKING, flags.ironman)))
                val perItemMs = (SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60 / efficiency).toLong()
                sessionRepo.startSession(Skills.COOKING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName,
                    petBoostPct = boostRepo.boostedPetPct(Skills.FLETCHING, flags, gatheringPetBoost(player.pets, Skills.FLETCHING, flags.ironman)),
                    petDropKey = petDropKey(Skills.FLETCHING), petDropChance = petDropChance(Skills.FLETCHING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(Skills.FLETCHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petBoostPct = boostRepo.boostedPetPct(Skills.CRAFTING, flags, gatheringPetBoost(player.pets, Skills.CRAFTING, flags.ironman)),
                    petDropKey = petDropKey(Skills.CRAFTING), petDropChance = petDropChance(Skills.CRAFTING))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(Skills.CRAFTING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.CONSTRUCTION -> {
                val r   = gameData.constructionRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CONSTRUCTION] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    petBoostPct = boostRepo.boostedPetPct(Skills.CONSTRUCTION, flags, gatheringPetBoost(player.pets, Skills.CONSTRUCTION, flags.ironman)),
                    petDropKey = petDropKey(Skills.CONSTRUCTION), petDropChance = petDropChance(Skills.CONSTRUCTION))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(Skills.CONSTRUCTION, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.HERBLORE -> {
                val r   = gameData.herbloreRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val catalystKey = action.catalystKey
                val outputKey   = if (catalystKey != null) "enhanced_${action.activityKey}" else action.activityKey
                // Ash is already consumed at enqueue time (CraftingViewModel);
                // action.catalystQty carries that amount through for the refund-on-abandon path.
                val ashCost   = action.catalystQty
                val frames    = buildCraftFrames(xpMap[Skills.HERBLORE] ?: 0L, qty, r.xpPerItem, r.outputQuantity, outputKey,
                    petBoostPct = boostRepo.boostedPetPct(Skills.HERBLORE, flags, gatheringPetBoost(player.pets, Skills.HERBLORE, flags.ironman)),
                    petDropKey = petDropKey(Skills.HERBLORE), petDropChance = petDropChance(Skills.HERBLORE))
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60
                sessionRepo.startSession(Skills.HERBLORE, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName, insertAsCompleted = offline, backdateMs = backdateMs,
                    catalystKey = catalystKey, catalystQty = ashCost, levelAtStart = levelAtStart, playerMutexHeld = true)
            }
            Skills.MERCANTILE -> {
                val route = gameData.tradeRoutes.firstOrNull { it.id == action.activityKey } ?: return
                val result = MercantileSimulator.simulate(
                    route           = route,
                    startXp         = xpMap[Skills.MERCANTILE] ?: 0L,
                    agilityLevel    = agilityLevel,
                    floorReductionMin = floorReductionMin,
                    petDropKey    = petDropKey(Skills.MERCANTILE),
                    petDropChance = petDropChance(Skills.MERCANTILE),
                    chronosMultiplier = chronosMult,
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
                 playerMutexHeld = true,)
            }
            "boss" -> {
                val bossKey = action.activityKey
                val boss    = gameData.bosses[bossKey] ?: return
                // Gear reads live at start so armor changes made while queued apply (issue
                // #1430); only the entry's explicit combat picks (weapon slot, spell,
                // arrows, potion) are honored from queue time.
                val bossEquipped: Map<String, String?> = equipped
                val bossArrowKey  = action.arrowsKey ?: flags.equippedArrows
                val bossSpellName = action.spellName ?: flags.activeSpell
                val bossPotionBonuses = if (action.potionKey != null && (inventory[action.potionKey] ?: 0) > 0) {
                    playerRepo.consumeItemsUnlocked(mapOf(action.potionKey to 1))
                    boostRepo.boostedPotionEffects(flags, gameData.potionEffects[action.potionKey] ?: emptyMap())
                } else emptyMap()
                val bossWeaponSlot = action.weaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { bossEquipped[it] != null }
                    ?: EquipSlot.WEAPON
                val bossWeapon = bossEquipped[bossWeaponSlot]?.let { equipMap[it] }
                val combatStyle = when (bossWeapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "melee"
                }
                val totalAtkBonus    = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = equipMap[bossEquipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> bossWeapon?.rangedAttackBonus ?: bossWeapon?.attackBonus ?: 0; "magic" -> bossWeapon?.magicAttackBonus ?: 0; else -> bossWeapon?.attackBonus ?: 0 }
                val totalStrBonus    = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[bossEquipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val totalDefBonus    = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[bossEquipped[it]]?.defenseBonus  ?: 0 } + (bossWeapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { equipMap[bossEquipped[it]]?.magicDamageBonus ?: 0 } + (bossWeapon?.magicDamageBonus ?: 0) else 0
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { equipMap[bossEquipped[it]]?.rangedStrengthBonus ?: 0 } + (bossWeapon?.rangedStrengthBonus ?: 0)
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
                                val bossFrames = CombatSimulator.simulateBoss(
                    boss               = boss,
                    bossKey            = bossKey,
                    playerAttack       = ((levels[Skills.ATTACK]   ?: 1) * attackCapeMult).toInt() + boostRepo.combatStatBonus(Skills.ATTACK, flags, levels[Skills.ATTACK] ?: 1) + (bossPotionBonuses["attack"]   ?: 0),
                    playerStrength     = ((levels[Skills.STRENGTH] ?: 1) * strengthCapeMult).toInt() + boostRepo.combatStatBonus(Skills.STRENGTH, flags, levels[Skills.STRENGTH] ?: 1) + (bossPotionBonuses["strength"] ?: 0),
                    playerDefence      = ((levels[Skills.DEFENSE]  ?: 1) * defenseCapeMult).toInt() + totalDefBonus + boostRepo.combatStatBonus(Skills.DEFENSE, flags, levels[Skills.DEFENSE] ?: 1) + (bossPotionBonuses["defense"] ?: 0),
                    playerHp           = (levels[Skills.HITPOINTS] ?: 1) + boostRepo.combatStatBonus(Skills.HITPOINTS, flags, levels[Skills.HITPOINTS] ?: 1) + flags.towerHpBonus,
                    weaponAttackBonus  = totalAtkBonus,
                    weaponStrBonus     = totalStrBonus,
                    combatStyle        = combatStyle,
                    playerRanged       = ((levels[Skills.RANGED] ?: 1) * rangedCapeMult).toInt() + boostRepo.combatStatBonus(Skills.RANGED, flags, levels[Skills.RANGED] ?: 1) + (bossPotionBonuses["ranged"] ?: 0),
                    playerMagic        = ((levels[Skills.MAGIC]  ?: 1) * magicCapeMult).toInt() + boostRepo.combatStatBonus(Skills.MAGIC, flags, levels[Skills.MAGIC] ?: 1) + (bossPotionBonuses["magic"]  ?: 0),
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit        = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows    = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    equippedFood       = availableFood,
                    foodHealValues     = boostRepo.boostedFoodHeal(flags, gameData.foodHealValues),
                    blessingDefBonus   = ChurchRepository.defBonus(flags, prayerCapeMult, gameData.blessings),
                    attackSpeedSec     = bossWeapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                    eatThresholdPct    = flags.foodEatThresholdPct,
                    foodEatOrder       = flags.foodEatOrder,
                    doubleHitChance     = boostRepo.doubleHitChance(flags),
                    secondChance        = boostRepo.secondChanceActive(flags),
                    // Queued raids use the contracts valid when the session actually starts,
                    // matching how queued sessions already use the current armor.
                    mercenaries         = if (boss.raid) mercRepo.combatants(flags) else emptyList(),
                    blockedRareDrops    = HeirloomStats.ownedHeirloomKeys(gameData.equipment, inventory) + sessionRepo.pendingHeirloomKeys(),
                )
                val frameMs        = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult) / 60L
                val bossDurationMs = boss.durationMinutes * frameMs
                sessionRepo.startSession(
                    skillName         = "boss",
                    activityKey       = bossKey,
                    frames            = encodeFrames(bossFrames),
                    durationMs        = bossDurationMs,
                    skillDisplayName  = action.skillDisplayName,
                    // endsAt is cosmetic (full duration, no outcome spoiler); the alarm
                    // ends the session at the exact death tick within the final frame.
                    alarmOffsetMs     = CombatSimulator.bossEndAlarmOffsetMs(bossFrames, boss.durationMinutes, frameMs),
                    insertAsCompleted = offline,
                    backdateMs        = backdateMs,
                    levelAtStart      = levelAtStart,
                    weaponSlot        = bossWeaponSlot,
                 playerMutexHeld = true,)
            }
            "expedition" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.skillingDungeons[dungeonKey] ?: return
                val toolEfficiency: Float = when (dungeon.skill) {
                    Skills.MINING      -> gameData.toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     dungeon.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                    Skills.WOODCUTTING -> gameData.toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         dungeon.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                    Skills.FISHING     -> gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, dungeon.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                    else               -> 1.0f
                }
                val result = SkillingDungeonSimulator.simulate(
                    dungeonKey      = dungeonKey,
                    dungeon         = dungeon,
                    startXp         = xpMap[dungeon.skill] ?: 0L,
                    agilityLevel    = agilityLevel,
                    floorReductionMin = boostRepo.sessionFloorReductionMin(flags),
                    toolEfficiency  = toolEfficiency,
                    chronosMultiplier = chronosMult,
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                // Live gear at start, same as the boss branch (issue #1430).
                val combatEquipped: Map<String, String?> = equipped
                val combatArrowKey  = action.arrowsKey ?: flags.equippedArrows
                val combatSpellName = action.spellName ?: flags.activeSpell
                val combatPotBonuses = if (action.potionKey != null && (inventory[action.potionKey] ?: 0) > 0) {
                    playerRepo.consumeItemsUnlocked(mapOf(action.potionKey to 1))
                    boostRepo.boostedPotionEffects(flags, gameData.potionEffects[action.potionKey] ?: emptyMap())
                } else emptyMap()
                val activeWeaponSlot = action.weaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { combatEquipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey  = combatEquipped[activeWeaponSlot]
                val weapon     = weaponKey?.let { equipMap[it] }
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
                    val eq = equipMap[combatEquipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[combatEquipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[combatEquipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { equipMap[combatEquipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { equipMap[combatEquipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
                } else 0
                val staffCoversRune = combatStyle == "magic" && spell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == spell.runeType)
                val queueRuneKey  = if (combatStyle == "magic" && spell != null && !staffCoversRune) spell.runeType else null
                val queueRuneCost = spell?.runeCost ?: 1
                                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = ((levels[Skills.ATTACK]   ?: 1) * attackCapeMult).toInt() + boostRepo.combatStatBonus(Skills.ATTACK, flags, levels[Skills.ATTACK] ?: 1) + (combatPotBonuses["attack"]   ?: 0),
                    playerStrength      = ((levels[Skills.STRENGTH] ?: 1) * strengthCapeMult).toInt() + boostRepo.combatStatBonus(Skills.STRENGTH, flags, levels[Skills.STRENGTH] ?: 1) + (combatPotBonuses["strength"] ?: 0),
                    playerDefence       = ((levels[Skills.DEFENSE]  ?: 1) * defenseCapeMult).toInt() + totalDefBonus + boostRepo.combatStatBonus(Skills.DEFENSE, flags, levels[Skills.DEFENSE] ?: 1) + (combatPotBonuses["defense"] ?: 0),
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + boostRepo.combatStatBonus(Skills.HITPOINTS, flags, levels[Skills.HITPOINTS] ?: 1) + flags.towerHpBonus,
                    blessingDefBonus    = ChurchRepository.defBonus(flags, prayerCapeMult, gameData.blessings),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = ((levels[Skills.RANGED] ?: 1) * rangedCapeMult).toInt() + boostRepo.combatStatBonus(Skills.RANGED, flags, levels[Skills.RANGED] ?: 1) + (combatPotBonuses["ranged"] ?: 0),
                    playerMagic         = ((levels[Skills.MAGIC]  ?: 1) * magicCapeMult).toInt() + boostRepo.combatStatBonus(Skills.MAGIC, flags, levels[Skills.MAGIC] ?: 1) + (combatPotBonuses["magic"]  ?: 0),
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    floorReductionMin     = boostRepo.sessionFloorReductionMin(flags),
                    petBoostPct         = combatPetBoost(player.pets, flags.ironman),
                    equippedFood        = availableFood,
                    foodHealValues      = boostRepo.boostedFoodHeal(flags, gameData.foodHealValues),
                    availableArrows     = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    runeKey             = queueRuneKey,
                    runeCostPerAttack   = queueRuneCost,
                    availableRunes      = if (queueRuneKey != null) inventory[queueRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                    eatThresholdPct     = flags.foodEatThresholdPct,
                    foodEatOrder        = flags.foodEatOrder,
                    chronosMultiplier   = chronosMult,
                    doubleHitChance     = boostRepo.doubleHitChance(flags),
                    secondChance        = boostRepo.secondChanceActive(flags),
                )
                startSession(action, result, offline, backdateMs, levelAtStart)
            }
            "tower" -> {
                // A won floor sitting uncollected no longer blocks the next attempt -- Tower
                // was otherwise the only skill where finishing a session wasn't enough to keep
                // the queue moving (issue #1183). A death still halts the chain so the player
                // sees it and can decide whether to keep climbing from the checkpoint.
                val pendingTower = sessionRepo.getAllCompletedSessions().lastOrNull { it.skillName == "tower" }
                val pendingFloor = pendingTower?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
                if (pendingTower != null) {
                    val pendingFrames: List<SessionFrame> = json.decodeFromString(pendingTower.frames)
                    if (pendingFrames.any { it.died }) throw TowerPendingCollectionException()
                }
                // Floors must be attempted contiguously — the queued key is never trusted for
                // the actual floor number, since queue edits (cancel/reorder) could otherwise
                // let a player skip ahead without completing intermediate floors. Taken from the
                // pending win itself rather than towerCurrentFloor (which only advances at
                // collection) so consecutive wins keep incrementing correctly.
                val floor = (pendingFloor ?: flags.towerCurrentFloor) + 1
                val dungeon = buildTowerFloorDungeon(floor)
                val activeWeaponSlot = flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey   = equipped[activeWeaponSlot]
                val weapon      = weaponKey?.let { equipMap[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = equipMap[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus     = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus     = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val totalRangedStrBonus = if (combatStyle == "ranged") {
                    EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
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
                                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = scaledTowerEnemies(floor),
                    playerAttack        = ((levels[Skills.ATTACK]   ?: 1) * attackCapeMult).toInt() + boostRepo.combatStatBonus(Skills.ATTACK, flags, levels[Skills.ATTACK] ?: 1),
                    playerStrength      = ((levels[Skills.STRENGTH] ?: 1) * strengthCapeMult).toInt() + boostRepo.combatStatBonus(Skills.STRENGTH, flags, levels[Skills.STRENGTH] ?: 1),
                    playerDefence       = ((levels[Skills.DEFENSE]  ?: 1) * defenseCapeMult).toInt() + totalDefBonus + boostRepo.combatStatBonus(Skills.DEFENSE, flags, levels[Skills.DEFENSE] ?: 1),
                    playerHp            = (levels[Skills.HITPOINTS] ?: 1) + boostRepo.combatStatBonus(Skills.HITPOINTS, flags, levels[Skills.HITPOINTS] ?: 1) + flags.towerHpBonus,
                    blessingDefBonus    = ChurchRepository.defBonus(flags, prayerCapeMult, gameData.blessings),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = ((levels[Skills.RANGED] ?: 1) * rangedCapeMult).toInt() + boostRepo.combatStatBonus(Skills.RANGED, flags, levels[Skills.RANGED] ?: 1),
                    playerMagic         = ((levels[Skills.MAGIC]  ?: 1) * magicCapeMult).toInt() + boostRepo.combatStatBonus(Skills.MAGIC, flags, levels[Skills.MAGIC] ?: 1),
                    rangedGearStrengthBonus = totalRangedStrBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    floorReductionMin     = boostRepo.sessionFloorReductionMin(flags),
                    petBoostPct         = combatPetBoost(player.pets, flags.ironman),
                    equippedFood        = availableFood,
                    foodHealValues      = boostRepo.boostedFoodHeal(flags, gameData.foodHealValues),
                    availableArrows     = availableArrows,
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    runeKey             = towerRuneKey,
                    runeCostPerAttack   = towerRuneCost,
                    availableRunes      = if (towerRuneKey != null) inventory[towerRuneKey] ?: 0 else Int.MAX_VALUE,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                    eatThresholdPct     = flags.foodEatThresholdPct,
                    foodEatOrder        = flags.foodEatOrder,
                    chronosMultiplier   = chronosMult,
                    doubleHitChance     = boostRepo.doubleHitChance(flags),
                    secondChance        = boostRepo.secondChanceActive(flags),
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
                    weaponSlot        = activeWeaponSlot,
                 playerMutexHeld = true,)
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
                    petBoostPct        = gatheringPetBoost(player.pets, CarnivalSimulator.relevantSkill(action.activityKey), flags.ironman),
                    agilityLevel       = agilityLevel,
                    floorReductionMin    = boostRepo.sessionFloorReductionMin(flags),
                    tierBonus          = townRepo.idleTicketBonusChance(flags),
                    chronosMultiplier  = chronosMult,
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
            // Queued dungeon repeats otherwise ran out their full timer after a death,
            // unlike first runs started from CombatViewModel (issue #935). Null for the
            // gathering skills, whose frames never carry a death.
            alarmOffsetMs     = CombatSimulator.deathAlarmOffsetMs(result.frames, result.durationMs / 60L),
            insertAsCompleted = offline,
            backdateMs        = backdateMs,
            levelAtStart      = levelAtStart,
            weaponSlot        = action.weaponSlot,
         playerMutexHeld = true,)
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

    private fun gatheringPetBoost(petsJson: String, skillKey: String, ironman: Boolean = false): Int {
        if (ironman) return 0
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == skillKey || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    private fun combatPetBoost(petsJson: String, ironman: Boolean = false): Int {
        if (ironman) return 0
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
        petBoostPct: Int = 0,
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
            val gain = (xpPerItem * itemsInBucket * efficiency * (1.0 + petBoostPct / 100.0)).toInt()
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


    private fun buildTowerFloorDungeon(floor: Int): DungeonData = DungeonData(
        name             = "tower_floor_$floor",
        displayName      = context.withAppLocale().getString(R.string.tower_floor_label, floor),
        description      = context.withAppLocale().getString(R.string.tower_floor_desc, floor),
        recommendedLevel = (floor * 2).coerceAtMost(200),
        encounterRate    = 0.65,
        enemySpawns      = TowerScaling.tierFor(floor),
    )

    private fun scaledTowerEnemies(floor: Int): Map<String, EnemyData> =
        TowerScaling.scaledEnemies(floor, gameData.enemies)

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
