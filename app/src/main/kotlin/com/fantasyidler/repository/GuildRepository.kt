package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.QuestProgressDao
import com.fantasyidler.data.json.GuildDailyTemplate
import com.fantasyidler.data.json.GuildQuestData
import com.fantasyidler.data.json.GuildQuestRewards
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QuestProgress
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow

data class GuildQuestWithProgress(
    val quest: GuildQuestData,
    val progress: Int,
    val completed: Boolean,
)

data class GuildDailyWithProgress(
    val template: GuildDailyTemplate,
    val progress: Int,
    val claimed: Boolean,
)

sealed class GuildQuestClaimResult {
    data class Success(val rewards: GuildQuestRewards) : GuildQuestClaimResult()
    object NotReady : GuildQuestClaimResult()
    object AlreadyClaimed : GuildQuestClaimResult()
}

@Singleton
class GuildRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val questProgressDao: QuestProgressDao,
    private val gameData: GameDataRepository,
) {

    companion object {
        val REP_THRESHOLDS = longArrayOf(
            500L, 1_500L, 4_000L, 9_000L, 20_000L,
            40_000L, 75_000L, 140_000L, 250_000L, 450_000L,
        )

        val ALL_GUILDS = listOf(
            "mining", "fishing", "woodcutting", "farming", "firemaking", "agility",
            "smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore",
            "warriors", "archers", "mages", "prayer", "mercantile",
        )

        fun guildLevelFromRep(rep: Long): Int = REP_THRESHOLDS.count { rep >= it }

        fun combatStyleToGuild(combatStyle: String): String = when (combatStyle) {
            "ranged" -> "archers"
            "magic"  -> "mages"
            else     -> "warriors"
        }
    }

    // -------------------------------------------------------------------------
    // Guild quest data (progression quests via quest_progress table)
    // -------------------------------------------------------------------------

    fun guildQuestsForGuild(guild: String): List<GuildQuestData> =
        gameData.guildQuests.values
            .filter { it.guild == guild }
            .sortedBy { it.guildLevelRequired }

    suspend fun getGuildQuestsWithProgress(guild: String): List<GuildQuestWithProgress> {
        val allProgress = questProgressDao.getAllProgress().associateBy { it.questId }
        return guildQuestsForGuild(guild).map { quest ->
            val row = allProgress[quest.id]
            GuildQuestWithProgress(
                quest     = quest,
                progress  = row?.progress ?: 0,
                completed = row?.completed ?: false,
            )
        }
    }

    fun observeQuestProgress(): Flow<List<QuestProgress>> =
        questProgressDao.observeAllProgress()

    // -------------------------------------------------------------------------
    // Record activity against progression quests
    // -------------------------------------------------------------------------

    /** Called when a gathering or firemaking session is collected. */
    suspend fun recordGuildGathering(skillName: String, items: Map<String, Int>) {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel(skillName, flags.guildReputation[skillName] ?: 0L, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "gather") continue
            if (quest.guildLevelRequired > currentLevel) continue
            val count = items[quest.target] ?: continue
            if (count > 0) addQuestProgress(questId, count)
        }
        flags = applyDailyGathering(flags, skillName, items)
        playerRepo.updateFlags(flags)
    }

    /** Called when a crafting session is collected (smithing, cooking, fletching, crafting, runecrafting, herblore). */
    suspend fun recordGuildCrafting(skillName: String, items: Map<String, Int>) {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel(skillName, flags.guildReputation[skillName] ?: 0L, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "craft") continue
            if (quest.guildLevelRequired > currentLevel) continue
            val count = items[quest.target] ?: continue
            if (count > 0) addQuestProgress(questId, count)
        }
        flags = applyDailyCrafting(flags, skillName, items)
        playerRepo.updateFlags(flags)
    }

    /** Called when a combat session is collected. */
    suspend fun recordGuildCombat(killsByEnemy: Map<String, Int>, combatStyle: String) {
        val guild = combatStyleToGuild(combatStyle)
        val totalKills = killsByEnemy.values.sum()
        var flags = getRefreshedGuildDailyFlags()
        if (totalKills > 0) {
            val completedIds = loadCompletedQuestIds()
            val currentLevel = guildLevel(guild, flags.guildReputation[guild] ?: 0L, completedIds)
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != guild || quest.type != "kill") continue
                if (quest.guildLevelRequired > currentLevel) continue
                addQuestProgress(questId, totalKills)
            }
        }
        flags = applyDailyCombat(flags, guild, totalKills)
        playerRepo.updateFlags(flags)
    }

    /** Called when a prayer session is collected. */
    suspend fun recordGuildPrayer(totalBuried: Int) {
        var flags = getRefreshedGuildDailyFlags()
        if (totalBuried > 0) {
            val completedIds = loadCompletedQuestIds()
            val currentLevel = guildLevel("prayer", flags.guildReputation["prayer"] ?: 0L, completedIds)
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != "prayer" || quest.type != "prayer") continue
                if (quest.guildLevelRequired > currentLevel) continue
                addQuestProgress(questId, totalBuried)
            }
        }
        flags = applyDailyPrayer(flags, totalBuried)
        playerRepo.updateFlags(flags)
    }

    /**
     * Lets the player submit crops from their inventory toward a farming gather daily.
     * Returns the number of items actually consumed (0 if daily is already complete/claimed or
     * the player has none of the target item).
     */
    suspend fun contributeFarmingDaily(templateId: String, inventory: Map<String, Int>): Int {
        val flags = playerRepo.getFlags()
        val pool  = gameData.guildDailyPool.associateBy { it.id }
        val t     = pool[templateId] ?: return 0
        if (t.guild != "farming" || t.type != "gather") return 0
        if (templateId in flags.guildDailyClaimed) return 0
        val current  = flags.guildDailyProgress[templateId] ?: 0
        val needed   = (t.amount - current).coerceAtLeast(0)
        if (needed == 0) return 0
        val available = inventory[t.target] ?: 0
        if (available == 0) return 0
        val toConsume = minOf(needed, available)
        playerRepo.consumeItems(mapOf(t.target to toConsume))
        val newProgress = flags.guildDailyProgress.toMutableMap()
        newProgress[templateId] = current + toConsume
        playerRepo.updateFlags(flags.copy(guildDailyProgress = newProgress))
        return toConsume
    }

    /** Called when a mercantile trade route session is collected. */
    suspend fun recordGuildTrade(coinsEarned: Long = 0L) {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("mercantile", flags.guildReputation["mercantile"] ?: 0L, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "mercantile" || quest.type != "trade") continue
            if (quest.guildLevelRequired > currentLevel) continue
            addQuestProgress(questId, 1)
        }
        flags = applyDailyTrade(flags)
        flags = applyDailyEarnCoins(flags, coinsEarned)
        playerRepo.updateFlags(flags)
    }

    /** Called when an agility session is collected (counts completed sessions, not items). */
    suspend fun recordGuildSessions() {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("agility", flags.guildReputation["agility"] ?: 0L, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "agility" || quest.type != "sessions") continue
            if (quest.guildLevelRequired > currentLevel) continue
            addQuestProgress(questId, 1)
        }
        flags = applyDailySessions(flags)
        playerRepo.updateFlags(flags)
    }

    // -------------------------------------------------------------------------
    // Claim progression quest reward
    // -------------------------------------------------------------------------

    suspend fun claimGuildQuestReward(questId: String): GuildQuestClaimResult {
        val quest = gameData.guildQuests[questId] ?: return GuildQuestClaimResult.NotReady
        val row = questProgressDao.getQuestProgress(questId) ?: return GuildQuestClaimResult.NotReady
        if (row.completed) return GuildQuestClaimResult.AlreadyClaimed
        if (row.progress < quest.amount) return GuildQuestClaimResult.NotReady

        questProgressDao.upsert(row.copy(completed = true, completedAt = System.currentTimeMillis()))

        val flags = playerRepo.getFlags()
        // completedIds includes the just-claimed quest (needed for newLevel gate).
        val completedIds = loadCompletedQuestIds()
        // oldLevel must reflect the state BEFORE this quest was counted, so exclude it.
        val preCompleteIds = completedIds - questId
        val oldLevel = guildLevel(quest.guild, flags.guildReputation[quest.guild] ?: 0L, preCompleteIds)
        val newRep = (flags.guildReputation[quest.guild] ?: 0L) + quest.rewards.reputation
        val newLevel = guildLevel(quest.guild, newRep, completedIds)

        var newFlags = flags.copy(guildReputation = flags.guildReputation + (quest.guild to newRep))

        if (newLevel > oldLevel) {
            // Reset any pre-accumulated progress on the newly active tier.
            for ((id, q) in gameData.guildQuests) {
                if (q.guild != quest.guild || q.guildLevelRequired != newLevel) continue
                val progressRow = questProgressDao.getQuestProgress(id) ?: continue
                if (progressRow.progress > 0 && !progressRow.completed) {
                    questProgressDao.upsert(progressRow.copy(progress = 0))
                }
            }
            newFlags = newFlags.copy(
                guildQuestResetLevels = newFlags.guildQuestResetLevels + (quest.guild to newLevel)
            )
        }

        playerRepo.updateFlags(newFlags)
        return GuildQuestClaimResult.Success(quest.rewards)
    }

    // -------------------------------------------------------------------------
    // Two-gate level: rep threshold AND all current-tier quests completed
    // -------------------------------------------------------------------------

    fun guildLevel(guild: String, rep: Long, completedQuestIds: Set<String>): Int {
        var level = 0
        for (threshold in REP_THRESHOLDS) {
            if (rep < threshold) break
            val tierQuests = gameData.guildQuests.values
                .filter { it.guild == guild && it.guildLevelRequired == level }
            if (tierQuests.any { it.id !in completedQuestIds }) break
            level++
        }
        return level
    }

    // -------------------------------------------------------------------------
    // Guild daily data
    // -------------------------------------------------------------------------

    fun getGuildDailiesWithProgress(guild: String, flags: PlayerFlags): List<GuildDailyWithProgress> {
        val pool = gameData.guildDailyPool.associateBy { it.id }
        return flags.guildDailyIds
            .mapNotNull { pool[it] }
            .filter { it.guild == guild }
            .map { t ->
                GuildDailyWithProgress(
                    template = t,
                    progress = flags.guildDailyProgress[t.id] ?: 0,
                    claimed  = t.id in flags.guildDailyClaimed,
                )
            }
    }

    /** Returns updated flags with guild daily reward claimed and reputation incremented (capped at tier boundary if gate quests incomplete). Returns null if not claimable. */
    suspend fun claimGuildDaily(flags: PlayerFlags, templateId: String): Pair<PlayerFlags, GuildQuestRewards>? {
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val template = pool[templateId] ?: return null
        val progress = flags.guildDailyProgress[templateId] ?: 0
        if (progress < template.amount) return null
        if (templateId in flags.guildDailyClaimed) return null

        val guild = template.guild
        val completedIds = loadCompletedQuestIds()
        val currentRep = flags.guildReputation[guild] ?: 0L
        val cap = repCapForGuild(guild, currentRep, completedIds)
        val newRep = minOf(currentRep + template.rewards.reputation, cap)
        val newFlags = flags.copy(
            guildDailyClaimed   = flags.guildDailyClaimed + templateId,
            guildReputation     = flags.guildReputation + (guild to newRep),
        )
        return newFlags to template.rewards
    }

    // -------------------------------------------------------------------------
    // Daily refresh
    // -------------------------------------------------------------------------

    fun shouldRefreshGuildDailies(generatedAt: Long): Boolean {
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

    fun nextResetMs(fromMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        cal.set(Calendar.HOUR_OF_DAY, 6)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= fromMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Selects up to 2 daily templates per guild for today, filtered by current guild level.
     *  Uses a date-seeded RNG so the same dailies are shown all day. */
    fun buildRefreshedGuildDailyFlags(flags: PlayerFlags, completedQuestIds: Set<String>): PlayerFlags {
        val today = Calendar.getInstance().let {
            it.get(Calendar.YEAR) * 10000 + it.get(Calendar.MONTH) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }
        val rng = Random(today.toLong())
        val selectedIds = mutableListOf<String>()

        for (guild in ALL_GUILDS) {
            val guildRep = flags.guildReputation[guild] ?: 0L
            if (guildRep == 0L) continue
            val guildLevel = guildLevel(guild, guildRep, completedQuestIds)
            val effectiveLevel = maxOf(guildLevel, 1)
            val eligible = gameData.guildDailyPool
                .filter { it.guild == guild && effectiveLevel >= it.guildLevelMin && effectiveLevel <= it.guildLevelMax }
                .shuffled(rng)
            selectedIds.addAll(eligible.take(2).map { it.id })
        }

        return flags.copy(
            guildDailyIds           = selectedIds,
            guildDailyProgress      = emptyMap(),
            guildDailyClaimed       = emptyList(),
            guildDailyGeneratedAt   = System.currentTimeMillis(),
        )
    }

    /** Refreshes guild dailies if needed, then retroactively resets any quest progress that pre-accumulated above the current tier. */
    suspend fun ensureGuildDailiesRefreshed(): PlayerFlags {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val retroFlags = retroactivelyResetQuestProgress(flags, completedIds)
        if (retroFlags !== flags) {
            playerRepo.updateFlags(retroFlags)
            flags = retroFlags
        }
        return flags
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun getRefreshedGuildDailyFlags(): PlayerFlags {
        val flags = playerRepo.getFlags()
        val completedQuestIds = questProgressDao.getAllProgress()
            .filter { it.completed }
            .map { it.questId }
            .toSet()
        return if (shouldRefreshGuildDailies(flags.guildDailyGeneratedAt) || hasNewlyUnlockedGuild(flags, completedQuestIds)) {
            val refreshed = buildRefreshedGuildDailyFlags(flags, completedQuestIds)
            playerRepo.updateFlags(refreshed)
            refreshed
        } else flags
    }

    private fun hasNewlyUnlockedGuild(flags: PlayerFlags, completedQuestIds: Set<String>): Boolean {
        val pool = gameData.guildDailyPool.associateBy { it.id }
        return ALL_GUILDS.any { guild ->
            (flags.guildReputation[guild] ?: 0L) > 0L &&
                flags.guildDailyIds.none { pool[it]?.guild == guild }
        }
    }

    private suspend fun loadCompletedQuestIds(): Set<String> =
        questProgressDao.getAllProgress().filter { it.completed }.map { it.questId }.toSet()

    /** Returns the rep cap for a guild at the current level. Rep cannot exceed the next tier threshold while gate quests are incomplete. */
    private fun repCapForGuild(guild: String, currentRep: Long, completedIds: Set<String>): Long {
        val level = guildLevel(guild, currentRep, completedIds)
        if (level >= 10) return Long.MAX_VALUE
        val tierQuests = gameData.guildQuests.values.filter { it.guild == guild && it.guildLevelRequired == level }
        return if (tierQuests.isEmpty() || tierQuests.all { it.id in completedIds }) {
            Long.MAX_VALUE
        } else {
            REP_THRESHOLDS[level] - 1
        }
    }

    /** Resets progress for any quest tier that has not yet been reset-on-advance, to clear pre-accumulated dirty counts. Runs once per level per guild. */
    private suspend fun retroactivelyResetQuestProgress(flags: PlayerFlags, completedIds: Set<String>): PlayerFlags {
        var updatedFlags = flags
        for (guild in ALL_GUILDS) {
            val level = guildLevel(guild, flags.guildReputation[guild] ?: 0L, completedIds)
            if (level == 0) continue
            val resetLevel = flags.guildQuestResetLevels[guild] ?: 0
            if (level <= resetLevel) continue
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != guild) continue
                if (quest.guildLevelRequired <= resetLevel || quest.guildLevelRequired >= level) continue
                val row = questProgressDao.getQuestProgress(questId) ?: continue
                if (row.progress > 0 && !row.completed) {
                    questProgressDao.upsert(row.copy(progress = 0))
                }
            }
            updatedFlags = updatedFlags.copy(
                guildQuestResetLevels = updatedFlags.guildQuestResetLevels + (guild to level)
            )
        }
        return updatedFlags
    }

    private suspend fun addQuestProgress(questId: String, delta: Int) {
        val current = questProgressDao.getQuestProgress(questId) ?: QuestProgress(questId)
        if (current.completed) return
        questProgressDao.upsert(current.copy(progress = current.progress + delta))
    }

    private fun applyDailyGathering(flags: PlayerFlags, guild: String, items: Map<String, Int>): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != guild || t.type != "gather") continue
            val count = items[t.target] ?: continue
            if (count <= 0) continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + count, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyCrafting(flags: PlayerFlags, guild: String, items: Map<String, Int>): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != guild || t.type != "craft") continue
            val count = items[t.target] ?: continue
            if (count <= 0) continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + count, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyCombat(flags: PlayerFlags, guild: String, totalKills: Int): PlayerFlags {
        if (totalKills == 0) return flags
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != guild || t.type != "kill") continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + totalKills, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyPrayer(flags: PlayerFlags, totalBuried: Int): PlayerFlags {
        if (totalBuried == 0) return flags
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "prayer" || t.type != "prayer") continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + totalBuried, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyTrade(flags: PlayerFlags): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "mercantile" || t.type != "trade") continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + 1, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyEarnCoins(flags: PlayerFlags, coinsEarned: Long): PlayerFlags {
        if (coinsEarned <= 0) return flags
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "mercantile" || t.type != "earn_coins") continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + coinsEarned.toInt(), t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailySessions(flags: PlayerFlags): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "agility" || t.type != "sessions") continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + 1, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }
}
