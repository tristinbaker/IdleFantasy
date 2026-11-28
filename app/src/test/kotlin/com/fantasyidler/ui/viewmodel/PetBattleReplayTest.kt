package com.fantasyidler.ui.viewmodel

import com.fantasyidler.data.json.PetArenaData
import com.fantasyidler.simulator.PetBattle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The Pokemon-style battle screen replays the exact seeded fight (PetBattle events + PetBattleReplay). */
class PetBattleReplayTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val arena: PetArenaData by lazy {
        val f = listOf("src/main/assets/data/pet_arena.json", "app/src/main/assets/data/pet_arena.json").map(::File).first { it.exists() }
        json.decodeFromString(f.readText())
    }

    private fun team(level: Int) = PetBattle.TYPES.map { PetBattle.fighter("pet_$it", it, level, 64, 64, 64, 1.0) }

    @Test
    fun `listening for events never changes the fight`() {
        val zone = arena.zones[1]
        val plain = PetBattle.simulateZone(team(25), zone, arena.creatures, Random(42))
        val events = mutableListOf<PetBattle.Event>()
        val watched = PetBattle.simulateZone(team(25), zone, arena.creatures, Random(42)) { events += it }
        assertEquals(plain, watched)
        assertEquals(plain.sumOf { it.kills }, events.count { it is PetBattle.BattleEnd && it.won })
        assertEquals(events, PetBattle.replayZone(team(25), zone, arena.creatures, 42))
    }

    @Test
    fun `special moves unlock with the first evolution`() {
        val zone = arena.zones[0]
        fun teamMoves(level: Int) = PetBattle.replayZone(team(level), zone, arena.creatures, 7)
            .filterIsInstance<PetBattle.Attack>().filter { it.side == PetBattle.Side.TEAM }.map { it.move }.toSet()
        val specials = setOf("iron_guard", "double_shot", "flame_burst")
        assertTrue(teamMoves(10).none { it in specials })
        assertEquals(setOf("tackle", "quick_shot", "zap"), teamMoves(10))
        val evolved = teamMoves(45)
        assertTrue("evolved pets use their specials: $evolved", evolved.containsAll(specials))
        assertTrue(evolved.containsAll(setOf("slam", "volley", "arcane_bolt")))
    }

    @Test
    fun `crits burns and guards all happen over an hour`() {
        val events = PetBattle.replayZone(team(45), arena.zones[2], arena.creatures, 11)
        val attacks = events.filterIsInstance<PetBattle.Attack>()
        assertTrue(attacks.any { a -> a.hits.any { it.crit } })
        assertTrue(attacks.any { it.effect == "burn" })
        assertTrue(attacks.any { it.effect == "guard" })
        assertTrue(events.any { it is PetBattle.BurnDamage })
        assertTrue(attacks.any { a -> a.hits.any { it.guarded } })
    }

    @Test
    fun `the replay tracks hp, faints and wins at every moment`() {
        val team = team(25)
        val events = PetBattle.replayZone(team, arena.zones[1], arena.creatures, 3)
        val replay = PetBattleReplay(team, events)
        var pos = 0.0
        var lastWins = 0
        while (pos <= replay.lastTime) {
            val v = replay.at(pos)
            for (f in v.team + v.foes) assertTrue(f.hp in 0..f.fighter.maxHp)
            assertTrue("wins never go down", v.wins >= lastWins)
            lastWins = v.wins
            pos += 0.37
        }
        val end = replay.at(Double.MAX_VALUE)
        assertTrue(end.finished)
        assertEquals(events.count { it is PetBattle.BattleEnd && it.won }, end.wins)
        when (val last = events.last()) {
            is PetBattle.BattleEnd -> {
                if (last.won) assertTrue(end.foes.all { it.fainted }) else assertTrue(end.team.all { it.fainted })
                assertTrue(end.lines.last() is BattleLine.Won || end.lines.last() is BattleLine.Lost)
            }
            is PetBattle.TimeUp -> assertEquals(BattleLine.TimeUp, end.lines.last())
            else -> error("a session ends with a battle result or time running out, not $last")
        }
    }

    @Test
    fun `the ultimate replay announces the boss and its enrage`() {
        val u = arena.ultimate!!
        val team = (0 until 8).map { PetBattle.fighter("u$it", PetBattle.TYPES[it % 3], 99, 64, 64, 64, 1.08) }
        val replay = PetBattleReplay(team, PetBattle.replayUltimate(team, u, 2))
        assertTrue(replay.at(0.0).lines.first() is BattleLine.BossAppeared)
        val end = replay.at(Double.MAX_VALUE)
        assertEquals(8, end.team.size)
        if (replay.lastTime >= u.enrageTurn) assertTrue(end.enraged)
    }

    @Test
    fun `a trainer battle opens with the challenge`() {
        val trainer = arena.trainers.first()
        val replay = PetBattleReplay(team(30), PetBattle.replayTrainer(team(30), trainer, arena.creatures, 5))
        val first = replay.at(0.0)
        assertEquals(trainer.id, first.trainerId)
        assertEquals(BattleLine.TrainerChallenge(trainer.id), first.lines.first())
        assertFalse(first.finished)
    }
}
