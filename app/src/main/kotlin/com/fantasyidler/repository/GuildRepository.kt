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
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "gather") continue
            val count = items[quest.target] ?: continue
            if (count > 0) addQuestProgress(questId, count)
        }
        var flags = getRefreshedGuildDailyFlags()
        flags = applyDailyGathering(flags, skillName, items)
        playerRepo.updateFlags(flags)
    }

    /** Called when a crafting session is collected (smithing, cooking, fletching, crafting, runecrafting, herblore). */
    suspend fun recordGuildCrafting(skillName: String, items: Map<String, Int>) {
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "craft") continue
            val count = items[quest.target] ?: continue
            if (count > 0) addQuestProgress(questId, count)
        }
        var flags = getRefreshedGuildDailyFlags()
        flags = applyDailyCrafting(flags, skillName, items)
        playerRepo.updateFlags(flags)
    }

    /** Called when a combat session is collected. */
    suspend fun recordGuildCombat(killsByEnemy: Map<String, Int>, combatStyle: String) {
        val guild = combatStyleToGuild(combatStyle)
        val totalKills = killsByEnemy.values.sum()
        if (totalKills > 0) {
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != guild || quest.type != "kill") continue
                addQuestProgress(questId, totalKills)
            }
        }
        var flags = getRefreshedGuildDailyFlags()
        flags = applyDailyCombat(flags, guild, totalKills)
        playerRepo.updateFlags(flags)
    }

    /** Called when a prayer session is collected. */
    suspend fun recordGuildPrayer(totalBuried: Int) {
        if (totalBuried > 0) {
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != "prayer" || quest.type != "prayer") continue
                addQuestProgress(questId, totalBuried)
            }
        }
        var flags = getRefreshedGuildDailyFlags()
        flags = applyDailyPrayer(flags, totalBuried)
        playerRepo.updateFlags(flags)
    }

    /** Called when a mercantile trade route session is collected. */
    suspend fun recordGuildTrade() {
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "mercantile" || quest.type != "trade") continue
            addQuestProgress(questId, 1)
        }
        var flags = getRefreshedGuildDailyFlags()
        flags = applyDailyTrade(flags)
        playerRepo.updateFlags(flags)
    }

    /** Called when an agility session is collected (counts completed sessions, not items). */
    suspend fun recordGuildSessions() {
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "agility" || quest.type != "sessions") continue
            addQuestProgress(questId, 1)
        }
        var flags = getRefreshedGuildDailyFlags()
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
        val newRep = (flags.guildReputation[quest.guild] ?: 0L) + quest.rewards.reputation
        playerRepo.updateFlags(
            flags.copy(guildReputation = flags.guildReputation + (quest.guild to newRep))
        )

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

    /** Returns updated flags with guild daily reward claimed and reputation incremented. Returns null if not claimable. */
    fun claimGuildDaily(flags: PlayerFlags, templateId: String): Pair<PlayerFlags, GuildQuestRewards>? {
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val template = pool[templateId] ?: return null
        val progress = flags.guildDailyProgress[templateId] ?: 0
        if (progress < template.amount) return null
        if (templateId in flags.guildDailyClaimed) return null

        val guild = template.guild
        val newRep = (flags.guildReputation[guild] ?: 0L) + template.rewards.reputation
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

    /** Refreshes guild dailies if the 6am boundary has passed and returns current flags. */
    suspend fun ensureGuildDailiesRefreshed(): PlayerFlags {
        return getRefreshedGuildDailyFlags()
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
