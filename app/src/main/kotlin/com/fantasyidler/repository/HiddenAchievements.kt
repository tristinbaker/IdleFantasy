package com.fantasyidler.repository

import com.fantasyidler.data.model.MainStory
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import kotlin.math.abs

/**
 * Hidden achievements (2.1.0): extra challenges for masochists that never count toward
 * achievement completion. Unlocks land in [PlayerFlags.hiddenAchievements] (id → epoch ms) the
 * moment they're earned and are never removed, so momentary conditions (1/1 daily odds, a coin
 * pile later spent) stay earned.
 *
 * Event achievements are unlocked by the pure `on*` hooks at the moment they happen; state
 * achievements are checked by [earnedFromState] whenever the player changes. Every counter lives
 * in [PlayerFlags.lifetimeStats] and starts at 0 on update, and every pet roll keeps rolling after
 * the pet is owned, so legacy saves and ironmen can earn every one. Achievements whose roll stops
 * once its whole drop pool is owned are granted outright to players who own that pool.
 */
object HiddenAchievements {

    const val CURSED           = "cursed"
    const val DIVINE_DENIED    = "divine_denied"
    const val PET_HATER        = "pet_hater"
    const val BONE_DRY         = "bone_dry"
    const val VOID_STARES_BACK = "void_stares_back"
    const val GREEN_THUMB      = "green_thumb_of_fate"
    const val DEJA_VU          = "deja_vu"
    const val BACK_TO_BACK     = "back_to_back"
    const val TWICE_BLESSED    = "twice_blessed"
    const val EXTERMINATOR     = "exterminator"
    const val REGULAR          = "regular"
    const val SKY_500          = "sky_500"
    const val SKY_1000         = "sky_1000"
    const val SEEN_IT_ALL      = "seen_it_all"
    const val TITLE_COLLECTOR  = "title_collector"
    const val VETERAN          = "veteran"
    const val OSSUARY          = "ossuary"
    const val HOARDER          = "hoarder"
    const val SCROOGE          = "scrooge"
    const val NAKED            = "naked_and_afraid"
    const val BRONZE_AGE       = "bronze_age"
    const val IRON_WILL        = "iron_will"
    const val CORPSE           = "professional_corpse"
    const val SPEEDRUN         = "speedrun_any"
    const val BUYERS_REMORSE   = "buyers_remorse"
    const val INDECISIVE       = "indecisive"
    const val JUNK_DEALER      = "junk_dealer"
    const val FESTIVAL_GOER    = "festival_goer"
    const val DEVOTED          = "devoted"
    const val ELDER_OBSESSION  = "elder_obsession"

    /** Display order. */
    val ALL: List<String> = listOf(
        CURSED, DIVINE_DENIED, PET_HATER, BONE_DRY, VOID_STARES_BACK, GREEN_THUMB,
        DEJA_VU, BACK_TO_BACK, TWICE_BLESSED,
        EXTERMINATOR, REGULAR, SKY_500, SKY_1000, SEEN_IT_ALL, TITLE_COLLECTOR, VETERAN,
        OSSUARY, HOARDER, SCROOGE,
        NAKED, BRONZE_AGE, IRON_WILL,
        CORPSE, SPEEDRUN,
        BUYERS_REMORSE, INDECISIVE, JUNK_DEALER,
        FESTIVAL_GOER, DEVOTED, ELDER_OBSESSION,
    )

    const val PET_HATER_SESSIONS      = 100L
    const val BONE_DRY_KILLS          = 500L
    const val BONE_DRY_PET_CHANCE     = 0.01
    const val VOID_SOVEREIGN          = "void_sovereign"
    const val VOID_KILLS              = 1_000L
    const val GREEN_THUMB_HARVESTS    = 3_000L
    const val DEJA_VU_ROLLS           = 10L
    const val BACK_TO_BACK_KILLS      = 10L
    const val EXTERMINATOR_KILLS      = 250_000
    const val REGULAR_RUNS            = 5_000
    const val SKY_FLOOR_1             = 500
    const val SKY_FLOOR_2             = 1_000
    const val VETERAN_MS              = 365L * 24 * 3_600_000L
    const val OSSUARY_BONES           = 1_000_000L
    const val HOARDER_QTY             = 1_000_000
    const val SCROOGE_COINS           = 1_000_000_000L
    const val NAKED_MIN_LEVEL         = 60
    const val CORPSE_DEATHS           = 100L
    const val INDECISIVE_RESPECS      = 25L
    const val JUNK_DEALER_ITEMS       = 1_000_000L
    const val DEVOTED_DAYS            = 90L
    /** Max spacing between the resets of two consecutive fully-claimed days: one day plus slack
     *  for DST and reset-hour changes, but well short of the two days a skipped day leaves. */
    const val DEVOTED_MAX_GAP_MS      = 36L * 3_600_000L
    /** Skilling pets roll once a minute; a session pools at least this many rolls. */
    const val SKILL_ROLLS_PER_SESSION = 60

    /** The 8 isle-only pets (7 elder dungeon drops + the Last Elder's familiar). */
    val ELDER_ISLE_PET_IDS: Set<String> = setOf(
        "tidal_sprite", "grove_fawn", "magma_salamander", "abyss_wisp",
        "ancient_turtle", "cinder_hawk", "deepwater_serpent", "elder_familiar",
    )

    private val COMBAT_SESSIONS = setOf("combat", "boss", "tower")

    const val STAT_FARM_DRY     = "farm_dry"
    const val STAT_DEATHS       = "deaths"
    const val STAT_BONES_BURIED = "bones_buried"
    const val STAT_RESPECS      = "prestige_respecs"
    const val STAT_BULK_SOLD    = "bulk_sold"
    const val STAT_DAILY_STREAK = "daily_streak"
    /** Reset time (epoch ms) that ended the last day whose dailies were all claimed. */
    const val STAT_DAILY_STREAK_RESET = "daily_streak_reset"
    /** Sessions in a skill since its pet last hit. */
    fun skillDryKey(skill: String) = "pet_dry:$skill"
    /** Won fights against a boss since its pet last hit. */
    fun bossDryKey(boss: String) = "boss_dry:$boss"
    /** Rolls since a pet last hit; absent until the first hit seen after 2.1.0. */
    fun petGapKey(petId: String) = "pet_gap:$petId"

    fun unlock(flags: PlayerFlags, id: String, now: Long = System.currentTimeMillis()): PlayerFlags =
        if (id in flags.hiddenAchievements) flags
        else flags.copy(hiddenAchievements = flags.hiddenAchievements + (id to now))

    private fun stat(flags: PlayerFlags, key: String): Long = flags.lifetimeStats[key] ?: 0L

    private fun withStat(flags: PlayerFlags, key: String, value: Long): PlayerFlags =
        flags.copy(lifetimeStats = flags.lifetimeStats + (key to value))

    /** Adds [by] to counter [key], unlocking [id] once it reaches [threshold]. */
    private fun bump(flags: PlayerFlags, key: String, by: Long, threshold: Long, id: String, now: Long): PlayerFlags {
        if (by <= 0L) return flags
        val value = stat(flags, key) + by
        val updated = withStat(flags, key, value)
        return if (value >= threshold) unlock(updated, id, now) else updated
    }

    /**
     * Daily quest claim ([before] → [after] are the flags around the claim). Cursed: the Dwarven
     * odds reached 1/1. Twice Blessed: a drop on the first daily after a drop (a 1/100 roll).
     * Devoted: the day's last daily claimed on 90 consecutive daily resets.
     */
    fun onDailyClaim(before: PlayerFlags, after: PlayerFlags, dropped: Boolean, now: Long = System.currentTimeMillis()): PlayerFlags {
        var f = after
        if (dropped && before.dwarvenPityClaims == 0) f = unlock(f, TWICE_BLESSED, now)
        if (!dropped && DailyQuestRepository.dwarvenDropDenominator(after.dwarvenPityClaims) == 1) f = unlock(f, CURSED, now)

        val ids = after.dailyQuestIds
        val periodEnd = before.dailyQuestNextResetAt
        val finishedDay = ids.isNotEmpty() && after.dailyQuestClaimed.containsAll(ids) && !before.dailyQuestClaimed.containsAll(ids)
        if (finishedDay && periodEnd > 0L) {
            val lastEnd = stat(f, STAT_DAILY_STREAK_RESET)
            val consecutive = lastEnd > 0L && periodEnd - lastEnd in 1L..DEVOTED_MAX_GAP_MS
            val streak = if (consecutive) stat(f, STAT_DAILY_STREAK) + 1 else 1L
            f = withStat(withStat(f, STAT_DAILY_STREAK, streak), STAT_DAILY_STREAK_RESET, periodEnd)
            if (streak >= DEVOTED_DAYS) f = unlock(f, DEVOTED, now)
        }
        return f
    }

    /** Farming pet roll on a harvest: Green Thumb of Fate (3,000 dry harvests) and Deja Vu. */
    fun onFarmPetRoll(flags: PlayerFlags, petId: String, hit: Boolean, now: Long = System.currentTimeMillis()): PlayerFlags {
        var f = if (hit) withStat(flags, STAT_FARM_DRY, 0L)
                else bump(flags, STAT_FARM_DRY, 1L, GREEN_THUMB_HARVESTS, GREEN_THUMB, now)
        f = advancePetGap(f, petId, rolls = 1L, hits = if (hit) listOf(1L) else emptyList(), DEJA_VU_ROLLS, DEJA_VU, now)
        return f
    }

    /**
     * Walks [rolls] pet rolls forward, [hits] being the 1-based roll positions that dropped the
     * pet. A hit landing within [window] rolls of the previous hit unlocks [id]. The counter only
     * starts at the first hit seen, so a pet owned before 2.1.0 needs two fresh hits.
     */
    private fun advancePetGap(flags: PlayerFlags, petId: String, rolls: Long, hits: List<Long>, window: Long, id: String, now: Long): PlayerFlags {
        val key = petGapKey(petId)
        var f = flags
        var since: Long? = flags.lifetimeStats[key]
        var pos = 0L
        for (h in hits.sorted()) {
            if (since != null && since + (h - pos) <= window) f = unlock(f, id, now)
            since = 0L
            pos = h
        }
        // Capped: anything past the window can never matter again.
        return if (since != null) withStat(f, key, minOf(since + (rolls - pos), window + 1)) else f
    }

    /** What [onSessionCollected] needs to know about a session, resolved from game data. */
    data class SessionInfo(
        val skillName: String,
        val activityKey: String,
        val isElder: Boolean = false,
        /** Pet this skill rolls once a minute, or null when it has none (or it's raid-only). */
        val skillPetId: String? = null,
        val bossPetId: String? = null,
        val bossPetChance: Double = 0.0,
        val dungeonRecommendedLevel: Int = 0,
        val dungeonSafeZone: Boolean = false,
    )

    /** A collected session: deaths, dungeon and boss challenges, and pet dry streaks. */
    fun onSessionCollected(flags: PlayerFlags, info: SessionInfo, frames: List<SessionFrame>, now: Long = System.currentTimeMillis()): PlayerFlags {
        if (frames.isEmpty()) return flags
        var f = flags
        if (info.skillName in COMBAT_SESSIONS) {
            val died = frames.any { it.died }
            if (died) f = bump(f, STAT_DEATHS, 1L, CORPSE_DEATHS, CORPSE, now)
            if (frames.first().died) f = unlock(f, SPEEDRUN, now)

            val gear = frames.first().gearAtStart
            if (info.skillName == "combat" && !died && gear != null && gear.isEmpty() &&
                info.dungeonRecommendedLevel >= NAKED_MIN_LEVEL && !info.dungeonSafeZone
            ) f = unlock(f, NAKED, now)

            if (info.skillName == "boss") {
                // Same win rule as HomeViewModel.collectBossSession.
                val won = frames.any { it.kills > 0 || it.killsByEnemy.isNotEmpty() }
                if (won && info.activityKey == MainStory.KURZUL_BOSS && gear != null &&
                    gear.values.all { it.startsWith("bronze_") } && frames.none { it.allyHpAfter.isNotEmpty() }
                ) f = unlock(f, BRONZE_AGE, now)
                if (won && info.bossPetId != null) f = onBossPetRoll(f, info, hit = frames.any { info.bossPetId in it.items }, now)
            }
            return f
        }

        val petId = info.skillPetId ?: return f
        if (info.isElder) return f
        // Gathering frames roll once each; crafts pool their 60 rolls onto one frame, so a short
        // frame list counts as 60 rolls with any hit at the very end (never overstates luck).
        val perFrame = frames.size >= SKILL_ROLLS_PER_SESSION
        val rolls = if (perFrame) frames.size.toLong() else SKILL_ROLLS_PER_SESSION.toLong()
        val hits = if (perFrame) frames.indices.filter { petId in frames[it].items }.map { it + 1L }
                   else if (frames.any { petId in it.items }) listOf(rolls) else emptyList()
        val dryKey = skillDryKey(info.skillName)
        f = if (hits.isNotEmpty()) withStat(f, dryKey, 0L)
            else bump(f, dryKey, 1L, PET_HATER_SESSIONS, PET_HATER, now)
        return advancePetGap(f, petId, rolls, hits, DEJA_VU_ROLLS, DEJA_VU, now)
    }

    private fun onBossPetRoll(flags: PlayerFlags, info: SessionInfo, hit: Boolean, now: Long): PlayerFlags {
        val dryKey = bossDryKey(info.activityKey)
        var f = if (hit) withStat(flags, dryKey, 0L) else withStat(flags, dryKey, stat(flags, dryKey) + 1)
        val dry = stat(f, dryKey)
        if (abs(info.bossPetChance - BONE_DRY_PET_CHANCE) < 1e-9 && dry >= BONE_DRY_KILLS) f = unlock(f, BONE_DRY, now)
        if (info.activityKey == VOID_SOVEREIGN && dry >= VOID_KILLS) f = unlock(f, VOID_STARES_BACK, now)
        return advancePetGap(f, info.bossPetId!!, rolls = 1L, hits = if (hit) listOf(1L) else emptyList(), BACK_TO_BACK_KILLS, BACK_TO_BACK, now)
    }

    fun onBonesBuried(flags: PlayerFlags, count: Int, now: Long = System.currentTimeMillis()): PlayerFlags =
        bump(flags, STAT_BONES_BURIED, count.toLong(), OSSUARY_BONES, OSSUARY, now)

    fun onRespec(flags: PlayerFlags, now: Long = System.currentTimeMillis()): PlayerFlags =
        bump(flags, STAT_RESPECS, 1L, INDECISIVE_RESPECS, INDECISIVE, now)

    /** Any shop sale: Buyer's Remorse for a Dwarven or Divine piece; bulk sales feed Junk Dealer. */
    fun onItemsSold(flags: PlayerFlags, sold: Map<String, Int>, bulk: Boolean, now: Long = System.currentTimeMillis()): PlayerFlags {
        var f = flags
        if (sold.any { (key, qty) -> qty > 0 && key in MASTERWORK_POOL }) f = unlock(f, BUYERS_REMORSE, now)
        if (bulk) f = bump(f, STAT_BULK_SOLD, sold.values.sumOf { it.toLong() }, JUNK_DEALER_ITEMS, JUNK_DEALER, now)
        return f
    }

    private val MASTERWORK_POOL: Set<String> =
        (DailyQuestRepository.DWARVEN_DROP_POOL + WeeklyQuestRepository.DIVINE_DROP_POOL).toSet()

    /** Everything [earnedFromState] reads, gathered once from the player and game data. */
    data class StateInput(
        val flags: PlayerFlags,
        val inventory: Map<String, Int>,
        val equippedItems: Set<String>,
        val coins: Long,
        val ownedPetIds: Set<String>,
        /** Every Armory piece. */
        val equipmentKeys: Set<String>,
        /** Every catalog title; seasonal titles are checked per series instead. */
        val titleIds: Set<String>,
        /** Seasonal event series ("bloomtide", ...). Banners and titles are earned per yearly run. */
        val seasonalSeries: Set<String>,
        val now: Long = System.currentTimeMillis(),
    )

    /** Hidden achievements the current state qualifies for (already-earned ones included). */
    fun earnedFromState(input: StateInput): Set<String> {
        val f = input.flags
        val owned = input.inventory.filterValues { it > 0 }.keys + input.equippedItems
        val earned = mutableSetOf<String>()

        // Once a pool is fully owned its roll stops for good, so its luck achievements can
        // no longer happen: grant them instead of locking the player out.
        val allDivine  = WeeklyQuestRepository.DIVINE_DROP_POOL.all { it in owned }
        val allDwarven = DailyQuestRepository.DWARVEN_DROP_POOL.all { it in owned }
        if (allDivine) earned += DIVINE_DENIED
        if (allDivine && allDwarven) { earned += CURSED; earned += TWICE_BLESSED }

        if (f.enemyKills.values.any { it >= EXTERMINATOR_KILLS }) earned += EXTERMINATOR
        if (f.dungeonRuns.values.any { it >= REGULAR_RUNS }) earned += REGULAR
        if (f.towerBestFloor >= SKY_FLOOR_1) earned += SKY_500
        if (f.towerBestFloor >= SKY_FLOOR_2) earned += SKY_1000
        if (input.equipmentKeys.isNotEmpty() && input.equipmentKeys.all { it in owned || it in f.seenItemKeys }) earned += SEEN_IT_ALL
        // A seasonal title per yearly run can't all be owned, so one per series counts.
        val seasonalTitleSeries = f.unlockedTitles.filter { it.startsWith("seasonal_") }
            .map { SeasonalCalendar.seriesOf(it.removePrefix("seasonal_")) }.toSet()
        if (input.titleIds.isNotEmpty() && input.titleIds.all { it in f.unlockedTitles } &&
            seasonalTitleSeries.containsAll(input.seasonalSeries)
        ) earned += TITLE_COLLECTOR
        if (f.characterCreatedAt > 0L && input.now - f.characterCreatedAt >= VETERAN_MS) earned += VETERAN
        if (input.inventory.values.any { it >= HOARDER_QTY }) earned += HOARDER
        if (input.coins >= SCROOGE_COINS) earned += SCROOGE
        if (f.ironman && f.storyStarted && f.storyChapter >= MainStory.CHAPTERS.size) earned += IRON_WILL
        val bannerSeries = f.seasonalBannersEarned.map { SeasonalCalendar.seriesOf(it.eventId) }.toSet()
        if (input.seasonalSeries.isNotEmpty() && bannerSeries.containsAll(input.seasonalSeries)) earned += FESTIVAL_GOER
        if (input.ownedPetIds.containsAll(ELDER_ISLE_PET_IDS)) earned += ELDER_OBSESSION
        return earned
    }
}
