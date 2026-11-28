package com.fantasyidler.repository

import com.fantasyidler.data.model.MainStory
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SeasonalBannerEarned
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.repository.HiddenAchievements as H
import com.fantasyidler.ui.viewmodel.HiddenAchievementCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unlock rules for the hidden achievements ([HiddenAchievements]). */
class HiddenAchievementsTest {

    private fun frame(minute: Int = 0, items: Map<String, Int> = emptyMap(), died: Boolean = false, kills: Int = 0,
                      gear: Map<String, String>? = null, allies: List<Int> = emptyList()) =
        SessionFrame(minute = minute, xpGain = 0, xpBefore = 0, xpAfter = 0, levelBefore = 0, levelAfter = 0,
            items = items, died = died, kills = kills, gearAtStart = gear, allyHpAfter = allies)

    private fun PlayerFlags.has(id: String) = id in hiddenAchievements

    // ------------------------------------------------------------------ catalog

    @Test
    fun `catalog lists every hidden achievement in display order`() {
        assertEquals(H.ALL, HiddenAchievementCatalog.ALL.map { it.id })
        assertEquals(H.ALL.size, H.ALL.toSet().size)
    }

    @Test
    fun `unlocking twice keeps the first timestamp`() {
        val once = H.unlock(PlayerFlags(), H.SCROOGE, now = 100L)
        assertEquals(100L, H.unlock(once, H.SCROOGE, now = 200L).hiddenAchievements[H.SCROOGE])
    }

    // ------------------------------------------------------------------ daily and weekly

    @Test
    fun `cursed unlocks when a dry claim takes the odds to 1 in 1`() {
        val before = PlayerFlags(dwarvenPityClaims = 49)
        assertTrue(H.onDailyClaim(before, before.copy(dwarvenPityClaims = 50), dropped = false).has(H.CURSED))
        val early = PlayerFlags(dwarvenPityClaims = 48)
        assertFalse(H.onDailyClaim(early, early.copy(dwarvenPityClaims = 49), dropped = false).has(H.CURSED))
    }

    @Test
    fun `twice blessed needs a drop on the first claim after a drop`() {
        val fresh = PlayerFlags(dwarvenPityClaims = 0)
        assertTrue(H.onDailyClaim(fresh, fresh, dropped = true).has(H.TWICE_BLESSED))
        val later = PlayerFlags(dwarvenPityClaims = 3)
        assertFalse(H.onDailyClaim(later, later.copy(dwarvenPityClaims = 0), dropped = true).has(H.TWICE_BLESSED))
    }

    private val day = 24L * 3_600_000L

    /** Claims the last of the day's dailies for the day whose reset is [resetAt]. */
    private fun finishDay(flags: PlayerFlags, resetAt: Long): PlayerFlags {
        val before = flags.copy(dailyQuestIds = listOf("a", "b", "c"), dailyQuestClaimed = listOf("a", "b"), dailyQuestNextResetAt = resetAt)
        return H.onDailyClaim(before, before.copy(dailyQuestClaimed = listOf("a", "b", "c")), dropped = false)
    }

    @Test
    fun `devoted counts consecutive fully-claimed days and resets on a gap`() {
        var f = PlayerFlags()
        for (d in 1..89) f = finishDay(f, d * day)
        assertEquals(89L, f.lifetimeStats[H.STAT_DAILY_STREAK])
        assertFalse(f.has(H.DEVOTED))
        assertTrue(finishDay(f, 90 * day).has(H.DEVOTED))
        // Skipping day 90 starts over.
        assertEquals(1L, finishDay(f, 91 * day).lifetimeStats[H.STAT_DAILY_STREAK])
    }

    @Test
    fun `devoted ignores claims that do not finish the day`() {
        val before = PlayerFlags(dailyQuestIds = listOf("a", "b", "c"), dailyQuestNextResetAt = day)
        val after = H.onDailyClaim(before, before.copy(dailyQuestClaimed = listOf("a")), dropped = false)
        assertEquals(null, after.lifetimeStats[H.STAT_DAILY_STREAK])
    }

    // ------------------------------------------------------------------ pets

    @Test
    fun `green thumb needs 3000 dry harvests in a row`() {
        var f = PlayerFlags()
        repeat(2_999) { f = H.onFarmPetRoll(f, "farm_pet", hit = false) }
        assertFalse(f.has(H.GREEN_THUMB))
        assertTrue(H.onFarmPetRoll(f, "farm_pet", hit = false).has(H.GREEN_THUMB))
        assertEquals(0L, H.onFarmPetRoll(f, "farm_pet", hit = true).lifetimeStats[H.STAT_FARM_DRY])
    }

    @Test
    fun `deja vu needs two hits within 10 rolls, counting from a hit seen after the update`() {
        var f = H.onFarmPetRoll(PlayerFlags(), "farm_pet", hit = true)
        assertFalse("a single hit is not deja vu", f.has(H.DEJA_VU))
        repeat(8) { f = H.onFarmPetRoll(f, "farm_pet", hit = false) }
        val nineMisses = H.onFarmPetRoll(f, "farm_pet", hit = false)
        assertTrue("9 misses then a hit is within 10", H.onFarmPetRoll(nineMisses, "farm_pet", hit = true).has(H.DEJA_VU))
        repeat(2) { f = H.onFarmPetRoll(f, "farm_pet", hit = false) }
        assertFalse("10 misses then a hit is outside the window", H.onFarmPetRoll(f, "farm_pet", hit = true).has(H.DEJA_VU))
    }

    private val mining = H.SessionInfo(skillName = "mining", activityKey = "iron_ore", skillPetId = "rock_pet")

    private fun gathering(hitMinutes: Set<Int> = emptySet()) =
        (0 until 60).map { m -> frame(minute = m, items = if (m in hitMinutes) mapOf("rock_pet" to 1) else emptyMap()) }

    @Test
    fun `pet hater needs 100 dry sessions in one skill`() {
        var f = PlayerFlags()
        repeat(99) { f = H.onSessionCollected(f, mining, gathering()) }
        assertFalse(f.has(H.PET_HATER))
        assertTrue(H.onSessionCollected(f, mining, gathering()).has(H.PET_HATER))
        assertEquals(0L, H.onSessionCollected(f, mining, gathering(setOf(5))).lifetimeStats[H.skillDryKey("mining")])
    }

    @Test
    fun `deja vu reads minute positions across back-to-back gathering sessions`() {
        val first = H.onSessionCollected(PlayerFlags(), mining, gathering(setOf(55)))  // 5 rolls left after the hit
        assertTrue(H.onSessionCollected(first, mining, gathering(setOf(3))).has(H.DEJA_VU))   // 5 + 3 = 8
        assertFalse(H.onSessionCollected(first, mining, gathering(setOf(8))).has(H.DEJA_VU))  // 5 + 8 = 13
    }

    @Test
    fun `pooled craft rolls never fake a deja vu`() {
        val craft = H.SessionInfo(skillName = "smithing", activityKey = "bar", skillPetId = "anvil_pet")
        val hitFrame = listOf(frame(items = mapOf("anvil_pet" to 1)))
        val first = H.onSessionCollected(PlayerFlags(), craft, hitFrame)
        assertFalse(H.onSessionCollected(first, craft, hitFrame).has(H.DEJA_VU))
    }

    @Test
    fun `elder isle sessions do not roll pets`() {
        var f = PlayerFlags()
        repeat(100) { f = H.onSessionCollected(f, mining.copy(isElder = true), gathering()) }
        assertFalse(f.has(H.PET_HATER))
    }

    private val kbd = H.SessionInfo(skillName = "boss", activityKey = "king_black_dragon", bossPetId = "kbd_pet", bossPetChance = 0.01)
    private fun bossWin(pet: Boolean = false) = listOf(frame(kills = 1, items = if (pet) mapOf("kbd_pet" to 1) else emptyMap()))

    @Test
    fun `bone dry needs 500 dry wins against a 1 in 100 boss`() {
        var f = PlayerFlags()
        repeat(499) { f = H.onSessionCollected(f, kbd, bossWin()) }
        assertFalse(f.has(H.BONE_DRY))
        assertTrue(H.onSessionCollected(f, kbd, bossWin()).has(H.BONE_DRY))
        // Losses don't roll the pet, so they don't count.
        assertFalse(H.onSessionCollected(f, kbd, listOf(frame(died = true))).has(H.BONE_DRY))
        // A rarer pet doesn't qualify.
        var balrog = PlayerFlags()
        repeat(500) { balrog = H.onSessionCollected(balrog, kbd.copy(activityKey = "balrog", bossPetChance = 0.005), bossWin()) }
        assertFalse(balrog.has(H.BONE_DRY))
    }

    @Test
    fun `void stares back needs 1000 dry void sovereign wins`() {
        val void = kbd.copy(activityKey = H.VOID_SOVEREIGN, bossPetChance = 0.003)
        var f = PlayerFlags()
        repeat(999) { f = H.onSessionCollected(f, void, bossWin()) }
        assertFalse(f.has(H.VOID_STARES_BACK))
        assertTrue(H.onSessionCollected(f, void, bossWin()).has(H.VOID_STARES_BACK))
    }

    @Test
    fun `back to back needs a raid pet within 10 kills of the last`() {
        var f = H.onSessionCollected(PlayerFlags(), kbd, bossWin(pet = true))
        repeat(9) { f = H.onSessionCollected(f, kbd, bossWin()) }
        assertTrue(H.onSessionCollected(f, kbd, bossWin(pet = true)).has(H.BACK_TO_BACK))
        assertFalse(H.onSessionCollected(H.onSessionCollected(f, kbd, bossWin()), kbd, bossWin(pet = true)).has(H.BACK_TO_BACK))
    }

    // ------------------------------------------------------------------ combat

    private val temple = H.SessionInfo(skillName = "combat", activityKey = "ancient_temple", dungeonRecommendedLevel = 65)

    @Test
    fun `deaths feed professional corpse and a first-minute death is a speedrun`() {
        var f = PlayerFlags()
        repeat(99) { f = H.onSessionCollected(f, temple, listOf(frame(), frame(minute = 1, died = true))) }
        assertFalse(f.has(H.CORPSE))
        assertFalse(f.has(H.SPEEDRUN))
        val last = H.onSessionCollected(f, temple, listOf(frame(died = true)))
        assertTrue(last.has(H.CORPSE))
        assertTrue(last.has(H.SPEEDRUN))
    }

    @Test
    fun `naked and afraid needs an empty gear stamp, no death and a level 60+ dungeon`() {
        val naked = listOf(frame(gear = emptyMap()), frame(minute = 1))
        assertTrue(H.onSessionCollected(PlayerFlags(), temple, naked).has(H.NAKED))
        assertFalse("pre-2.1.0 sessions have no stamp", H.onSessionCollected(PlayerFlags(), temple, listOf(frame(gear = null))).has(H.NAKED))
        assertFalse(H.onSessionCollected(PlayerFlags(), temple.copy(dungeonRecommendedLevel = 58), naked).has(H.NAKED))
        assertFalse(H.onSessionCollected(PlayerFlags(), temple, listOf(frame(gear = emptyMap(), died = true))).has(H.NAKED))
        assertFalse(H.onSessionCollected(PlayerFlags(), temple, listOf(frame(gear = mapOf("head" to "bronze_full_helmet")))).has(H.NAKED))
    }

    @Test
    fun `bronze age needs a kurzul win in only bronze with no mercenaries`() {
        val kurzul = H.SessionInfo(skillName = "boss", activityKey = MainStory.KURZUL_BOSS)
        val bronze = mapOf("weapon" to "bronze_battleaxe", "head" to "bronze_full_helmet", "body" to "bronze_platebody")
        assertTrue(H.onSessionCollected(PlayerFlags(), kurzul, listOf(frame(kills = 1, gear = bronze))).has(H.BRONZE_AGE))
        assertFalse("mercenaries", H.onSessionCollected(PlayerFlags(), kurzul, listOf(frame(kills = 1, gear = bronze, allies = listOf(50)))).has(H.BRONZE_AGE))
        assertFalse("iron piece", H.onSessionCollected(PlayerFlags(), kurzul, listOf(frame(kills = 1, gear = bronze + ("legs" to "iron_platelegs")))).has(H.BRONZE_AGE))
        assertFalse("loss", H.onSessionCollected(PlayerFlags(), kurzul, listOf(frame(died = true, gear = bronze))).has(H.BRONZE_AGE))
        assertFalse("other boss", H.onSessionCollected(PlayerFlags(), kbd, listOf(frame(kills = 1, gear = bronze))).has(H.BRONZE_AGE))
    }

    // ------------------------------------------------------------------ economy

    @Test
    fun `selling feeds buyers remorse and junk dealer`() {
        assertTrue(H.onItemsSold(PlayerFlags(), mapOf("dwarven_sword" to 1), bulk = false).has(H.BUYERS_REMORSE))
        assertTrue(H.onItemsSold(PlayerFlags(), mapOf("divine_helm" to 1), bulk = true).has(H.BUYERS_REMORSE))
        val single = H.onItemsSold(PlayerFlags(), mapOf("bones" to 2_000_000), bulk = false)
        assertFalse("only bulk sales count", single.has(H.JUNK_DEALER))
        val almost = H.onItemsSold(PlayerFlags(), mapOf("bones" to 999_999), bulk = true)
        assertFalse(almost.has(H.JUNK_DEALER))
        assertTrue(H.onItemsSold(almost, mapOf("logs" to 1), bulk = true).has(H.JUNK_DEALER))
    }

    @Test
    fun `ossuary and indecisive are lifetime counters`() {
        assertFalse(H.onBonesBuried(PlayerFlags(), 999_999).has(H.OSSUARY))
        assertTrue(H.onBonesBuried(H.onBonesBuried(PlayerFlags(), 999_999), 1).has(H.OSSUARY))
        var f = PlayerFlags()
        repeat(24) { f = H.onRespec(f) }
        assertFalse(f.has(H.INDECISIVE))
        assertTrue(H.onRespec(f).has(H.INDECISIVE))
    }

    // ------------------------------------------------------------------ state

    private fun state(
        flags: PlayerFlags = PlayerFlags(),
        inventory: Map<String, Int> = emptyMap(),
        coins: Long = 0L,
        pets: Set<String> = emptySet(),
        now: Long = 0L,
    ) = H.earnedFromState(H.StateInput(
        flags = flags, inventory = inventory, equippedItems = emptySet(), coins = coins, ownedPetIds = pets,
        equipmentKeys = setOf("bronze_sword", "iron_sword"), titleIds = setOf("master_smith", "slayer"),
        seasonalSeries = setOf("bloomtide", "frostfall"), now = now,
    ))

    @Test
    fun `owning a whole drop pool grants the luck achievements its roll can no longer give`() {
        val divine = WeeklyQuestRepository.DIVINE_DROP_POOL.associateWith { 1 }
        val dwarven = DailyQuestRepository.DWARVEN_DROP_POOL.associateWith { 1 }
        assertEquals(setOf(H.DIVINE_DENIED), state(inventory = divine))
        assertEquals(setOf(H.DIVINE_DENIED, H.CURSED, H.TWICE_BLESSED), state(inventory = divine + dwarven))
        assertTrue(state(inventory = dwarven).isEmpty())
    }

    @Test
    fun `fresh player earns nothing`() {
        assertTrue(state().isEmpty())
    }

    @Test
    fun `state thresholds`() {
        assertTrue(H.EXTERMINATOR in state(PlayerFlags(enemyKills = mapOf("goblin" to 250_000))))
        assertTrue(H.REGULAR in state(PlayerFlags(dungeonRuns = mapOf("goblin_cave" to 5_000))))
        assertEquals(setOf(H.SKY_500), state(PlayerFlags(towerBestFloor = 999)))
        assertTrue(H.SKY_1000 in state(PlayerFlags(towerBestFloor = 1_000)))
        assertTrue(H.SEEN_IT_ALL in state(PlayerFlags(seenItemKeys = setOf("bronze_sword")), inventory = mapOf("iron_sword" to 1)))
        assertTrue(H.HOARDER in state(inventory = mapOf("bones" to 1_000_000)))
        assertTrue(H.SCROOGE in state(coins = 1_000_000_000L))
        assertTrue(H.ELDER_OBSESSION in state(pets = H.ELDER_ISLE_PET_IDS))
        assertFalse(H.ELDER_OBSESSION in state(pets = H.ELDER_ISLE_PET_IDS - "elder_familiar"))
    }

    @Test
    fun `veteran needs a stamped creation date a year old`() {
        assertFalse("unstamped legacy character", H.VETERAN in state(PlayerFlags(characterCreatedAt = 0L), now = H.VETERAN_MS * 2))
        assertTrue(H.VETERAN in state(PlayerFlags(characterCreatedAt = 1L), now = H.VETERAN_MS + 1))
    }

    @Test
    fun `iron will needs the finished story on an ironman`() {
        val done = PlayerFlags(storyStarted = true, storyChapter = MainStory.CHAPTERS.size)
        assertTrue(H.IRON_WILL in state(done.copy(ironman = true)))
        assertFalse(H.IRON_WILL in state(done))
        assertFalse(H.IRON_WILL in state(done.copy(ironman = true, storyChapter = MainStory.CHAPTERS.size - 1)))
    }

    @Test
    fun `seasonal banners and titles count once per series, any year`() {
        val banners = listOf(
            SeasonalBannerEarned(eventId = "bloomtide_2026", displayText = "", completedAtMs = 0L),
            SeasonalBannerEarned(eventId = "frostfall_2027", displayText = "", completedAtMs = 0L),
        )
        assertTrue(H.FESTIVAL_GOER in state(PlayerFlags(seasonalBannersEarned = banners)))
        assertFalse(H.FESTIVAL_GOER in state(PlayerFlags(seasonalBannersEarned = banners.take(1))))
        val titles = setOf("master_smith", "slayer", "seasonal_bloomtide_2026", "seasonal_frostfall_2026")
        assertTrue(H.TITLE_COLLECTOR in state(PlayerFlags(unlockedTitles = titles)))
        assertFalse(H.TITLE_COLLECTOR in state(PlayerFlags(unlockedTitles = titles - "slayer")))
        assertFalse(H.TITLE_COLLECTOR in state(PlayerFlags(unlockedTitles = titles - "seasonal_frostfall_2026")))
    }

    // ------------------------------------------------------------------ saves

    @Test
    fun `new fields survive a JSON round trip and old saves decode with defaults`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        val flags = PlayerFlags(
            hiddenAchievements = mapOf(H.CURSED to 5L),
            hiddenAnnounced = setOf(H.CURSED),
            lifetimeStats = mapOf(H.STAT_DEATHS to 3L),
        )
        assertEquals(flags, json.decodeFromString<PlayerFlags>(json.encodeToString(PlayerFlags.serializer(), flags)))
        val legacy = json.decodeFromString<PlayerFlags>("{}")
        assertTrue(legacy.hiddenAchievements.isEmpty() && legacy.hiddenAnnounced.isEmpty() && legacy.lifetimeStats.isEmpty())
        val oldFrame = json.decodeFromString<SessionFrame>("""{"minute":0,"xp_gain":0,"xp_before":0,"xp_after":0,"level_before":0,"level_after":0}""")
        assertEquals(null, oldFrame.gearAtStart)
    }
}
