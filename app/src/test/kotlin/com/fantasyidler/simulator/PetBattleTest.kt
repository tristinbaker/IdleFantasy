package com.fantasyidler.simulator

import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.PetArenaData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.PetArenaRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Pet Arena (2.1.0): levels, boosts, the type triangle, battles and the shipped arena data. */
class PetBattleTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private fun dataFile(name: String): File =
        listOf("src/main/assets/data/$name", "app/src/main/assets/data/$name")
            .map(::File).firstOrNull { it.exists() }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")

    private val pets: Map<String, PetData> by lazy { json.decodeFromString(dataFile("pets.json").readText()) }
    private val arena: PetArenaData by lazy { json.decodeFromString(dataFile("pet_arena.json").readText()) }

    private fun xpAt(level: Int) = XpTable.xpForLevel(level)

    // ------------------------------------------------------------------ progression

    @Test
    fun `skill boosts grow from the base at level 1 to double at 99`() {
        assertEquals(10, PetBattle.boostPercent(10, 0L))
        assertEquals(20, PetBattle.boostPercent(10, xpAt(99)))
        assertEquals(15, PetBattle.boostPercent(10, xpAt(50)))
        assertEquals(0, PetBattle.boostPercent(0, xpAt(99)))
    }

    @Test
    fun `pets evolve at 30 and 60 and cap at 99`() {
        assertEquals(1, PetBattle.stage(29))
        assertEquals(2, PetBattle.stage(30))
        assertEquals(3, PetBattle.stage(60))
        assertEquals(99, PetBattle.level(Long.MAX_VALUE))
        assertEquals(1, PetBattle.level(-5L))
    }

    @Test
    fun `each type beats the next and loses to the one before`() {
        fun f(type: String) = PetBattle.fighter("x", type, 50, 64, 64, 64, 1.0)
        for ((strong, weak) in listOf("melee" to "ranged", "ranged" to "magic", "magic" to "melee")) {
            assertTrue("$strong should out-hit $weak", PetBattle.hitChance(f(strong), f(weak)) > PetBattle.hitChance(f(weak), f(strong)))
            assertTrue(PetBattle.maxHit(f(strong), f(weak)) > PetBattle.maxHit(f(weak), f(strong)))
        }
    }

    @Test
    fun `harder-to-get pets start with higher stats`() {
        val order = listOf("common", "uncommon", "rare", "epic", "legendary")
        val stats = order.map { PetBattle.fighter("x", "melee", 1, 64, 64, 64, PetBattle.rarityMultiplier(it)) }
        for (i in 1 until stats.size) assertTrue(order[i], stats[i].maxHp >= stats[i - 1].maxHp && stats[i].attack >= stats[i - 1].attack)
        assertTrue(stats.last().maxHp > stats.first().maxHp)
    }

    // ------------------------------------------------------------------ battles

    private fun team(level: Int, rarity: String = "common") =
        PetBattle.TYPES.map { PetBattle.fighter("pet_$it", it, level, 64, 64, 64, PetBattle.rarityMultiplier(rarity)) }

    @Test
    fun `zone battles are reproducible and pay XP to every team pet`() {
        val zone = arena.zones.first()
        val a = PetBattle.simulateZone(team(10), zone, arena.creatures, Random(7))
        val b = PetBattle.simulateZone(team(10), zone, arena.creatures, Random(7))
        assertEquals(a, b)
        assertTrue(a.size <= PetBattle.FRAMES_PER_SESSION)
        val xp = a.flatMap { it.petXp.keys }.toSet()
        assertEquals(setOf("pet_melee", "pet_ranged", "pet_magic"), xp)
        val lootKeys = zone.loot.map { it.item }.toSet()
        assertTrue(a.flatMap { it.items.keys }.all { it in lootKeys })
    }

    @Test
    fun `a wipe ends the session on that minute`() {
        val zone = arena.zones.last()
        val frames = PetBattle.simulateZone(team(1), zone, arena.creatures, Random(1))
        assertTrue(frames.last().died)
        assertEquals(1, frames.count { it.died })
        assertTrue(frames.size < PetBattle.FRAMES_PER_SESSION)
    }

    private fun wipePercent(zoneIndex: Int, level: Int, seeds: Int = 60): Int {
        val zone = arena.zones[zoneIndex]
        return (1..seeds).count { seed -> PetBattle.simulateZone(team(level), zone, arena.creatures, Random(seed)).any { it.died } } * 100 / seeds
    }

    @Test
    fun `every zone carries real risk, more at the bottom of its range than the top`() {
        for ((i, zone) in arena.zones.withIndex()) {
            val bottom = wipePercent(i, zone.levelMin)
            val mid = wipePercent(i, (zone.levelMin + zone.levelMax) / 2)
            val top = wipePercent(i, zone.levelMax)
            assertTrue("${zone.id} mid-range wipes $mid% of hours", mid in 5..40)
            assertTrue("${zone.id} top of range wipes $top%", top <= 15)
            assertTrue("${zone.id} bottom $bottom% should be riskier than top $top%", bottom >= top)
        }
    }

    @Test
    fun `a fresh team takes many hours to reach level 40`() {
        for (seedBase in listOf(1, 100)) {
            val xp = LongArray(3)
            var hours = 0
            while (PetBattle.level(xp.min()) < 40 && hours < 100) {
                val lvl = PetBattle.level(xp.min())
                val zone = arena.zones.last { it.levelMin <= lvl }
                val fighters = PetBattle.TYPES.mapIndexed { k, t -> PetBattle.fighter("pet_$t", t, PetBattle.level(xp[k]), 64, 64, 64, 1.0) }
                val frames = PetBattle.simulateZone(fighters, zone, arena.creatures, Random(seedBase + hours))
                for (k in 0..2) xp[k] += frames.sumOf { it.petXp["pet_${PetBattle.TYPES[k]}"] ?: 0L }
                hours++
            }
            assertTrue("level 40 after $hours hours", hours in 10..25)
        }
    }

    @Test
    fun `wounds carry between wild battles and a win restores half`() {
        val events = PetBattle.replayZone(team(30), arena.zones[2], arena.creatures, 9)
        val starts = events.filterIsInstance<PetBattle.BattleStart>()
        assertTrue(starts.first().teamHp == team(30).map { it.maxHp })
        assertTrue("later battles start wounded", starts.drop(1).any { s -> s.teamHp.zip(team(30)).any { (hp, f) -> hp < f.maxHp } })
    }

    @Test
    fun `alphas appear alone and always pay the zone's rarest drop`() {
        val zone = arena.zones[1]
        val prize = zone.loot.filter { it.item != "coins" }.minBy { it.chance }.item
        var alphas = 0
        var alphaWins = 0
        for (seed in 1..40) {
            val events = PetBattle.replayZone(team(30), zone, arena.creatures, seed.toLong())
            val frames = PetBattle.simulateZone(team(30), zone, arena.creatures, Random(seed.toLong()))
            for ((i, e) in events.withIndex()) {
                if (e !is PetBattle.BattleStart || e.foes.none { it.alpha }) continue
                alphas++
                assertEquals(1, e.foes.size)
                val end = events.drop(i + 1).firstOrNull { it is PetBattle.BattleEnd || it is PetBattle.TimeUp }
                if (end is PetBattle.BattleEnd && end.won) {
                    alphaWins++
                    val minute = end.tick / PetBattle.TICKS_PER_FRAME
                    assertTrue("alpha win pays $prize", (frames[minute].items[prize] ?: 0) >= 1)
                }
            }
        }
        assertTrue("alphas show up: $alphas", alphas > 0)
        assertTrue("some alphas are beaten: $alphaWins", alphaWins > 0)
    }

    @Test
    fun `a wild wipe halves the loot and coins`() {
        val (items, coins) = PetArenaRepository.halveLoot(mapOf("cowhide" to 7, "sapphire" to 1), 101L)
        assertEquals(mapOf("cowhide" to 3), items)
        assertEquals(50L, coins)
    }

    @Test
    fun `trainers need a team a few levels above them`() {
        for (trainer in arena.trainers) {
            val wins = (1..40).count { seed ->
                PetBattle.simulateTrainer(team(trainer.level + 8), trainer, arena.creatures, Random(seed)).last().kills == 1
            }
            assertTrue("${trainer.id} beaten $wins/40 at +8 levels", wins >= 32)
            val underdog = (1..40).count { seed ->
                PetBattle.simulateTrainer(team((trainer.level - 8).coerceAtLeast(1)), trainer, arena.creatures, Random(seed)).last().kills == 1
            }
            assertTrue("${trainer.id} should usually beat a weaker team, lost $underdog/40", underdog <= 12)
        }
    }

    @Test
    fun `a trainer fight is one battle with the win on its last frame`() {
        val trainer = arena.trainers.first()
        val frames = PetBattle.simulateTrainer(team(40), trainer, arena.creatures, Random(3))
        assertEquals(1, frames.sumOf { it.kills })
        assertEquals(mapOf(trainer.id to 1), frames.last().killsByEnemy)
        assertFalse(frames.any { it.died })
    }

    // ------------------------------------------------------------------ data

    @Test
    fun `every pet has battle stats`() {
        assertEquals(39, pets.size)
        for (p in pets.values) {
            assertTrue(p.id, p.battleType in PetBattle.TYPES)
            assertTrue(p.id, p.rarity in setOf("common", "uncommon", "rare", "epic", "legendary", "mythic"))
            assertTrue(p.id, p.baseAttack > 0 && p.baseDefense > 0 && p.baseHp > 0)
        }
    }

    @Test
    fun `arena data references only real creatures and items`() {
        assertEquals(5, arena.zones.size)
        assertEquals(8, arena.trainers.size)
        for (z in arena.zones) assertTrue(z.id, z.creatures.all { it in arena.creatures })
        for (t in arena.trainers) assertTrue(t.id, t.team.isNotEmpty() && t.team.all { it in arena.creatures })
        val dropped = Json.parseToJsonElement(dataFile("enemies.json").readText()).jsonObject.values.flatMap { e ->
            e.jsonObject["drop_table"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("item").jsonPrimitive.content }
        }.toSet()
        val cooking: Map<String, CookingRecipe> = json.decodeFromString(dataFile("recipes/cooking.json").readText())
        val known = dropped + cooking.values.map { it.cookedItem } +
            Json.parseToJsonElement(dataFile("bones.json").readText()).jsonObject.keys +
            Json.parseToJsonElement(dataFile("gems.json").readText()).jsonObject.keys +
            Json.parseToJsonElement(dataFile("crops.json").readText()).jsonObject.keys +
            Json.parseToJsonElement(dataFile("recipes/crafting.json").readText()).jsonObject.keys + "coins"
        for (z in arena.zones) for (l in z.loot) assertTrue("${z.id} drops unknown ${l.item}", l.item in known)
        for (t in arena.trainers) for (k in t.rewardItems.keys) assertTrue("${t.id} rewards unknown $k", k in known)
        for (treat in arena.treats.keys) {
            val recipe = cooking.values.firstOrNull { it.cookedItem == treat } ?: error("$treat has no cooking recipe")
            assertEquals("treats must not count as food", 0, recipe.healingValue)
        }
    }

    @Test
    fun `the ladder unlocks one trainer and zone at a time`() {
        val trainers = arena.trainers
        assertTrue(PetArenaRepository.trainerUnlocked(trainers, 0, emptySet()))
        assertFalse(PetArenaRepository.trainerUnlocked(trainers, 1, emptySet()))
        assertTrue(PetArenaRepository.trainerUnlocked(trainers, 1, setOf(trainers[0].id)))
        assertTrue(PetArenaRepository.zoneUnlocked(arena.zones.first(), emptySet()))
        assertFalse(PetArenaRepository.zoneUnlocked(arena.zones[1], emptySet()))
        assertTrue(PetArenaRepository.zoneUnlocked(arena.zones[1], setOf(trainers[0].id)))
    }

    @Test
    fun `pets rest from knocked out to full in two hours`() {
        assertEquals(1.0, PetBattle.restedFraction(1.0, 0L, 123L), 0.0)
        assertEquals(0.5, PetBattle.restedFraction(0.0, 1_000L, 1_000L + PetBattle.REST_FULL_MS / 2), 1e-9)
        assertEquals(1.0, PetBattle.restedFraction(0.2, 1_000L, 1_000L + PetBattle.REST_FULL_MS * 3), 0.0)
        assertEquals(PetBattle.REST_FULL_MS / 4, PetBattle.msToFullRest(0.75))
    }

    @Test
    fun `treats heal a share of max HP and the best one sets Well Fed`() {
        val carrot = arena.treats.getValue("carrot_treat")
        val starfruit = arena.treats.getValue("starfruit_treat")
        assertEquals(0.45 to carrot.wellFed, PetBattle.feed(0.3, 0, carrot, 1).let { (f, w) -> (Math.round(f * 100) / 100.0) to w })
        assertEquals(1.0, PetBattle.feed(0.9, 0, starfruit, 1).first, 0.0)
        assertEquals("a weaker treat never lowers Well Fed", starfruit.wellFed, PetBattle.feed(0.5, starfruit.wellFed, carrot, 1).second)
        assertEquals(5, PetBattle.treatsToHeal(0.3, carrot))
        assertEquals("full HP still takes one, for Well Fed", 1, PetBattle.treatsToHeal(1.0, carrot))
        for (t in arena.treats.values) assertTrue(t.heal in 0.05..1.0 && t.wellFed > 0)
    }

    @Test
    fun `Well Fed raises attack and defense, not HP`() {
        val pet = pets.getValue("rock_golem")
        val plain = PetBattle.petFighter(pet, xpAt(40))
        val fed = PetBattle.petFighter(pet, xpAt(40), wellFed = 10)
        assertTrue(fed.attack > plain.attack && fed.defense > plain.defense)
        assertEquals(plain.maxHp, fed.maxHp)
        assertEquals(10, fed.wellFed)
    }

    @Test
    fun `wounded pets start wounded and their HP at the end is recorded`() {
        val zone = arena.zones[1]
        val fighters = team(25)
        val start = fighters.map { it.maxHp / 3 }
        val events = PetBattle.replayZone(fighters, zone, arena.creatures, 4, start)
        assertEquals(start, events.filterIsInstance<PetBattle.BattleStart>().first().teamHp)
        val frames = PetBattle.simulateZone(fighters, zone, arena.creatures, Random(4), start)
        val end = frames.last().petHpEnd
        assertEquals(fighters.map { it.id }.toSet(), end.keys)
        if (frames.any { it.died }) assertTrue(end.values.all { it == 0 })
        for (f in fighters) assertTrue((end[f.id] ?: -1) in 0..f.maxHp)
        assertTrue("starting at a third of HP is riskier than starting full",
            (1..30).count { PetBattle.simulateZone(fighters, zone, arena.creatures, Random(it), start).any { f -> f.died } } >=
            (1..30).count { PetBattle.simulateZone(fighters, zone, arena.creatures, Random(it)).any { f -> f.died } })
    }

    // A typical late-game Ultimate team: 3 melee, 3 ranged, 2 magic, mostly common with a few rarer pets.
    private fun ultimateTeam(level: Int, size: Int = 8): List<PetBattle.Fighter> {
        val rarities = listOf("common", "common", "common", "common", "uncommon", "uncommon", "rare", "rare")
        val types = listOf("melee", "ranged", "magic", "melee", "ranged", "magic", "melee", "ranged")
        return (0 until size).map { PetBattle.fighter("u$it", types[it], level, 64, 64, 64, PetBattle.rarityMultiplier(rarities[it])) }
    }

    private fun ultimateWins(team: List<PetBattle.Fighter>, seeds: Int = 100) =
        (1..seeds).count { PetBattle.simulateUltimate(team, arena.ultimate!!, Random(it)).last().kills == 1 }

    @Test
    fun `only 8 fully trained pets can beat the ultimate`() {
        val full = ultimateWins(ultimateTeam(99))
        assertTrue("8 level 99 pets win sometimes: $full/100", full in 25..75)
        assertTrue("8 level 95 pets almost never win", ultimateWins(ultimateTeam(95)) <= 2)
        assertEquals("7 pets never win", 0, ultimateWins(ultimateTeam(99, 7)))
    }

    @Test
    fun `the ultimate shifts type, enrages and hits the whole team`() {
        val u = arena.ultimate!!
        val events = PetBattle.replayUltimate(ultimateTeam(99), u, 3)
        assertTrue((events.first() as PetBattle.BattleStart).ultimate)
        val shifts = events.filterIsInstance<PetBattle.PhaseShift>().map { it.type }
        assertTrue("cycles every type: $shifts", shifts.toSet() == u.phases.toSet())
        assertTrue(events.any { it is PetBattle.Enrage && it.tick == u.enrageTurn } || events.last().tick < u.enrageTurn)
        val area = events.filterIsInstance<PetBattle.AreaAttack>()
        assertTrue(area.isNotEmpty())
        assertTrue("an area attack hits every standing pet", area.first().hits.size == 8)
    }

    @Test
    fun `an ultimate win rolls the rare pet`() {
        val u = arena.ultimate!!
        assertEquals(PetBattle.AETHER_PET, u.petDrop)
        assertEquals(0.01, u.petChance, 0.0)
        assertTrue(u.petDrop in pets)
        val sure = u.copy(petChance = 1.0)
        val win = (1..30).map { PetBattle.simulateUltimate(ultimateTeam(99), sure, Random(it)) }.first { it.last().kills == 1 }
        assertEquals(1, win.last().items[u.petDrop])
    }

    @Test
    fun `ironmen keep only the aether hatchling's boost`() {
        val owned = listOf(OwnedPet("rock_golem", 10), OwnedPet(PetBattle.AETHER_PET, 5))
        assertEquals(owned, PetBattle.boostingPets(owned, ironman = false))
        assertEquals(listOf(PetBattle.AETHER_PET), PetBattle.boostingPets(owned, ironman = true).map { it.id })
    }

    @Test
    fun `old saves load with level 1 pets and no arena progress`() {
        val pet = json.decodeFromString<OwnedPet>("""{"id":"rock_golem","boost_percent":10}""")
        assertEquals(0L, pet.xp)
        assertEquals(1.0, pet.hpFraction, 0.0)
        assertEquals(0, pet.wellFed)
        val flags = json.decodeFromString<PlayerFlags>("{}")
        assertEquals(null, flags.petArenaStarter)
        assertTrue(flags.petTeam.isEmpty() && flags.petBadges.isEmpty())
        val round = json.decodeFromString<OwnedPet>(json.encodeToString(OwnedPet.serializer(), pet.copy(xp = 1234L)))
        assertEquals(1234L, round.xp)
    }
}
