package com.fantasyidler.repository

import com.fantasyidler.data.model.SlayerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class ForetelResult {
    data class Success(val task: SlayerTask) : ForetelResult()
    object QueueFull : ForetelResult()
    object NoEligibleTasks : ForetelResult()
    data class NotEnoughBones(val costUnits: Int) : ForetelResult()
}

@Singleton
class SlayerRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
) {

    /** Pick and assign a random eligible task for the given Slayer level. Returns false if no eligible tasks exist. */
    suspend fun assignTask(slayerLevel: Int, unlockedDungeons: Set<String> = emptySet()): Boolean {
        val flags = playerRepo.getFlags()
        // Pop the first foretelled task if any exist
        if (flags.foretelledTasks.isNotEmpty()) {
            val next = flags.foretelledTasks.first()
            playerRepo.updateFlags(
                flags.copy(
                    activeSlayerTask = next,
                    foretelledTasks  = flags.foretelledTasks.drop(1),
                )
            )
            return true
        }

        val eligible = gameData.slayerTasks.filter { (enemyKey, cfg) ->
            if (cfg.slayerLevel > slayerLevel) return@filter false
            // Skip enemies that exclusively appear in expedition-locked dungeons the player hasn't unlocked.
            val dungeonKeys = gameData.dungeons.values
                .filter { d -> d.enemySpawns.any { it.enemy == enemyKey } }
                .map { it.name }
            if (dungeonKeys.isEmpty()) return@filter true
            dungeonKeys.any { it !in gameData.expeditionLockedDungeons || it in unlockedDungeons }
        }
        if (eligible.isEmpty()) return false

        val (enemyKey, cfg) = eligible.entries.random()
        val displayName = gameData.enemies[enemyKey]?.displayName
            ?: enemyKey.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val targetKills = Random.nextInt(cfg.minKills, cfg.maxKills + 1)
        val taskPoints  = maxOf(3, cfg.slayerLevel / 5)

        playerRepo.updateFlags(
            playerRepo.getFlags().copy(
                activeSlayerTask = SlayerTask(
                    enemyKey       = enemyKey,
                    displayName    = displayName,
                    targetKills    = targetKills,
                    killsCompleted = 0,
                    xpPerKill      = cfg.xpPerKill,
                    taskPoints     = taskPoints,
                )
            )
        )
        return true
    }

    /**
     * Bone cost (in units, where 1 unit = 10 bone XP = 1 regular bone) for the next foretell slot.
     * Slot 0 (first queued) = 10, slot 1 = 25, slot 2 = 50.
     */
    private fun foretelCostUnits(queueSize: Int): Int = when (queueSize) {
        0    -> 10
        1    -> 25
        else -> 50
    }

    private val BONE_XP = mapOf("dragon_bone" to 80, "giant_bones" to 40, "big_bones" to 20, "bones" to 10)
    private val BONE_TYPES_ORDERED = listOf("bones", "big_bones", "giant_bones", "dragon_bone")
    private val BASE_BONE_XP = 10

    private fun totalBoneXp(inventory: Map<String, Int>): Int =
        BONE_XP.entries.sumOf { (key, xp) -> (inventory[key] ?: 0) * xp }

    /**
     * Pre-assign the next future task by paying bones. Returns false if the queue is full (3),
     * no eligible tasks exist, or the player has insufficient bones. Cost escalates per slot.
     */
    suspend fun foretelTask(slayerLevel: Int, unlockedDungeons: Set<String> = emptySet()): ForetelResult {
        val flags = playerRepo.getFlags()
        if (flags.foretelledTasks.size >= 3) return ForetelResult.QueueFull

        val eligible = gameData.slayerTasks.filter { (enemyKey, cfg) ->
            if (cfg.slayerLevel > slayerLevel) return@filter false
            val dungeonKeys = gameData.dungeons.values
                .filter { d -> d.enemySpawns.any { it.enemy == enemyKey } }
                .map { it.name }
            if (dungeonKeys.isEmpty()) return@filter true
            dungeonKeys.any { it !in gameData.expeditionLockedDungeons || it in unlockedDungeons }
        }
        if (eligible.isEmpty()) return ForetelResult.NoEligibleTasks

        val costUnits = foretelCostUnits(flags.foretelledTasks.size)
        val costXp = costUnits * BASE_BONE_XP
        val inventory = playerRepo.getInventory()
        if (totalBoneXp(inventory) < costXp) return ForetelResult.NotEnoughBones(costUnits)

        // Consume bones greedily cheapest-first
        val toConsume = mutableMapOf<String, Int>()
        var remaining = costXp
        for (boneKey in BONE_TYPES_ORDERED) {
            if (remaining <= 0) break
            val xp   = BONE_XP[boneKey] ?: continue
            val have = inventory[boneKey] ?: 0
            if (have == 0) continue
            val needed  = (remaining + xp - 1) / xp
            val consume = minOf(have, needed)
            toConsume[boneKey] = consume
            remaining -= consume * xp
        }
        playerRepo.consumeItems(toConsume)

        val (enemyKey, cfg) = eligible.entries.random()
        val displayName = gameData.enemies[enemyKey]?.displayName
            ?: enemyKey.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val targetKills = Random.nextInt(cfg.minKills, cfg.maxKills + 1)
        val taskPoints  = maxOf(3, cfg.slayerLevel / 5)
        val task = SlayerTask(
            enemyKey       = enemyKey,
            displayName    = displayName,
            targetKills    = targetKills,
            killsCompleted = 0,
            xpPerKill      = cfg.xpPerKill,
            taskPoints     = taskPoints,
        )

        val latestFlags = playerRepo.getFlags()
        if (latestFlags.activeSlayerTask == null) {
            playerRepo.updateFlags(latestFlags.copy(activeSlayerTask = task))
        } else {
            playerRepo.updateFlags(latestFlags.copy(foretelledTasks = latestFlags.foretelledTasks + task))
        }
        return ForetelResult.Success(task)
    }

    /**
     * Record kills from a dungeon session.
     *
     * Returns Slayer XP earned (0 if no active task or enemy doesn't match).
     * Handles task completion: awards points and records quest progress.
     */
    suspend fun recordKills(enemyKey: String, count: Int): Long {
        if (count <= 0) return 0L
        val flags = playerRepo.getFlags()
        val task  = flags.activeSlayerTask ?: return 0L
        if (task.enemyKey != enemyKey) return 0L

        val added          = minOf(count, task.targetKills - task.killsCompleted)
        if (added <= 0) return 0L
        val xpEarned       = added.toLong() * task.xpPerKill
        val newCompleted   = task.killsCompleted + added

        if (newCompleted >= task.targetKills) {
            val freshFlags = playerRepo.getFlags()  // re-read to get latest foretelledTasks
            val nextTask = freshFlags.foretelledTasks.firstOrNull()
            playerRepo.updateFlags(
                freshFlags.copy(
                    activeSlayerTask = nextTask,
                    foretelledTasks  = if (nextTask != null) freshFlags.foretelledTasks.drop(1) else freshFlags.foretelledTasks,
                    slayerPoints     = freshFlags.slayerPoints + task.taskPoints,
                )
            )
            questRepo.recordSlayerTaskCompleted()
            playerRepo.recordWeeklyProgress("slayer_task", "any", 1)
        } else {
            playerRepo.updateFlags(
                flags.copy(
                    activeSlayerTask = task.copy(killsCompleted = newCompleted)
                )
            )
        }
        return xpEarned
    }

    /**
     * Spend 30 Slayer points to skip the current task and immediately assign a new one.
     * Returns false if the player doesn't have enough points or there are no eligible tasks.
     */
    suspend fun skipTask(slayerLevel: Int, unlockedDungeons: Set<String> = emptySet()): Boolean {
        val flags = playerRepo.getFlags()
        if (flags.slayerPoints < 30) return false
        playerRepo.updateFlags(flags.copy(slayerPoints = flags.slayerPoints - 30, activeSlayerTask = null))
        return assignTask(slayerLevel, unlockedDungeons)
    }

    /**
     * Deduct [cost] Slayer points and add [itemKey] to the player's inventory.
     * Returns false if insufficient points.
     */
    suspend fun spendPointsForItem(itemKey: String, cost: Int): Boolean {
        val flags = playerRepo.getFlags()
        if (flags.slayerPoints < cost) return false
        playerRepo.updateFlags(flags.copy(slayerPoints = flags.slayerPoints - cost))
        playerRepo.addItem(itemKey, 1)
        return true
    }

    /**
     * Deduct [cost] Slayer points and add [xpAmount] XP to [skillKey].
     * Returns false if insufficient points.
     */
    suspend fun spendPointsForXp(skillKey: String, xpAmount: Long, cost: Int): Boolean {
        val flags = playerRepo.getFlags()
        if (flags.slayerPoints < cost) return false
        playerRepo.updateFlags(flags.copy(slayerPoints = flags.slayerPoints - cost))
        playerRepo.applyMultiSkillResults(
            mapOf(skillKey to xpAmount),
            emptyMap(),
            0L,
        )
        return true
    }
}
