package com.fantasyidler.repository

import com.fantasyidler.data.json.WeeklyQuestTemplate
import com.fantasyidler.data.model.PlayerFlags
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class WeeklyBonusReward {
    data class CoinsReward(val amount: Long = 100_000L) : WeeklyBonusReward()
    data class DivineItemReward(val itemKey: String) : WeeklyBonusReward()
}

data class WeeklyQuestWithProgress(
    val template: WeeklyQuestTemplate,
    val progress: Int,
    val claimed: Boolean,
)

@Singleton
class WeeklyQuestRepository @Inject constructor(
    private val gameData: GameDataRepository,
) {

    companion object {
        internal val DIVINE_DROP_POOL = listOf(
        "divine_sword", "divine_greatblade",
        "divine_helm", "divine_platebody", "divine_platelegs", "divine_shield", "divine_boots",
        "divine_pickaxe", "divine_axe", "divine_fishing_rod", "divine_hoe", "divine_hammer",
        "divine_tinderbox", "divine_frying_pan", "divine_grappling_hook", "divine_lockpick",
        )
    }

    internal val divineDropPool = DIVINE_DROP_POOL

    private val baseDivineDropChance = 1.0 / 26.0

    /** Pity-adjusted Divine gear drop chance: each consecutive missed week multiplies the odds
     *  by 1.5x, capped at a guaranteed drop. */
    fun currentDivineDropChance(pityMisses: Int): Double =
        (baseDivineDropChance * Math.pow(1.5, pityMisses.toDouble())).coerceAtMost(1.0)

    /** Current drop chance for display, or null once every Divine piece is already owned. */
    fun currentDivineDropChanceForDisplay(flags: PlayerFlags, ownedItems: Set<String>): Double? {
        if (divineDropPool.all { it in ownedItems }) return null
        return currentDivineDropChance(flags.divinePityMisses)
    }

    /** Returns epoch ms of the next Monday [resetHour] in local time after [fromMs]. */
    fun nextResetMs(fromMs: Long = System.currentTimeMillis(), resetHour: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        cal.set(Calendar.HOUR_OF_DAY, resetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // If we are exactly at Monday 6am or earlier today, and it is Monday, we advance if we are strictly <= fromMs.
        // Actually, just advance until it's Monday and time > fromMs.
        if (cal.timeInMillis <= fromMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun shouldRefresh(nextResetAt: Long, generatedAt: Long = 0L, resetHour: Int = 6): Boolean {
        if (nextResetAt == 0L) return true
        val now = System.currentTimeMillis()
        if (now < nextResetAt) return false
        if (generatedAt > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            cal.set(Calendar.HOUR_OF_DAY, resetHour)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            if (generatedAt >= cal.timeInMillis) return false
        }
        return true
    }

    private val combatSkills = listOf("attack", "strength", "defense", "ranged", "magic", "hitpoints")

    private val craftDependencies: Map<String, List<Pair<String, Int>>> = mapOf(
        "bronze_arrow"     to listOf("smithing" to 1),
        "iron_arrow"       to listOf("smithing" to 20),
        "steel_arrow"      to listOf("smithing" to 35),
        "mithril_arrow"    to listOf("smithing" to 55),
        "adamantite_arrow" to listOf("smithing" to 75),
        "runite_arrow"     to listOf("smithing" to 90),
    )

    /** Pick 5 distinct quest IDs from the pool using a date-seeded RNG (same quests all week). */
    fun selectFiveQuests(skillLevels: Map<String, Int>, resetHour: Int): List<String> {
        // Seed based on the most recent Monday reset boundary
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, resetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val seed = cal.timeInMillis

        val rng = Random(seed)
        val pool = gameData.weeklyQuestPool
        val eligible = pool.filter { quest ->
            val playerLevel = if (quest.skill == "combat") {
                combatSkills.maxOf { skillLevels[it] ?: 1 }
            } else {
                skillLevels[quest.skill] ?: 1
            }
            if (playerLevel < quest.levelRequired) return@filter false
            if (quest.skill == "cooking" &&
                (skillLevels["fishing"] ?: 1) < gameData.fishingLevelForCooking(quest.target)
            ) return@filter false
            val deps = craftDependencies[quest.target]
            deps == null || deps.all { (skill, minLevel) -> (skillLevels[skill] ?: 1) >= minLevel }
        }.shuffled(rng).take(5).toMutableList()
        if (eligible.size < 5) {
            val remaining = pool.sortedBy { it.levelRequired }
                .filter { q -> eligible.none { it.id == q.id } }
            eligible += remaining.take(5 - eligible.size)
        }
        return eligible.map { it.id }
    }

    fun refreshFlags(flags: PlayerFlags, skillLevels: Map<String, Int>): PlayerFlags {
        val ids = selectFiveQuests(skillLevels, flags.dailyResetHour)
        val now = System.currentTimeMillis()
        return flags.copy(
            weeklyQuestIds = ids,
            weeklyQuestProgress = emptyMap(),
            weeklyQuestClaimed = emptyList(),
            weeklyBonusClaimed = false,
            weeklyQuestGeneratedAt = now,
            weeklyQuestNextResetAt = nextResetMs(now, flags.dailyResetHour),
        )
    }

    fun getActiveWeeklyQuests(flags: PlayerFlags): List<WeeklyQuestWithProgress> {
        val pool = gameData.weeklyQuestPool.associateBy { it.id }
        return flags.weeklyQuestIds.mapNotNull { id ->
            val template = pool[id] ?: return@mapNotNull null
            WeeklyQuestWithProgress(
                template = template,
                progress = flags.weeklyQuestProgress[id] ?: 0,
                claimed = id in flags.weeklyQuestClaimed,
            )
        }
    }

    fun recordProgress(
        flags: PlayerFlags,
        type: String,
        target: String,
        amount: Int,
    ): PlayerFlags {
        val pool = gameData.weeklyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.weeklyQuestIds.filter { it !in flags.weeklyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.weeklyQuestProgress.toMutableMap()
        var changed = false

        for (id in activeUnclaimed) {
            val quest = pool[id] ?: continue
            if (quest.type != type) continue
            if (quest.type == "kill_enemy" && quest.target != target && quest.target != "any" &&
                gameData.enemies[target]?.tags?.contains(quest.target) != true &&
                gameData.bosses[target]?.tags?.contains(quest.target) != true) continue
            if (quest.type in listOf("gather", "craft") && quest.target != target && quest.target != "any") continue
            if (quest.type == "boss" && quest.target != target && quest.target != "any") continue
            if (quest.type == "slayer_task" && quest.target != target && quest.target != "any") continue
            if (quest.type == "mercantile" && quest.target != target && quest.target != "any") continue
            if (quest.type == "farming" && quest.target != target && quest.target != "any") continue
            if (quest.type == "guild_daily" && quest.target != target && quest.target != "any") continue
            if (quest.type == "agility" && quest.target != target && quest.target != "any") continue
            val current = updated[id] ?: 0
            val max = quest.amount
            if (current >= max) continue
            updated[id] = minOf(current + amount, max)
            changed = true
        }

        return if (changed) flags.copy(weeklyQuestProgress = updated) else flags
    }

    fun recordPrayerProgress(flags: PlayerFlags, amount: Int): PlayerFlags {
        val pool = gameData.weeklyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.weeklyQuestIds.filter { it !in flags.weeklyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.weeklyQuestProgress.toMutableMap()
        var changed = false

        for (id in activeUnclaimed) {
            val quest = pool[id] ?: continue
            if (quest.type != "prayer") continue
            val current = updated[id] ?: 0
            val max = quest.amount
            if (current >= max) continue
            updated[id] = minOf(current + amount, max)
            changed = true
        }

        return if (changed) flags.copy(weeklyQuestProgress = updated) else flags
    }

    fun claimQuest(
        flags: PlayerFlags,
        templateId: String,
    ): Pair<PlayerFlags, Long> {
        val pool = gameData.weeklyQuestPool.associateBy { it.id }
        val template = pool[templateId] ?: return flags to 10_000L
        val progress = flags.weeklyQuestProgress[templateId] ?: 0
        check(progress >= template.amount) { "Quest not complete yet" }
        check(templateId !in flags.weeklyQuestClaimed) { "Quest already claimed" }

        val reward = (5 + template.levelRequired / 5) * 1_000L

        val newFlags = flags.copy(
            weeklyQuestClaimed = flags.weeklyQuestClaimed + templateId,
        )
        return newFlags to reward
    }

    fun claimWeeklyBonus(
        flags: PlayerFlags,
        ownedItems: Set<String> = emptySet(),
    ): Pair<PlayerFlags, WeeklyBonusReward> {
        check(flags.weeklyQuestClaimed.size == 5) { "Not all weekly quests claimed" }
        check(!flags.weeklyBonusClaimed) { "Weekly bonus already claimed" }

        val missingPieces = divineDropPool.filter { it !in ownedItems }
        val dropped = missingPieces.isNotEmpty() && Random.nextDouble() < currentDivineDropChance(flags.divinePityMisses)

        val newFlags = flags.copy(
            weeklyBonusClaimed = true,
            // A drop resets the streak; owning every piece freezes it (nothing left to pity toward).
            divinePityMisses = when {
                dropped -> 0
                missingPieces.isEmpty() -> flags.divinePityMisses
                else -> flags.divinePityMisses + 1
            },
        )

        val reward: WeeklyBonusReward = if (dropped) {
            WeeklyBonusReward.DivineItemReward(missingPieces.random())
        } else {
            WeeklyBonusReward.CoinsReward()
        }

        return newFlags to reward
    }
}
