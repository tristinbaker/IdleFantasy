package com.fantasyidler.simulator

import com.fantasyidler.data.json.ArenaCreature
import com.fantasyidler.data.json.ArenaTrainer
import com.fantasyidler.data.json.ArenaZone
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.SessionFrame
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pet Arena (2.1.0): pet levels, stats, evolutions and battle simulation. Pure Kotlin, so a
 * whole arena hour is pre-simulated at start like every other session.
 *
 * Types form a triangle (melee beats ranged, ranged beats magic, magic beats melee): the
 * favoured side hits harder and gets past defense more easily, using the same hit chance and
 * max hit shape as player combat.
 */
object PetBattle {

    const val STAGE_2_LEVEL = 30
    const val STAGE_3_LEVEL = 60
    const val MAX_LEVEL = 99
    const val MAX_TEAM = 3
    /** One attack per 2.4 s tick, as in player combat. */
    const val TICKS_PER_FRAME = 25
    const val FRAMES_PER_SESSION = 60
    /** Wild creatures fight below their level so a matched team wins most battles. */
    const val WILD_STRENGTH = 0.75
    const val TRAINER_STRENGTH = 1.0
    /** Pet XP per opponent level, given to every pet on the team for each battle won. */
    const val XP_PER_OPPONENT_LEVEL = 8L
    const val TRAINER_XP_MULTIPLIER = 5L
    /** Wild levels sit at most this far below the team's average, within the zone's range. */
    const val WILD_LEVEL_SPREAD_BELOW = 6
    const val WILD_LEVEL_SPREAD_ABOVE = 1

    val TYPES = listOf("melee", "ranged", "magic")
    private val BEATS = mapOf("melee" to "ranged", "ranged" to "magic", "magic" to "melee")

    val MAX_XP: Long get() = XpTable.xpForLevel(MAX_LEVEL)

    fun level(xp: Long): Int = XpTable.levelForXp(xp.coerceIn(0L, MAX_XP)).coerceIn(1, MAX_LEVEL)

    /** 1 below level 30, 2 from 30, 3 from 60. */
    fun stage(level: Int): Int = when {
        level >= STAGE_3_LEVEL -> 3
        level >= STAGE_2_LEVEL -> 2
        else                   -> 1
    }

    private fun stageMultiplier(level: Int): Double = when (stage(level)) {
        3    -> 1.2
        2    -> 1.1
        else -> 1.0
    }

    /** Harder-to-get pets fight with higher stats. */
    fun rarityMultiplier(rarity: String): Double = when (rarity) {
        "uncommon"  -> 1.04
        "rare"      -> 1.08
        "epic"      -> 1.14
        "legendary" -> 1.2
        else        -> 1.0
    }

    /** A pet's skill boost grows with its arena level: the base at level 1, double at 99. */
    fun boostPercent(base: Int, xp: Long): Int =
        if (base <= 0) base else base * (MAX_LEVEL - 2 + level(xp)) / (MAX_LEVEL - 1)

    /** 1.25 when [attacker]'s type beats [defender]'s, 0.8 when it loses to it, else 1. */
    fun typeMultiplier(attacker: String, defender: String): Double = when {
        BEATS[attacker] == defender -> 1.25
        BEATS[defender] == attacker -> 0.8
        else                        -> 1.0
    }

    data class Fighter(
        val id: String,
        val type: String,
        val level: Int,
        val attack: Int,
        val defense: Int,
        val maxHp: Int,
    )

    fun fighter(id: String, type: String, level: Int, baseAttack: Int, baseDefense: Int, baseHp: Int, strength: Double): Fighter {
        val m = strength * stageMultiplier(level)
        return Fighter(
            id      = id,
            type    = type,
            level   = level,
            attack  = ((level + 8) * baseAttack / 64.0 * m).roundToInt().coerceAtLeast(1),
            defense = ((level + 8) * baseDefense / 64.0 * m).roundToInt().coerceAtLeast(1),
            maxHp   = ((level * 2 + 20) * baseHp / 64.0 * m).roundToInt().coerceAtLeast(1),
        )
    }

    fun petFighter(pet: PetData, xp: Long): Fighter =
        fighter(pet.id, pet.battleType, level(xp), pet.baseAttack, pet.baseDefense, pet.baseHp, rarityMultiplier(pet.rarity))

    fun creatureFighter(creature: ArenaCreature, level: Int, strength: Double): Fighter =
        fighter(creature.id, creature.battleType, level, creature.baseAttack, creature.baseDefense, creature.baseHp, strength)

    fun hitChance(attacker: Fighter, defender: Fighter): Double {
        val atk = attacker.attack.coerceAtLeast(1)
        val def = (defender.defense / typeMultiplier(attacker.type, defender.type)).coerceAtLeast(1.0)
        return (if (atk > def) 1.0 - def / (2.0 * atk) else atk / (2.0 * def)).coerceIn(0.15, 0.95)
    }

    fun maxHit(attacker: Fighter, defender: Fighter): Int =
        max(1, ((1 + attacker.attack * 72 / 640.0) * typeMultiplier(attacker.type, defender.type)).toInt())

    private class Battle(val team: List<Fighter>, val foes: List<Fighter>) {
        val teamHp = IntArray(team.size) { team[it].maxHp }
        val foeHp = IntArray(foes.size) { foes[it].maxHp }
        fun teamAlive() = teamHp.any { it > 0 }
        fun foesAlive() = foeHp.any { it > 0 }
    }

    /** One tick: every standing fighter on both sides attacks a random standing enemy. */
    private fun tick(b: Battle, rnd: Random) {
        fun strike(attackers: List<Fighter>, attackerHp: IntArray, targets: List<Fighter>, targetHp: IntArray) {
            for (i in attackers.indices) {
                if (attackerHp[i] <= 0) continue
                val alive = targetHp.indices.filter { targetHp[it] > 0 }
                if (alive.isEmpty()) return
                val t = alive[rnd.nextInt(alive.size)]
                if (rnd.nextDouble() < hitChance(attackers[i], targets[t])) {
                    targetHp[t] -= rnd.nextInt(0, maxHit(attackers[i], targets[t]) + 1)
                }
            }
        }
        strike(b.team, b.teamHp, b.foes, b.foeHp)
        strike(b.foes, b.foeHp, b.team, b.teamHp)
    }

    private class FrameAcc {
        var wins = 0
        val defeated = mutableMapOf<String, Int>()
        val items = mutableMapOf<String, Int>()
        val petXp = mutableMapOf<String, Long>()
        var died = false
    }

    private fun toFrames(acc: List<FrameAcc>): List<SessionFrame> = acc.mapIndexed { minute, a ->
        SessionFrame(
            minute = minute, xpGain = a.petXp.values.sum().toInt(), xpBefore = 0L, xpAfter = 0L,
            levelBefore = 0, levelAfter = 0,
            items = a.items.toMap(), kills = a.wins, killsByEnemy = a.defeated.toMap(),
            died = a.died, petXp = a.petXp.toMap(),
        )
    }

    /**
     * An hour of wild battles in [zone]: back-to-back fights against 1 to 3 creatures near the
     * team's level (within the zone's range), healing fully between fights. A team wipe ends the
     * session at that minute, keeping everything won before it.
     */
    fun simulateZone(
        team: List<Fighter>,
        zone: ArenaZone,
        creatures: Map<String, ArenaCreature>,
        random: Random = Random.Default,
    ): List<SessionFrame> {
        require(team.isNotEmpty()) { "empty team" }
        val pool = zone.creatures.mapNotNull { creatures[it] }
        require(pool.isNotEmpty()) { "zone ${zone.id} has no creatures" }
        val totalTicks = FRAMES_PER_SESSION * TICKS_PER_FRAME
        val frames = List(FRAMES_PER_SESSION) { FrameAcc() }
        val teamLevel = team.sumOf { it.level } / team.size
        var t = 0
        while (t < totalTicks) {
            val foes = List(random.nextInt(1, MAX_TEAM + 1)) {
                val lvl = (teamLevel + random.nextInt(-WILD_LEVEL_SPREAD_BELOW, WILD_LEVEL_SPREAD_ABOVE + 1))
                    .coerceIn(zone.levelMin, zone.levelMax)
                creatureFighter(pool[random.nextInt(pool.size)], lvl, WILD_STRENGTH)
            }
            val b = Battle(team, foes)
            while (t < totalTicks && b.teamAlive() && b.foesAlive()) { tick(b, random); t++ }
            if (b.teamAlive() && b.foesAlive()) break  // the hour ran out mid-fight
            val minute = (t - 1).coerceAtLeast(0) / TICKS_PER_FRAME
            val frame = frames[minute]
            if (!b.teamAlive()) {
                frame.died = true
                return toFrames(frames.subList(0, minute + 1))
            }
            frame.wins++
            for (f in foes) frame.defeated[f.id] = (frame.defeated[f.id] ?: 0) + 1
            val xp = XP_PER_OPPONENT_LEVEL * foes.sumOf { it.level }
            for (p in team) frame.petXp[p.id] = (frame.petXp[p.id] ?: 0L) + xp
            for (drop in zone.loot) {
                if (random.nextDouble() < drop.chance) {
                    val qty = if (drop.quantityMax > drop.quantityMin) random.nextInt(drop.quantityMin, drop.quantityMax + 1) else drop.quantityMin
                    if (qty > 0) frame.items[drop.item] = (frame.items[drop.item] ?: 0) + qty
                }
            }
            t++  // a breath between battles
        }
        return toFrames(frames)
    }

    /** One fight against [trainer]'s team. Frames run until it ends; a win is a single kill on the last frame. */
    fun simulateTrainer(
        team: List<Fighter>,
        trainer: ArenaTrainer,
        creatures: Map<String, ArenaCreature>,
        random: Random = Random.Default,
    ): List<SessionFrame> {
        require(team.isNotEmpty()) { "empty team" }
        val foes = trainer.team.mapNotNull { creatures[it] }.map { creatureFighter(it, trainer.level, TRAINER_STRENGTH) }
        require(foes.isNotEmpty()) { "trainer ${trainer.id} has no team" }
        val totalTicks = FRAMES_PER_SESSION * TICKS_PER_FRAME
        val b = Battle(team, foes)
        var t = 0
        while (t < totalTicks && b.teamAlive() && b.foesAlive()) { tick(b, random); t++ }
        val frames = List((t + TICKS_PER_FRAME - 1).coerceAtLeast(1) / TICKS_PER_FRAME) { FrameAcc() }
        val last = frames.last()
        if (b.teamAlive() && !b.foesAlive()) {
            last.wins = 1
            last.defeated[trainer.id] = 1
            val xp = XP_PER_OPPONENT_LEVEL * TRAINER_XP_MULTIPLIER * foes.sumOf { it.level }
            for (p in team) last.petXp[p.id] = xp
        } else {
            last.died = true  // knocked out, or the hour ran out: either way the trainer holds
        }
        return toFrames(frames)
    }
}
