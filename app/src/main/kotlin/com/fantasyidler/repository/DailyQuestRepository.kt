package com.fantasyidler.repository

import com.fantasyidler.data.json.DailyQuestTemplate
import com.fantasyidler.data.model.PlayerFlags
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class DailyReward {
    data class CoinsReward(val amount: Int = 2000) : DailyReward()
    data class DwarvenItemReward(val itemKey: String) : DailyReward()
    data class DivineItemReward(val itemKey: String) : DailyReward()
}

data class DailyQuestWithProgress(
    val template: DailyQuestTemplate,
    val progress: Int,
    val claimed: Boolean,
)

@Singleton
class DailyQuestRepository @Inject constructor(
    private val gameData: GameDataRepository,
) {

    companion object {
        const val DWARVEN_BASE_DENOMINATOR = 100

        /** Pity-adjusted Dwarven drop odds, expressed as 1 in the returned value: every claimed
         *  daily without a drop narrows the denominator by 2 (1/100, 1/98, 1/96, ...) until a
         *  piece drops, which resets the streak. Floors at 1, i.e. a guaranteed drop. */
        fun dwarvenDropDenominator(pityClaims: Int): Int =
            (DWARVEN_BASE_DENOMINATOR - 2 * pityClaims.coerceAtLeast(0)).coerceAtLeast(1)
    }

    /** Denominator for the quests screen, or null when every Dwarven and Divine piece is owned
     *  (nothing can drop). Once Dwarven is complete the same roll drops Divine pieces. */
    fun dailyDropDenominatorForDisplay(flags: PlayerFlags, ownedItems: Set<String>): Int? {
        if (dwarvenDropPool.all { it in ownedItems } && WeeklyQuestRepository.DIVINE_DROP_POOL.all { it in ownedItems }) return null
        return dwarvenDropDenominator(flags.dwarvenPityClaims)
    }

    internal val dwarvenDropPool = listOf(
        "dwarven_sword", "dwarven_scimitar", "dwarven_warhammer",
        "dwarven_pickaxe", "dwarven_axe", "dwarven_fishing_rod", "dwarven_hoe",
        "dwarven_helm", "dwarven_platebody", "dwarven_platelegs",
        "dwarven_shield", "dwarven_boots",
        "dwarven_hammer", "dwarven_tinderbox", "dwarven_frying_pan", "dwarven_grappling_hook",
        "dwarven_lockpick",
    )

    /** Returns epoch ms of the next daily reset ([resetHour] local time) after [fromMs]. */
    fun nextResetMs(fromMs: Long = System.currentTimeMillis(), resetHour: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        cal.set(Calendar.HOUR_OF_DAY, resetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= fromMs) cal.add(Calendar.DAY_OF_YEAR, 1)
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
            if (generatedAt >= cal.timeInMillis) return false
        }
        return true
    }

    private val combatSkills = listOf("attack", "strength", "defense", "ranged", "magic", "hitpoints")

    // Fletching arrows requires smithing to craft the arrowheads first.
    private val craftDependencies: Map<String, List<Pair<String, Int>>> = mapOf(
        "bronze_arrow"     to listOf("smithing" to 1),
        "iron_arrow"       to listOf("smithing" to 20),
        "steel_arrow"      to listOf("smithing" to 35),
        "mithril_arrow"    to listOf("smithing" to 55),
        "adamantite_arrow" to listOf("smithing" to 75),
        "runite_arrow"     to listOf("smithing" to 90),
    )

    /** Pick 3 distinct quest IDs from the pool using a date-seeded RNG (same quests all day).
     *  Filters to quests the player can actually do based on their skill levels. */
    fun selectThreeQuests(skillLevels: Map<String, Int>): List<String> {
        val today = Calendar.getInstance().let {
            it.get(Calendar.YEAR) * 10000 + it.get(Calendar.MONTH) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }
        val rng = Random(today.toLong())
        val pool = gameData.dailyQuestPool
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
        }.shuffled(rng).take(3).toMutableList()
        if (eligible.size < 3) {
            val remaining = pool.sortedBy { it.levelRequired }
                .filter { q -> eligible.none { it.id == q.id } }
            eligible += remaining.take(3 - eligible.size)
        }
        return eligible.map { it.id }
    }

    fun refreshFlags(flags: PlayerFlags, skillLevels: Map<String, Int>): PlayerFlags {
        val ids = selectThreeQuests(skillLevels)
        val now = System.currentTimeMillis()
        return flags.copy(
            dailyQuestIds = ids,
            dailyQuestProgress = emptyMap(),
            dailyQuestClaimed = emptyList(),
            dailyQuestGeneratedAt = now,
            dailyQuestNextResetAt = nextResetMs(now, flags.dailyResetHour),
        )
    }

    fun getActiveDailyQuests(flags: PlayerFlags): List<DailyQuestWithProgress> {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        return flags.dailyQuestIds.mapNotNull { id ->
            val template = pool[id] ?: return@mapNotNull null
            DailyQuestWithProgress(
                template = template,
                progress = flags.dailyQuestProgress[id] ?: 0,
                claimed = id in flags.dailyQuestClaimed,
            )
        }
    }

    fun recordProgress(
        flags: PlayerFlags,
        type: String,
        target: String,
        amount: Int,
    ): PlayerFlags {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.dailyQuestIds.filter { it !in flags.dailyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.dailyQuestProgress.toMutableMap()
        var changed = false

        for (id in activeUnclaimed) {
            val quest = pool[id] ?: continue
            if (quest.type != type) continue
            if (quest.type == "kill_enemy" && quest.target != target &&
                gameData.enemies[target]?.tags?.contains(quest.target) != true &&
                gameData.bosses[target]?.tags?.contains(quest.target) != true) continue
            if (quest.type in listOf("gather", "craft") && quest.target != target) continue
            val current = updated[id] ?: 0
            val max = quest.amount
            if (current >= max) continue
            updated[id] = minOf(current + amount, max)
            changed = true
        }

        return if (changed) flags.copy(dailyQuestProgress = updated) else flags
    }

    fun recordPrayerProgress(flags: PlayerFlags, amount: Int): PlayerFlags {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.dailyQuestIds.filter { it !in flags.dailyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.dailyQuestProgress.toMutableMap()
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

        return if (changed) flags.copy(dailyQuestProgress = updated) else flags
    }

    fun claimQuest(
        flags: PlayerFlags,
        templateId: String,
        ownedItems: Set<String> = emptySet(),
    ): Pair<PlayerFlags, DailyReward> {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val template = pool[templateId] ?: return flags to DailyReward.CoinsReward()
        val progress = flags.dailyQuestProgress[templateId] ?: 0
        check(progress >= template.amount) { "Quest not complete yet" }
        check(templateId !in flags.dailyQuestClaimed) { "Quest already claimed" }

        val missingDwarven = dwarvenDropPool.filter { it !in ownedItems }
        // Once the Dwarven set is complete the same pity roll drops Divine pieces instead.
        val dropsDivine = missingDwarven.isEmpty()
        val missingPieces = if (dropsDivine) WeeklyQuestRepository.DIVINE_DROP_POOL.filter { it !in ownedItems } else missingDwarven
        val dropped = missingPieces.isNotEmpty() &&
            Random.nextInt(dwarvenDropDenominator(flags.dwarvenPityClaims)) == 0
        val reward: DailyReward = if (dropped) {
            if (dropsDivine) DailyReward.DivineItemReward(missingPieces.random())
            else DailyReward.DwarvenItemReward(missingPieces.random())
        } else {
            DailyReward.CoinsReward()
        }

        val newFlags = flags.copy(
            dailyQuestClaimed = flags.dailyQuestClaimed + templateId,
            // A drop resets the streak; owning every piece freezes it (nothing left to pity toward).
            dwarvenPityClaims = when {
                dropped                 -> 0
                missingPieces.isEmpty() -> flags.dwarvenPityClaims
                else                    -> flags.dwarvenPityClaims + 1
            },
        )
        return newFlags to reward
    }
}
