package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.QuestProgressDao
import com.fantasyidler.data.json.GuildDailyTemplate
import com.fantasyidler.data.json.GuildQuestData
import com.fantasyidler.data.json.GuildQuestRewards
import com.fantasyidler.data.model.CombatGuilds
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QuestProgress
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

data class GuildQuestWithProgress(
    val quest: GuildQuestData,
    val progress: Int,
    val completed: Boolean,
    val effectiveAmount: Int,
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
    private val townRepoProvider: Provider<TownRepository>,
) {

    /** Returns the effective quest target amount after applying the Guild Hall upgrade reduction. */
    suspend fun effectiveQuestAmount(quest: GuildQuestData): Int {
        val flags = playerRepo.getFlags()
        val factor = townRepoProvider.get().guildQuestRequirementFactor(flags)
        return (quest.amount * factor).toInt().coerceAtLeast(1)
    }

    /** Same as effectiveQuestAmount but for use in display contexts where flags are already loaded. */
    fun effectiveQuestAmountFromFlags(quest: GuildQuestData, flags: PlayerFlags): Int {
        val factor = townRepoProvider.get().guildQuestRequirementFactor(flags)
        return (quest.amount * factor).toInt().coerceAtLeast(1)
    }

    /**
     * One-time backfill for saves created before guild leveling switched from reputation
     * thresholds to a daily-count requirement. Without this, every existing player's guild
     * rank would appear reset to 0, since [PlayerFlags.guildDailyTierCounts] starts empty for
     * everyone and the old [PlayerFlags.guildReputation] value is otherwise never read anymore.
     *
     * For each guild, recomputes the rank the player had under the old rep-threshold system
     * (same step-quest gate, [LEGACY_REP_THRESHOLDS] instead of [DAILIES_REQUIRED_PER_TIER]) and
     * credits every tier below that rank as fully satisfied, so [guildLevel] recomputes to the
     * same rank they already had. Does not touch the tier they were actively working on, so
     * real post-update daily progress on that tier is untouched. Runs at most once per save.
     */
    suspend fun migrateLegacyGuildReputation() = playerRepo.playerMutex.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        if (flags.guildDailyMigrationDone) return@withLock
        val completedIds = loadCompletedQuestIds()
        val newCounts = flags.guildDailyTierCounts.toMutableMap()
        for (guild in ALL_GUILDS) {
            val rep = flags.guildReputation[guild] ?: 0L
            if (rep <= 0L) continue
            var oldLevel = 0
            for (threshold in LEGACY_REP_THRESHOLDS) {
                if (rep < threshold) break
                val tierQuests = gameData.guildQuests.values
                    .filter { it.guild == guild && it.guildLevelRequired == oldLevel }
                if (tierQuests.any { it.id !in completedIds }) break
                oldLevel++
            }
            for (tier in 0 until oldLevel) {
                newCounts["$guild:$tier"] = DAILIES_REQUIRED_PER_TIER.getOrElse(tier) { 0 }
            }
        }
        playerRepo.updateFlagsUnlocked(flags.copy(guildDailyTierCounts = newCounts, guildDailyMigrationDone = true))
    }

    companion object {
        /** Frozen copy of the old reputation thresholds, kept only for [migrateLegacyGuildReputation] to recompute pre-update guild ranks. Do not use elsewhere. */
        private val LEGACY_REP_THRESHOLDS = longArrayOf(
            500L, 1_500L, 4_000L, 9_000L, 20_000L,
            40_000L, 75_000L, 140_000L, 250_000L, 450_000L,
        )

        /** Guild dailies required this tier (resets to 0 each tier since counts are keyed by "guild:tier"), alongside that tier's step quest. */
        val DAILIES_REQUIRED_PER_TIER = intArrayOf(
            2, 3, 4, 5, 7,
            9, 12, 15, 20, 25,
        )

        val ALL_GUILDS = listOf(
            "mining", "fishing", "woodcutting", "farming", "thieving", "firemaking", "agility",
            "smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore", "construction",
            "warriors", "archers", "mages", "slayer", "prayer", "mercantile",
        )

        val POTION_SUBSTITUTES: Map<String, List<String>> = mapOf(
            "strength_potion"       to listOf("super_strength_potion", "overload_potion"),
            "attack_potion"         to listOf("super_attack_potion",   "overload_potion"),
            "defense_potion"        to listOf("super_defense_potion",  "overload_potion"),
            "ranging_potion"        to listOf("super_ranging_potion",  "overload_potion"),
            "magic_potion"          to listOf("super_magic_potion",    "overload_potion"),
            "super_strength_potion" to listOf("overload_potion"),
            "super_attack_potion"   to listOf("overload_potion"),
            "super_defense_potion"  to listOf("overload_potion"),
            "super_ranging_potion"  to listOf("overload_potion"),
            "super_magic_potion"    to listOf("overload_potion"),
        )

        fun countForTarget(items: Map<String, Int>, target: String): Int =
            (items[target] ?: 0) + (items["enhanced_$target"] ?: 0) + (POTION_SUBSTITUTES[target]?.sumOf { items[it] ?: 0 } ?: 0)
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
                quest           = quest,
                progress        = row?.progress ?: 0,
                completed       = row?.completed ?: false,
                effectiveAmount = effectiveQuestAmount(quest),
            )
        }
    }

    fun observeQuestProgress(): Flow<List<QuestProgress>> =
        questProgressDao.observeAllProgress()

    // -------------------------------------------------------------------------
    // Record activity against progression quests
    // -------------------------------------------------------------------------

    /** Called when a gathering or firemaking session is collected. */
    suspend fun recordGuildGathering(skillName: String, items: Map<String, Int>) = playerRepo.playerMutex.withLock {
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel(skillName, flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "gather") continue
            if (quest.guildLevelRequired > currentLevel) continue
            val count = items[quest.target] ?: continue
            if (count > 0) addQuestProgress(questId, count)
        }
        flags = applyDailyGathering(flags, skillName, items)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when a crafting session is collected (smithing, cooking, fletching, crafting, runecrafting, herblore). */
    suspend fun recordGuildCrafting(skillName: String, items: Map<String, Int>) = playerRepo.playerMutex.withLock {
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel(skillName, flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != skillName || quest.type != "craft") continue
            if (quest.guildLevelRequired > currentLevel) continue
            val count = countForTarget(items, quest.target)
            if (count > 0) addQuestProgress(questId, count)
        }
        flags = applyDailyCrafting(flags, skillName, items)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when a combat session is collected. */
    suspend fun recordGuildCombat(killsByEnemy: Map<String, Int>, combatStyle: String) = playerRepo.playerMutex.withLock {
        val guild = CombatGuilds.guildFor(combatStyle)
        val totalKills = killsByEnemy.values.sum()
        var flags = ensureGuildDailiesRefreshedUnlocked()
        if (totalKills > 0) {
            val completedIds = loadCompletedQuestIds()
            val currentLevel = guildLevel(guild, flags.guildDailyTierCounts, completedIds)
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != guild || quest.type != "kill") continue
                if (quest.guildLevelRequired > currentLevel) continue
                addQuestProgress(questId, totalKills)
            }
        }
        flags = applyDailyCombat(flags, guild, totalKills)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when slayer task kills are recorded ([taskKills] on-task kills, [tasksCompleted] tasks finished). */
    suspend fun recordGuildSlayer(taskKills: Int, tasksCompleted: Int) = playerRepo.playerMutex.withLock {
        if (taskKills <= 0 && tasksCompleted <= 0) return
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("slayer", flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "slayer") continue
            if (quest.guildLevelRequired > currentLevel) continue
            val count = when (quest.type) {
                "slayer_kill" -> taskKills
                "slayer_task" -> tasksCompleted
                else          -> 0
            }
            if (count > 0) addQuestProgress(questId, count)
        }
        flags = applyDailySlayer(flags, taskKills, tasksCompleted)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when a prayer session is collected. */
    suspend fun recordGuildPrayer(totalBuried: Int) = playerRepo.playerMutex.withLock {
        var flags = ensureGuildDailiesRefreshedUnlocked()
        if (totalBuried > 0) {
            val completedIds = loadCompletedQuestIds()
            val currentLevel = guildLevel("prayer", flags.guildDailyTierCounts, completedIds)
            for ((questId, quest) in gameData.guildQuests) {
                if (quest.guild != "prayer" || quest.type != "prayer") continue
                if (quest.guildLevelRequired > currentLevel) continue
                addQuestProgress(questId, totalBuried)
            }
        }
        flags = applyDailyPrayer(flags, totalBuried)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /**
     * Lets the player submit crops from their inventory toward a farming gather daily.
     * Returns the number of items actually consumed (0 if daily is already complete/claimed or
     * the player has none of the target item).
     */
    suspend fun contributeFarmingDaily(templateId: String, inventory: Map<String, Int>): Int = playerRepo.playerMutex.withLock {
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
        if (!playerRepo.consumeItemsUnlocked(mapOf(t.target to toConsume))) return 0
        val newProgress = flags.guildDailyProgress.toMutableMap()
        newProgress[templateId] = current + toConsume
        playerRepo.updateFlagsUnlocked(flags.copy(guildDailyProgress = newProgress))
        return toConsume
    }

    /**
     * Lets the player submit crops from their inventory toward a regular farming gather quest.
     * Returns the number of items actually consumed.
     */
    suspend fun contributeFarmingQuest(questId: String, inventory: Map<String, Int>): Int = playerRepo.playerMutex.withLock {
        val quest = gameData.guildQuests[questId] ?: return 0
        if (quest.guild != "farming" || quest.type != "gather") return 0
        val row = questProgressDao.getQuestProgress(questId) ?: QuestProgress(questId)
        if (row.completed) return 0
        
        val effectiveAmt = effectiveQuestAmount(quest)
        val needed = (effectiveAmt - row.progress).coerceAtLeast(0)
        if (needed == 0) return 0
        
        val available = inventory[quest.target] ?: 0
        if (available == 0) return 0
        
        val toConsume = minOf(needed, available)
        if (!playerRepo.consumeItemsUnlocked(mapOf(quest.target to toConsume))) return 0
        
        questProgressDao.upsert(row.copy(progress = row.progress + toConsume))
        return toConsume
    }

    /** Called when a mercantile trade route session is collected. */
    suspend fun recordGuildTrade(routeKey: String, coinsEarned: Long = 0L) = playerRepo.playerMutex.withLock {
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("mercantile", flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "mercantile" || quest.type != "trade") continue
            if (quest.guildLevelRequired > currentLevel) continue
            if (quest.target != routeKey) continue
            addQuestProgress(questId, 1)
        }
        flags = applyDailyTrade(flags, routeKey)
        flags = applyDailyEarnCoins(flags, coinsEarned)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when a thieving session is collected. Tracks pickpocket count per NPC. */
    suspend fun recordGuildThieving(npcKey: String, successCount: Int) = playerRepo.playerMutex.withLock {
        if (successCount <= 0) return
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("thieving", flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "thieving" || quest.type != "pickpocket") continue
            if (quest.guildLevelRequired > currentLevel) continue
            if (quest.target != npcKey) continue
            addQuestProgress(questId, successCount)
        }
        flags = applyDailyThieving(flags, npcKey, successCount)
        playerRepo.updateFlagsUnlocked(flags)
    }

    /** Called when an agility session is collected (counts completed sessions, not items). */
    suspend fun recordGuildSessions(courseKey: String) = playerRepo.playerMutex.withLock {
        var flags = ensureGuildDailiesRefreshedUnlocked()
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel("agility", flags.guildDailyTierCounts, completedIds)
        for ((questId, quest) in gameData.guildQuests) {
            if (quest.guild != "agility" || quest.type != "sessions") continue
            if (quest.guildLevelRequired > currentLevel) continue
            if (quest.target != courseKey) continue
            addQuestProgress(questId, 1)
        }
        flags = applyDailySessions(flags, courseKey)
        playerRepo.updateFlagsUnlocked(flags)
    }

    // -------------------------------------------------------------------------
    // Claim progression quest reward
    // -------------------------------------------------------------------------

    suspend fun claimGuildQuestReward(questId: String): GuildQuestClaimResult = playerRepo.playerMutex.withLock {
        val quest = gameData.guildQuests[questId] ?: return GuildQuestClaimResult.NotReady
        val row = questProgressDao.getQuestProgress(questId) ?: return GuildQuestClaimResult.NotReady
        if (row.completed) return GuildQuestClaimResult.AlreadyClaimed
        if (row.progress < effectiveQuestAmount(quest)) return GuildQuestClaimResult.NotReady

        questProgressDao.upsert(row.copy(completed = true, completedAt = System.currentTimeMillis()))

        val flags = playerRepo.getFlags()
        // completedIds includes the just-claimed quest (needed for newLevel gate).
        val completedIds = loadCompletedQuestIds()
        // oldLevel must reflect the state BEFORE this quest was counted, so exclude it.
        val preCompleteIds = completedIds - questId
        val oldLevel = guildLevel(quest.guild, flags.guildDailyTierCounts, preCompleteIds)
        val newLevel = guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds)

        var newFlags = flags

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

        playerRepo.updateFlagsUnlocked(newFlags)
        return GuildQuestClaimResult.Success(withCapeIfMaxLevel(quest.guild, oldLevel, newLevel, quest.rewards))
    }

    /**
     * Saves that reached guild level 10 before guild capes existed never got one, because the
     * cape is only granted on the level-up itself (issue #2157). Hands out any missing capes
     * once per save; the flag stops a sold cape from being re-granted later.
     */
    suspend fun backfillMissingGuildCapes() {
        if (playerRepo.getFlags().guildCapeBackfillDone) return
        val completedQuestIds = questProgressDao.getAllProgress().filter { it.completed }.map { it.questId }.toSet()
        val guilds = gameData.guildQuests.values.map { it.guild }.toSet()
        playerRepo.withLock {
            val flags = playerRepo.getFlagsUnlocked()
            if (flags.guildCapeBackfillDone) return@withLock
            val inventory = playerRepo.getInventoryUnlocked()
            val equipped = playerRepo.getEquipped().values
            val missing = guilds
                .filter { guildLevel(it, flags.guildDailyTierCounts, completedQuestIds) >= DAILIES_REQUIRED_PER_TIER.size }
                .map { "${it}_guild_cape" }
                .filter { it in gameData.equipment && (inventory[it] ?: 0) <= 0 && it !in equipped }
            playerRepo.updateFlagsUnlocked(flags.copy(guildCapeBackfillDone = true))
            if (missing.isNotEmpty()) playerRepo.addItemsUnlocked(missing.associateWith { 1 })
        }
    }

    /**
     * Returns [rewards] with the guild's cape added when [newLevel] just reached the top
     * guild rank. The cape is awarded on reaching level 10 itself, whichever gate closes
     * last (final quest or tier dailies), not on claiming the final quest. Skipped when
     * the player already owns the cape, so saves that received it early are not granted
     * a duplicate.
     */
    private suspend fun withCapeIfMaxLevel(
        guild: String,
        oldLevel: Int,
        newLevel: Int,
        rewards: GuildQuestRewards,
    ): GuildQuestRewards {
        if (newLevel <= oldLevel || newLevel < DAILIES_REQUIRED_PER_TIER.size) return rewards
        val capeKey = "${guild}_guild_cape"
        val owned = (playerRepo.getInventoryUnlocked()[capeKey] ?: 0) > 0 ||
            capeKey in playerRepo.getEquipped().values
        if (owned) return rewards
        return rewards.copy(items = rewards.items + (capeKey to 1))
    }

    // -------------------------------------------------------------------------
    // Two-gate level: all current-tier quests completed AND this tier's daily count met
    // -------------------------------------------------------------------------

    fun guildLevel(guild: String, dailyTierCounts: Map<String, Int>, completedQuestIds: Set<String>): Int {
        var level = 0
        while (level < DAILIES_REQUIRED_PER_TIER.size) {
            val tierQuests = gameData.guildQuests.values
                .filter { it.guild == guild && it.guildLevelRequired == level }
            if (tierQuests.any { it.id !in completedQuestIds }) break
            val count = dailyTierCounts["$guild:$level"] ?: 0
            if (count < DAILIES_REQUIRED_PER_TIER[level]) break
            level++
        }
        return level
    }

    /** Whether the player has engaged with [guild] at all (completed at least one of its quests), used to decide whether to seed dailies for it. */
    private fun isGuildUnlocked(guild: String, completedQuestIds: Set<String>): Boolean =
        gameData.guildQuests.values.any { it.guild == guild && it.id in completedQuestIds }

    /**
     * Whether the player's current skill level can actually work toward this daily — not just
     * whether the guild's rank (from completed quests) allows it. Guild rank never resets on
     * skill prestige, so without this a high-rank guild could keep assigning dailies the player
     * can no longer perform right after resetting that skill's level (issue #1015).
     */
    private fun isTemplateReachable(template: GuildDailyTemplate, skillLevels: Map<String, Int>): Boolean {
        fun level(skill: String) = skillLevels[skill] ?: 1
        return when {
            template.guild == "farming" && template.type == "gather" ->
                level("farming") >= (gameData.crops[template.target]?.levelRequired ?: 1)
            template.guild == "thieving" && template.type == "pickpocket" ->
                level("thieving") >= (gameData.thievingNpcs[template.target]?.levelRequired ?: 1)
            template.guild == "fletching" && template.type == "craft" -> {
                val recipeLevel = gameData.fletchingRecipes[template.target]?.levelRequired ?: 1
                if (level("fletching") < recipeLevel) return false
                if (template.target.endsWith("_arrow")) {
                    val tipsLevel = gameData.smithingRecipes[template.target + "_tip"]?.levelRequired ?: 1
                    level("smithing") >= tipsLevel
                } else true
            }
            template.guild == "herblore" && template.type == "craft" ->
                level("herblore") >= (gameData.herbloreRecipes[template.target]?.levelRequired ?: 1)
            template.guild == "smithing" && template.type == "craft" ->
                level("smithing") >= (gameData.smithingRecipes[template.target]?.levelRequired ?: 1)
            template.guild == "cooking" && template.type == "craft" -> {
                if (level("cooking") < (gameData.cookingRecipes[template.target]?.levelRequired ?: 1)) return false
                level("fishing") >= gameData.fishingLevelForCooking(template.target)
            }
            template.guild == "crafting" && template.type == "craft" ->
                level("crafting") >= (gameData.craftingRecipes[template.target]?.levelRequired ?: 1)
            template.guild == "runecrafting" && template.type == "craft" ->
                level("runecrafting") >= (gameData.runes[template.target]?.levelRequired ?: 1)
            template.guild == "construction" && template.type == "craft" ->
                level("construction") >= (gameData.constructionRecipes[template.target]?.levelRequired ?: 1)
            template.guild == "mining" && template.type == "gather" ->
                level("mining") >= (gameData.ores[template.target]?.levelRequired ?: 1)
            template.guild == "fishing" && template.type == "gather" ->
                level("fishing") >= (gameData.fish[template.target]?.levelRequired ?: 1)
            template.guild == "woodcutting" && template.type == "gather" -> {
                val treeLevel = gameData.trees.values.firstOrNull { it.logName == template.target }?.levelRequired ?: 1
                level("woodcutting") >= treeLevel
            }
            template.guild == "firemaking" && template.type == "craft" -> {
                val ashToLog = mapOf(
                    "ashes" to "log", "oak_ashes" to "oak_log", "willow_ashes" to "willow_log",
                    "maple_ashes" to "maple_log", "yew_ashes" to "yew_log",
                    "magic_ashes" to "magic_log", "redwood_ashes" to "redwood_log",
                )
                val logKey = ashToLog[template.target] ?: template.target
                level("firemaking") >= (gameData.logs[logKey]?.levelRequired ?: 1)
            }
            template.guild == "mercantile" && template.type == "trade" -> {
                val tradeRoute = gameData.tradeRoutes.find { it.id == template.target }
                level("mercantile") >= (tradeRoute?.levelRequired ?: 1)
            }
            template.guild == "agility" && template.type == "sessions" ->
                level("agility") >= (gameData.agilityCourses[template.target]?.levelRequired ?: 1)
            else -> true
        }
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

    /**
     * Claims a completed guild daily and increments this tier's daily counter (capped once the
     * tier requirement is met). Flags are read AND written under one lock hold so a concurrent
     * flags writer (e.g. the prestige flow while a collect is in flight) can't clobber the
     * claim and let it be claimed again. Returns the rewards, or null if not claimable.
     */
    suspend fun claimGuildDaily(templateId: String): GuildQuestRewards? = playerRepo.playerMutex.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val template = pool[templateId] ?: return null
        val progress = flags.guildDailyProgress[templateId] ?: 0
        if (progress < template.amount) return null
        if (templateId in flags.guildDailyClaimed) return null

        val guild = template.guild
        val completedIds = loadCompletedQuestIds()
        val currentLevel = guildLevel(guild, flags.guildDailyTierCounts, completedIds)
        val tierKey = "$guild:$currentLevel"
        val required = DAILIES_REQUIRED_PER_TIER.getOrElse(currentLevel) { Int.MAX_VALUE }
        val newCount = minOf((flags.guildDailyTierCounts[tierKey] ?: 0) + 1, required)
        val newFlags = flags.copy(
            guildDailyClaimed      = flags.guildDailyClaimed + templateId,
            guildDailyTierCounts   = flags.guildDailyTierCounts + (tierKey to newCount),
        )
        playerRepo.updateFlagsUnlocked(newFlags)
        val newLevel = guildLevel(guild, newFlags.guildDailyTierCounts, completedIds)
        return withCapeIfMaxLevel(guild, currentLevel, newLevel, template.rewards)
    }

    // -------------------------------------------------------------------------
    // Daily refresh
    // -------------------------------------------------------------------------

    fun shouldRefreshGuildDailies(nextResetAt: Long): Boolean {
        if (nextResetAt == 0L) return true
        return System.currentTimeMillis() >= nextResetAt
    }

    fun nextResetMs(fromMs: Long = System.currentTimeMillis(), resetHour: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        cal.set(Calendar.HOUR_OF_DAY, resetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= fromMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Selects up to 4 daily templates per guild for today, filtered by current guild level.
     *  Uses a date-seeded RNG so the same dailies are shown all day.
     *
     *  If the strict guild-level bracket doesn't yield 4 (e.g. a thin bracket, or a skill level
     *  that outpaces guild rank), the search widens in stages so players still get a full 4
     *  whenever the guild has that many templates at all (issue #1076). The widened stage
     *  hands out the highest tiers the player's skill can manage first, random within a tier.
     */
    fun buildRefreshedGuildDailyFlags(flags: PlayerFlags, completedQuestIds: Set<String>, skillLevels: Map<String, Int> = emptyMap(), randomSeed: Boolean = false): PlayerFlags {
        val today = Calendar.getInstance().let {
            it.get(Calendar.YEAR) * 10000 + it.get(Calendar.MONTH) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }
        val rng = if (randomSeed) Random(Random.nextLong()) else Random(today.toLong())

        val selectedIds = mutableListOf<String>()

        for (guild in ALL_GUILDS) {
            if (!isGuildUnlocked(guild, completedQuestIds)) continue
            val level = guildLevel(guild, flags.guildDailyTierCounts, completedQuestIds)
            val effectiveLevel = maxOf(level, 1)
            val guildPool = gameData.guildDailyPool.filter { it.guild == guild }

            val inBracket = guildPool.filter { effectiveLevel >= it.guildLevelMin && effectiveLevel <= it.guildLevelMax }
            val reachableInBracket = inBracket.filter { isTemplateReachable(it, skillLevels) }
            // Fallback prefers the HIGHEST tiers the player's skill can actually do (random
            // within a tier): a high-rank guild whose bracket is out of skill reach after a
            // prestige should hand out the best manageable dailies, not roll uniformly across
            // every low tier (playtester feedback ahead of 1.14).
            val reachableBestFirst = guildPool
                .filter { isTemplateReachable(it, skillLevels) }
                .shuffled(rng)
                .sortedByDescending { it.guildLevelMin }

            val chosen = mutableListOf<GuildDailyTemplate>()
            val chosenIds = mutableSetOf<String>()
            val chosenTargets = mutableSetOf<String>()
            val reachableStages = listOf(reachableInBracket.shuffled(rng), reachableBestFirst)
            // Last resort when the guild has fewer than 4 skill-reachable templates: deal the
            // lowest tiers first so the top-up quests become completable soonest while the
            // player relevels (a freshly prestiged construction player was dealt a level-65
            // yew wardrobe daily from a random draw here).
            val unreachableLowestFirst = guildPool.shuffled(rng).sortedBy { it.guildLevelMin }
            fun fill(candidates: List<GuildDailyTemplate>, allowDuplicateTargets: Boolean) {
                candidates
                    .filter { it.id !in chosenIds }
                    .filter { allowDuplicateTargets || it.target.isBlank() || it.target !in chosenTargets }
                    .forEach {
                        if (chosen.size < 4) {
                            chosen.add(it)
                            chosenIds.add(it.id)
                            chosenTargets.add(it.target)
                        }
                    }
            }
            // Distinct target items first: a thin bracket must not fill the whole day with
            // one item (issue #1500 -- four "craft platinum diamond ring" dailies). Every
            // reachable template, even with a duplicate target, ranks ahead of anything the
            // player's skill level cannot do yet.
            for (allowDuplicateTargets in listOf(false, true)) {
                reachableStages.forEach { fill(it, allowDuplicateTargets) }
            }
            for (allowDuplicateTargets in listOf(false, true)) {
                fill(unreachableLowestFirst, allowDuplicateTargets)
            }
            selectedIds.addAll(chosen.map { it.id })
        }

        val now = System.currentTimeMillis()
        return flags.copy(
            guildDailyIds           = selectedIds,
            guildDailyProgress      = emptyMap(),
            guildDailyClaimed       = emptyList(),
            guildDailyGeneratedAt   = now,
            guildDailyNextResetAt   = nextResetMs(now, flags.dailyResetHour),
        )
    }

    /** Refreshes guild dailies if needed, then retroactively resets any quest progress that pre-accumulated above the current tier. */
    suspend fun ensureGuildDailiesRefreshed(): PlayerFlags = playerRepo.playerMutex.withLock { ensureGuildDailiesRefreshedUnlocked() }

    /**
     * Debug helper: force-regenerates today's guild dailies and clears progress/claimed state,
     * as if the 6am daily reset had just fired.
     */
    suspend fun debugResetGuildDailies() = playerRepo.playerMutex.withLock {
        val flags = playerRepo.getFlags()
        val completedQuestIds = loadCompletedQuestIds()
        val skillLevels = try { playerRepo.getSkillLevels() } catch (_: Exception) { emptyMap() }
        val refreshed = buildRefreshedGuildDailyFlags(flags, completedQuestIds, skillLevels, true)
        playerRepo.updateFlagsUnlocked(refreshed)
    }

    internal suspend fun ensureGuildDailiesRefreshedUnlocked(): PlayerFlags {
        var flags = getRefreshedGuildDailyFlags()
        val completedIds = loadCompletedQuestIds()
        val retroFlags = retroactivelyResetQuestProgress(flags, completedIds)
        if (retroFlags !== flags) {
            playerRepo.updateFlagsUnlocked(retroFlags)
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
        val skillLevels = try { playerRepo.getSkillLevels() } catch (_: Exception) { emptyMap<String, Int>() }
        return when {
            shouldRefreshGuildDailies(flags.guildDailyNextResetAt) -> {
                val refreshed = buildRefreshedGuildDailyFlags(flags, completedQuestIds, skillLevels)
                playerRepo.updateFlagsUnlocked(refreshed)
                refreshed
            }
            hasNewlyUnlockedGuild(flags, completedQuestIds) -> {
                val patched = patchMissingGuildDailies(flags, completedQuestIds, skillLevels)
                playerRepo.updateFlagsUnlocked(patched)
                patched
            }
            else -> flags
        }
    }

    private fun hasNewlyUnlockedGuild(flags: PlayerFlags, completedQuestIds: Set<String>): Boolean {
        val pool = gameData.guildDailyPool.associateBy { it.id }
        return ALL_GUILDS.any { guild ->
            if (!isGuildUnlocked(guild, completedQuestIds)) return@any false
            if (flags.guildDailyIds.any { pool[it]?.guild == guild }) return@any false
            // Only trigger if there is at least one eligible template — avoids infinite loops
            // when a guild is unlocked but has no templates in its current level range.
            val level = maxOf(guildLevel(guild, flags.guildDailyTierCounts, completedQuestIds), 1)
            gameData.guildDailyPool.any { it.guild == guild && level >= it.guildLevelMin && level <= it.guildLevelMax }
        }
    }

    /** Appends daily IDs for guilds that are unlocked but have no current daily, without resetting existing progress. */
    private fun patchMissingGuildDailies(flags: PlayerFlags, completedQuestIds: Set<String>, skillLevels: Map<String, Int>): PlayerFlags {
        val today = Calendar.getInstance().let {
            it.get(Calendar.YEAR) * 10000 + it.get(Calendar.MONTH) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }
        val rng = Random(today.toLong())
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val guildsWithDailies = flags.guildDailyIds.mapNotNull { pool[it]?.guild }.toSet()
        val newIds = mutableListOf<String>()
        for (guild in ALL_GUILDS) {
            if (guild in guildsWithDailies) continue
            if (!isGuildUnlocked(guild, completedQuestIds)) continue
            val effectiveLevel = maxOf(guildLevel(guild, flags.guildDailyTierCounts, completedQuestIds), 1)
            val eligible = gameData.guildDailyPool
                .filter { it.guild == guild && effectiveLevel >= it.guildLevelMin && effectiveLevel <= it.guildLevelMax }
                .filter { isTemplateReachable(it, skillLevels) }
                .shuffled(rng)
            newIds.addAll(eligible.take(4).map { it.id })
        }
        return if (newIds.isEmpty()) flags else flags.copy(guildDailyIds = flags.guildDailyIds + newIds)
    }

    private suspend fun loadCompletedQuestIds(): Set<String> =
        questProgressDao.getAllProgress().filter { it.completed }.map { it.questId }.toSet()

    /** Resets progress for any quest tier that has not yet been reset-on-advance, to clear pre-accumulated dirty counts. Runs once per level per guild. */
    private suspend fun retroactivelyResetQuestProgress(flags: PlayerFlags, completedIds: Set<String>): PlayerFlags {
        var updatedFlags = flags
        for (guild in ALL_GUILDS) {
            val level = guildLevel(guild, flags.guildDailyTierCounts, completedIds)
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
            val count = countForTarget(items, t.target)
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

    private fun applyDailyTrade(flags: PlayerFlags, routeKey: String): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "mercantile" || t.type != "trade" || t.target != routeKey) continue
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

    private fun applyDailySessions(flags: PlayerFlags, courseKey: String): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "agility" || t.type != "sessions" || t.target != courseKey) continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + 1, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailySlayer(flags: PlayerFlags, taskKills: Int, tasksCompleted: Int): PlayerFlags {
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "slayer") continue
            val count = when (t.type) {
                "slayer_kill" -> taskKills
                "slayer_task" -> tasksCompleted
                else          -> 0
            }
            if (count <= 0) continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + count, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }

    private fun applyDailyThieving(flags: PlayerFlags, npcKey: String, successCount: Int): PlayerFlags {
        if (successCount <= 0) return flags
        val unclaimed = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        if (unclaimed.isEmpty()) return flags
        val pool = gameData.guildDailyPool.associateBy { it.id }
        val updated = flags.guildDailyProgress.toMutableMap()
        var changed = false
        for (id in unclaimed) {
            val t = pool[id] ?: continue
            if (t.guild != "thieving" || t.type != "pickpocket") continue
            if (t.target != npcKey) continue
            val cur = updated[id] ?: 0
            if (cur >= t.amount) continue
            updated[id] = minOf(cur + successCount, t.amount)
            changed = true
        }
        return if (changed) flags.copy(guildDailyProgress = updated) else flags
    }
}
