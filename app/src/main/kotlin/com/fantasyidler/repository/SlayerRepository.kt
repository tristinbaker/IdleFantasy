package com.fantasyidler.repository

import com.fantasyidler.data.model.SlayerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SlayerRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
) {

    /** Pick and assign a random eligible task for the given Slayer level. Returns false if no eligible tasks exist. */
    suspend fun assignTask(slayerLevel: Int, unlockedDungeons: Set<String> = emptySet()): Boolean {
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

        val flags = playerRepo.getFlags()
        playerRepo.updateFlags(
            flags.copy(
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
            playerRepo.updateFlags(
                flags.copy(
                    activeSlayerTask = null,
                    slayerPoints     = flags.slayerPoints + task.taskPoints,
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
