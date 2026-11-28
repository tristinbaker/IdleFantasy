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

    @Test
    fun `a team in the middle of a zone's range rarely wipes`() {
        for (zone in arena.zones) {
            val mid = (zone.levelMin + zone.levelMax) / 2
            val safe = (1..30).count { seed -> PetBattle.simulateZone(team(mid), zone, arena.creatures, Random(seed)).none { it.died } }
            assertTrue("${zone.id} at level $mid survived $safe/30", safe >= 27)
        }
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
        assertEquals(38, pets.size)
        for (p in pets.values) {
            assertTrue(p.id, p.battleType in PetBattle.TYPES)
            assertTrue(p.id, p.rarity in setOf("common", "uncommon", "rare", "epic", "legendary"))
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
    fun `old saves load with level 1 pets and no arena progress`() {
        val pet = json.decodeFromString<OwnedPet>("""{"id":"rock_golem","boost_percent":10}""")
        assertEquals(0L, pet.xp)
        val flags = json.decodeFromString<PlayerFlags>("{}")
        assertEquals(null, flags.petArenaStarter)
        assertTrue(flags.petTeam.isEmpty() && flags.petBadges.isEmpty())
        val round = json.decodeFromString<OwnedPet>(json.encodeToString(OwnedPet.serializer(), pet.copy(xp = 1234L)))
        assertEquals(1234L, round.xp)
    }
}
