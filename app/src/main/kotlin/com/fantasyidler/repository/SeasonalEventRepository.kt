package com.fantasyidler.repository

import com.fantasyidler.data.json.SeasonalBountyTaskData
import com.fantasyidler.data.json.SeasonalEventData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SeasonalBannerEarned
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.withLock

data class SeasonalBountyTaskWithProgress(
    val task: SeasonalBountyTaskData,
    val progress: Int,
    val claimed: Boolean,
)

sealed class SeasonalMinigameResult {
    object NoActiveEvent : SeasonalMinigameResult()
    data class OnCooldown(val resumesAtMs: Long) : SeasonalMinigameResult()
    data class Success(val resumesAtMs: Long) : SeasonalMinigameResult()
    data class Failure(val resumesAtMs: Long) : SeasonalMinigameResult()
}

@Singleton
class SeasonalEventRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
) {

    /** Returns the event whose date window currently contains "now", or null between events. */
    fun activeEvent(): SeasonalEventData? =
        gameData.seasonalEvents.values.firstOrNull { it.isActiveAt(System.currentTimeMillis()) }

    fun bountyTasksWithProgress(event: SeasonalEventData, flags: PlayerFlags): List<SeasonalBountyTaskWithProgress> =
        event.bountyTasks.map { task ->
            SeasonalBountyTaskWithProgress(
                task     = task,
                progress = flags.seasonalBountyProgress[task.id] ?: 0,
                claimed  = task.id in flags.seasonalBountyClaimed,
            )
        }

    // -------------------------------------------------------------------------
    // Bounty Board — mirrors GuildRepository's daily-reset shape
    // -------------------------------------------------------------------------

    suspend fun ensureBountyRefreshed() = playerRepo.playerMutex.withLock { ensureBountyRefreshedUnlocked() }

    private suspend fun ensureBountyRefreshedUnlocked(): PlayerFlags {
        val flags = playerRepo.getFlags()
        if (!shouldRefreshBounty(flags.seasonalBountyGeneratedAt)) return flags
        val refreshed = flags.copy(
            seasonalBountyProgress    = emptyMap(),
            seasonalBountyClaimed     = emptyList(),
            seasonalBountyGeneratedAt = System.currentTimeMillis(),
        )
        playerRepo.updateFlagsUnlocked(refreshed)
        return refreshed
    }

    private fun shouldRefreshBounty(generatedAt: Long): Boolean {
        if (generatedAt == 0L) return true
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = generatedAt }
        cal.set(Calendar.HOUR_OF_DAY, 6)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= generatedAt) cal.add(Calendar.DAY_OF_YEAR, 1)
        return now >= cal.timeInMillis
    }

    /** Called when a gathering session is collected. */
    suspend fun recordGathering(items: Map<String, Int>) = recordBountyProgress("gather", items)

    /** Called when a crafting session is collected. */
    suspend fun recordCrafting(items: Map<String, Int>) = recordBountyProgress("craft", items)

    /** Called when a combat session (dungeon, boss, or tower) is collected. */
    suspend fun recordCombat(killsByEnemy: Map<String, Int>) = recordBountyProgress("kill", killsByEnemy)

    private suspend fun recordBountyProgress(type: String, counts: Map<String, Int>) = playerRepo.playerMutex.withLock {
        val event = activeEvent() ?: return@withLock
        if ("bounty" !in event.pillars) return@withLock
        val flags = ensureBountyRefreshedUnlocked()
        val updated = flags.seasonalBountyProgress.toMutableMap()
        var changed = false
        for (task in event.bountyTasks) {
            if (task.type != type) continue
            if (task.id in flags.seasonalBountyClaimed) continue
            val count = counts[task.target] ?: continue
            if (count <= 0) continue
            val cur = updated[task.id] ?: 0
            if (cur >= task.amount) continue
            updated[task.id] = minOf(cur + count, task.amount)
            changed = true
        }
        if (changed) playerRepo.updateFlagsUnlocked(flags.copy(seasonalBountyProgress = updated))
    }

    /** Claims a completed Bounty Board task, awarding one token. Returns false if not yet completable. */
    suspend fun claimBountyTask(taskId: String): Boolean = playerRepo.playerMutex.withLock {
        val event = activeEvent() ?: return@withLock false
        if ("bounty" !in event.pillars) return@withLock false
        val task = event.bountyTasks.firstOrNull { it.id == taskId } ?: return@withLock false
        val flags = ensureBountyRefreshedUnlocked()
        if (taskId in flags.seasonalBountyClaimed) return@withLock false
        val progress = flags.seasonalBountyProgress[taskId] ?: 0
        if (progress < task.amount) return@withLock false
        val claimedFlags = flags.copy(seasonalBountyClaimed = flags.seasonalBountyClaimed + taskId)
        playerRepo.updateFlagsUnlocked(awardTokenUnlocked(claimedFlags, event))
        true
    }

    // -------------------------------------------------------------------------
    // Expedition / Raid Boss — the underlying session is the existing dungeon/boss
    // engine; these just award a token when the completed key matches the active event.
    // -------------------------------------------------------------------------

    suspend fun recordExpeditionCompletion(activityKey: String) = playerRepo.playerMutex.withLock {
        val event = activeEvent() ?: return@withLock
        if ("expedition" !in event.pillars || event.expeditionDungeonKey != activityKey) return@withLock
        playerRepo.updateFlagsUnlocked(awardTokenUnlocked(playerRepo.getFlags(), event))
    }

    suspend fun recordBossDefeat(bossKey: String) = playerRepo.playerMutex.withLock {
        val event = activeEvent() ?: return@withLock
        if ("boss" !in event.pillars || event.bossKey != bossKey) return@withLock
        playerRepo.updateFlagsUnlocked(awardTokenUnlocked(playerRepo.getFlags(), event))
    }

    // -------------------------------------------------------------------------
    // Minigame — the Hub screen runs the actual whack-a-mole rounds and reports
    // whether the player landed enough hits to win. Either way the cooldown applies.
    // -------------------------------------------------------------------------

    suspend fun submitMinigameAttempt(won: Boolean): SeasonalMinigameResult = playerRepo.playerMutex.withLock {
        val event = activeEvent() ?: return@withLock SeasonalMinigameResult.NoActiveEvent
        val minigame = event.minigame
        if ("minigame" !in event.pillars || minigame == null) return@withLock SeasonalMinigameResult.NoActiveEvent
        val flags = playerRepo.getFlags()
        val now = System.currentTimeMillis()
        if (flags.seasonalMinigameCooldownAt > now) return@withLock SeasonalMinigameResult.OnCooldown(flags.seasonalMinigameCooldownAt)
        val resumesAt = now + minigame.cooldownMs
        val flagsWithCooldown = flags.copy(seasonalMinigameCooldownAt = resumesAt)
        if (won) {
            playerRepo.updateFlagsUnlocked(awardTokenUnlocked(flagsWithCooldown, event))
            SeasonalMinigameResult.Success(resumesAt)
        } else {
            playerRepo.updateFlagsUnlocked(flagsWithCooldown)
            SeasonalMinigameResult.Failure(resumesAt)
        }
    }

    // -------------------------------------------------------------------------
    // Shared token award — must only be called while already holding playerMutex.
    // -------------------------------------------------------------------------

    private fun awardTokenUnlocked(flags: PlayerFlags, event: SeasonalEventData): PlayerFlags {
        val newCount = (flags.seasonalTokensByEvent[event.id] ?: 0) + 1
        var updated = flags.copy(seasonalTokensByEvent = flags.seasonalTokensByEvent + (event.id to newCount))
        if (newCount >= event.tokenGoal && event.id !in flags.seasonalBannersEarned.map { it.eventId }) {
            updated = updated.copy(
                seasonalBannersEarned = updated.seasonalBannersEarned + SeasonalBannerEarned(
                    eventId       = event.id,
                    displayText   = event.bannerText,
                    completedAtMs = System.currentTimeMillis(),
                )
            )
        }
        return updated
    }
}
