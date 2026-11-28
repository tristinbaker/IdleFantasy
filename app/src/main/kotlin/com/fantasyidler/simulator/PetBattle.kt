package com.fantasyidler.simulator

import com.fantasyidler.data.json.ArenaCreature
import com.fantasyidler.data.json.ArenaTrainer
import com.fantasyidler.data.json.ArenaUltimate
import com.fantasyidler.data.json.ArenaZone
import com.fantasyidler.data.json.ArenaTreat
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.SessionFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pet Arena (2.1.0): pet levels, stats, evolutions and battle simulation. Pure Kotlin, so a
 * whole arena hour is pre-simulated at start like every other session.
 *
 * Types form a triangle (melee beats ranged, ranged beats magic, magic beats melee): the
 * favoured side hits harder and gets past defense more easily, using the same hit chance and
 * max hit shape as player combat. Each fighter attacks with its type's move for its evolution
 * stage; from the first evolution it sometimes uses its type's special move instead (melee
 * Iron Guard halves the next hit taken, ranged Double Shot hits twice, magic Flame Burst
 * burns for 3 turns), and any hit can be a critical.
 *
 * Simulation is seeded and deterministic, so the battle screen replays the exact fight from
 * the session's seed and starting team ([Event]s) instead of storing every hit.
 */
object PetBattle {

    const val STAGE_2_LEVEL = 30
    const val STAGE_3_LEVEL = 60
    const val MAX_LEVEL = 99
    const val MAX_TEAM = 3
    /** One attack per 2.4 s tick, as in player combat. */
    const val TICKS_PER_FRAME = 25
    const val FRAMES_PER_SESSION = 60
    /** Default wild strength; each zone sets its own (pet_arena.json) so every zone carries similar risk. */
    const val WILD_STRENGTH = 0.82
    const val TRAINER_STRENGTH = 1.0
    /** Pet XP per opponent level, given to every pet on the team for each battle won. */
    const val XP_PER_OPPONENT_LEVEL = 4L
    const val TRAINER_XP_MULTIPLIER = 5L
    /** Wild levels sit at most this far below the team's average, within the zone's range. */
    /** Wild levels land within this share of the team's average level, either way (at least 2 levels). */
    const val WILD_LEVEL_SPREAD = 0.08

    fun wildSpread(teamLevel: Int): Int = maxOf(2, (teamLevel * WILD_LEVEL_SPREAD).toInt())
    /** HP carries over between wild battles; each win restores this share of every pet's max HP. */
    const val RECOVER_AFTER_WIN = 0.5
    /** About 1 wild battle in 20 is a lone Alpha: higher level, stronger, and it drops the zone's rarest item. */
    const val ALPHA_CHANCE = 1.0 / 20
    const val ALPHA_LEVELS_ABOVE = 5
    const val ALPHA_STRENGTH = 1.4
    const val ALPHA_XP_MULTIPLIER = 2L
    /** The rarest pet, from the Ultimate Challenge: its skill boost is the one pet boost ironmen keep. */
    const val AETHER_PET = "aether_hatchling"

    /** Pets whose skill boosts count: every pet normally, only the Aether Hatchling for ironmen. */
    fun boostingPets(pets: List<OwnedPet>, ironman: Boolean): List<OwnedPet> =
        if (ironman) pets.filter { it.id == AETHER_PET } else pets

    /** A pet rests from 0 HP back to full in this long (wounds carry between sessions). */
    const val REST_FULL_MS = 2 * 3_600_000L

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
        "mythic"    -> 1.3
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

    @Serializable
    data class Fighter(
        val id: String,
        val type: String,
        val level: Int,
        val attack: Int,
        val defense: Int,
        @SerialName("max_hp") val maxHp: Int,
        /** A wild Alpha (see [ALPHA_CHANCE]). */
        val alpha: Boolean = false,
        /** Well Fed bonus (%) already folded into attack and defense. */
        @SerialName("well_fed") val wellFed: Int = 0,
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

    fun petFighter(pet: PetData, xp: Long, wellFed: Int = 0): Fighter {
        val f = fighter(pet.id, pet.battleType, level(xp), pet.baseAttack, pet.baseDefense, pet.baseHp, rarityMultiplier(pet.rarity))
        if (wellFed <= 0) return f
        val m = 1 + wellFed / 100.0
        return f.copy(attack = (f.attack * m).roundToInt(), defense = (f.defense * m).roundToInt(), wellFed = wellFed)
    }

    // ------------------------------------------------------------------ wounds, rest and treats

    /** HP share at [now] for a pet left at [hpFraction] at [hpAt], resting back to full over [REST_FULL_MS]. */
    fun restedFraction(hpFraction: Double, hpAt: Long, now: Long): Double =
        if (hpAt <= 0L) hpFraction.coerceIn(0.0, 1.0)
        else (hpFraction + (now - hpAt).coerceAtLeast(0L).toDouble() / REST_FULL_MS).coerceIn(0.0, 1.0)

    /** Milliseconds until a pet at [fraction] HP is fully rested. */
    fun msToFullRest(fraction: Double): Long = ((1.0 - fraction.coerceIn(0.0, 1.0)) * REST_FULL_MS).toLong()

    /** Feeding [qty] of [treat] to a pet at [fraction] HP: its new HP share and Well Fed bonus (the best treat counts). */
    fun feed(fraction: Double, wellFed: Int, treat: ArenaTreat, qty: Int): Pair<Double, Int> =
        (fraction + treat.heal * qty).coerceAtMost(1.0) to maxOf(wellFed, treat.wellFed)

    /** Treats of this kind it takes to bring a pet at [fraction] HP to full (at least 1, for the Well Fed bonus). */
    fun treatsToHeal(fraction: Double, treat: ArenaTreat): Int =
        kotlin.math.ceil(((1.0 - fraction).coerceAtLeast(0.0) / treat.heal) - 1e-9).toInt().coerceAtLeast(1)

    fun creatureFighter(creature: ArenaCreature, level: Int, strength: Double): Fighter =
        fighter(creature.id, creature.battleType, level, creature.baseAttack, creature.baseDefense, creature.baseHp, strength)

    fun hitChance(attacker: Fighter, defender: Fighter): Double {
        val atk = attacker.attack.coerceAtLeast(1)
        val def = (defender.defense / typeMultiplier(attacker.type, defender.type)).coerceAtLeast(1.0)
        return (if (atk > def) 1.0 - def / (2.0 * atk) else atk / (2.0 * def)).coerceIn(0.15, 0.95)
    }

    fun maxHit(attacker: Fighter, defender: Fighter): Int =
        max(1, ((1 + attacker.attack * 72 / 640.0) * typeMultiplier(attacker.type, defender.type)).toInt())

    // ------------------------------------------------------------------ moves

    const val SPECIAL_CHANCE = 0.25
    const val CRIT_CHANCE = 1.0 / 16
    const val BURN_TURNS = 3

    /** Regular move per type, by evolution stage. Keys resolve to pet_move_<key> strings. */
    private val MOVES = mapOf(
        "melee"  to listOf("tackle", "slam", "crush"),
        "ranged" to listOf("quick_shot", "volley", "piercing_shot"),
        "magic"  to listOf("zap", "arcane_bolt", "starfall"),
    )
    private val SPECIALS = mapOf("melee" to "iron_guard", "ranged" to "double_shot", "magic" to "flame_burst")

    val ALL_MOVES: List<String> get() = MOVES.values.flatten() + SPECIALS.values

    fun moveFor(f: Fighter): String = MOVES[f.type]?.getOrNull(stage(f.level) - 1) ?: "tackle"
    fun specialFor(f: Fighter): String = SPECIALS[f.type] ?: "iron_guard"
    /** Special moves unlock with the first evolution. */
    fun hasSpecial(f: Fighter): Boolean = stage(f.level) >= 2

    /** +1 when [attacker]'s type beats [defender]'s ("super effective"), -1 when it loses to it. */
    fun effectiveness(attacker: Fighter, defender: Fighter): Int = when {
        BEATS[attacker.type] == defender.type -> 1
        BEATS[defender.type] == attacker.type -> -1
        else                                  -> 0
    }

    // ------------------------------------------------------------------ battle events (for the replay screen)

    enum class Side { TEAM, FOES }

    sealed interface Event { val tick: Int }

    /** Battle [number] of the session begins against [foes] (wild, or [trainerId]'s team); [teamHp] is the team's HP going in. */
    data class BattleStart(
        override val tick: Int,
        val number: Int,
        val foes: List<Fighter>,
        val trainerId: String?,
        val teamHp: List<Int>,
        /** The Ultimate Challenge boss fight. */
        val ultimate: Boolean = false,
    ) : Event

    data class Hit(val damage: Int, val missed: Boolean, val crit: Boolean, val guarded: Boolean)

    /** One move. [effect] is "guard" or "burn" when the special move's effect took hold. */
    data class Attack(
        override val tick: Int,
        val side: Side,
        val attacker: Int,
        val target: Int,
        val move: String,
        val effectiveness: Int,
        val hits: List<Hit>,
        val effect: String?,
    ) : Event

    data class BurnDamage(override val tick: Int, val side: Side, val index: Int, val damage: Int) : Event
    data class Faint(override val tick: Int, val side: Side, val index: Int) : Event
    data class BattleEnd(override val tick: Int, val won: Boolean, val xpEach: Long) : Event
    /** The hour ran out mid-fight; that battle pays nothing. */
    data class TimeUp(override val tick: Int) : Event
    /** The Ultimate boss strikes the whole team at once; [hits] are by team index. */
    data class AreaAttack(override val tick: Int, val move: String, val hits: Map<Int, Hit>) : Event
    /** The Ultimate boss switches to a new type. */
    data class PhaseShift(override val tick: Int, val type: String) : Event
    /** The Ultimate boss enrages: from now on its damage is multiplied. */
    data class Enrage(override val tick: Int) : Event

    // ------------------------------------------------------------------ engine

    private class Combatant(var f: Fighter) {
        var hp = f.maxHp
        var burnTurns = 0
        var burnDamage = 0
        var guard = false
        val up: Boolean get() = hp > 0
    }

    private class Battle(team: List<Fighter>, foes: List<Fighter>, teamHp: IntArray? = null) {
        val team = team.mapIndexed { i, f -> Combatant(f).also { c -> if (teamHp != null) c.hp = teamHp[i] } }
        val foes = foes.map(::Combatant)
        fun teamAlive() = team.any { it.up }
        fun foesAlive() = foes.any { it.up }
    }

    /** One tick: burns hurt first, then every standing fighter on each side makes one move. */
    private fun tick(b: Battle, t: Int, rnd: Random, emit: ((Event) -> Unit)?) {
        burns(b, t, emit)
        strike(b.team, Side.TEAM, b.foes, Side.FOES, t, rnd, emit)
        strike(b.foes, Side.FOES, b.team, Side.TEAM, t, rnd, emit)
    }

    private fun burns(b: Battle, t: Int, emit: ((Event) -> Unit)?) {
        for ((side, list) in listOf(Side.TEAM to b.team, Side.FOES to b.foes)) {
            for ((i, c) in list.withIndex()) {
                if (!c.up || c.burnTurns <= 0) continue
                c.burnTurns--
                c.hp -= c.burnDamage
                emit?.invoke(BurnDamage(t, side, i, c.burnDamage))
                if (!c.up) emit?.invoke(Faint(t, side, i))
            }
        }
    }

    private fun strike(attackers: List<Combatant>, side: Side, targets: List<Combatant>, targetSide: Side, t: Int, rnd: Random, emit: ((Event) -> Unit)?) {
        for ((i, a) in attackers.withIndex()) {
            if (!a.up) continue
            val alive = targets.indices.filter { targets[it].up }
            if (alive.isEmpty()) return
            val ti = alive[rnd.nextInt(alive.size)]
            val target = targets[ti]
            val special = hasSpecial(a.f) && rnd.nextDouble() < SPECIAL_CHANCE
            val shots = if (special && a.f.type == "ranged") 2 else 1
            val hits = ArrayList<Hit>(shots)
            var landed = false
            for (shot in 0 until shots) {
                if (!target.up) break
                if (rnd.nextDouble() < hitChance(a.f, target.f)) {
                    val maxDmg = maxHit(a.f, target.f)
                    val crit = rnd.nextDouble() < CRIT_CHANCE
                    var dmg = if (crit) rnd.nextInt(maxDmg / 2, maxDmg + 1) * 3 / 2 else rnd.nextInt(0, maxDmg + 1)
                    val guarded = target.guard && dmg > 0
                    if (guarded) { dmg /= 2; target.guard = false }
                    target.hp -= dmg
                    landed = true
                    hits += Hit(dmg, missed = false, crit = crit, guarded = guarded)
                } else {
                    hits += Hit(0, missed = true, crit = false, guarded = false)
                }
            }
            var effect: String? = null
            if (special && a.f.type == "melee") {
                a.guard = true
                effect = "guard"
            }
            if (special && a.f.type == "magic" && landed && target.up && target.burnTurns <= 0) {
                target.burnTurns = BURN_TURNS
                target.burnDamage = max(1, maxHit(a.f, target.f) / 3)
                effect = "burn"
            }
            emit?.invoke(Attack(t, side, i, ti, if (special) specialFor(a.f) else moveFor(a.f), effectiveness(a.f, target.f), hits, effect))
            if (!target.up) emit?.invoke(Faint(t, targetSide, ti))
        }
    }

    private class FrameAcc {
        var wins = 0
        val defeated = mutableMapOf<String, Int>()
        val items = mutableMapOf<String, Int>()
        val petXp = mutableMapOf<String, Long>()
        var died = false
        var hpEnd: Map<String, Int> = emptyMap()
    }

    private fun toFrames(acc: List<FrameAcc>): List<SessionFrame> = acc.mapIndexed { minute, a ->
        SessionFrame(
            minute = minute, xpGain = a.petXp.values.sum().toInt(), xpBefore = 0L, xpAfter = 0L,
            levelBefore = 0, levelAfter = 0,
            items = a.items.toMap(), kills = a.wins, killsByEnemy = a.defeated.toMap(),
            died = a.died, petXp = a.petXp.toMap(), petHpEnd = a.hpEnd,
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
        /** Each pet's HP going in (null = full); wounds from earlier sessions carry over. */
        startHp: List<Int>? = null,
        onEvent: ((Event) -> Unit)? = null,
    ): List<SessionFrame> {
        require(team.isNotEmpty()) { "empty team" }
        val pool = zone.creatures.mapNotNull { creatures[it] }
        require(pool.isNotEmpty()) { "zone ${zone.id} has no creatures" }
        val totalTicks = FRAMES_PER_SESSION * TICKS_PER_FRAME
        val frames = List(FRAMES_PER_SESSION) { FrameAcc() }
        val teamLevel = team.sumOf { it.level } / team.size
        val spread = wildSpread(teamLevel)
        val strength = zone.strength ?: WILD_STRENGTH
        // The zone's rarest drop (coins aside) is the Alpha's guaranteed prize.
        val alphaPrize = zone.loot.filter { it.item != "coins" }.minByOrNull { it.chance }
        val teamHp = IntArray(team.size) { startHp?.getOrNull(it)?.coerceIn(0, team[it].maxHp) ?: team[it].maxHp }
        fun hpMap() = team.indices.associate { team[it].id to teamHp[it].coerceAtLeast(0) }
        var t = 0
        var number = 0
        while (t < totalTicks) {
            val alpha = random.nextDouble() < ALPHA_CHANCE
            val foes = if (alpha) {
                val lvl = (teamLevel + random.nextInt(-spread, spread + 1))
                    .coerceIn(zone.levelMin, zone.levelMax) + ALPHA_LEVELS_ABOVE
                listOf(creatureFighter(pool[random.nextInt(pool.size)], lvl, strength * ALPHA_STRENGTH).copy(alpha = true))
            } else List(random.nextInt(1, MAX_TEAM + 1)) {
                val lvl = (teamLevel + random.nextInt(-spread, spread + 1))
                    .coerceIn(zone.levelMin, zone.levelMax)
                creatureFighter(pool[random.nextInt(pool.size)], lvl, strength)
            }
            onEvent?.invoke(BattleStart(t, ++number, foes, null, teamHp.toList()))
            val b = Battle(team, foes, teamHp)
            while (t < totalTicks && b.teamAlive() && b.foesAlive()) { tick(b, t, random, onEvent); t++ }
            if (b.teamAlive() && b.foesAlive()) {  // the hour ran out mid-fight
                onEvent?.invoke(TimeUp(t - 1))
                for (i in team.indices) teamHp[i] = b.team[i].hp.coerceAtLeast(0)
                break
            }
            val minute = (t - 1).coerceAtLeast(0) / TICKS_PER_FRAME
            val frame = frames[minute]
            if (!b.teamAlive()) {
                onEvent?.invoke(BattleEnd(t - 1, won = false, xpEach = 0L))
                frame.died = true
                frame.hpEnd = team.associate { it.id to 0 }
                return toFrames(frames.subList(0, minute + 1))
            }
            frame.wins++
            for (f in foes) frame.defeated[f.id] = (frame.defeated[f.id] ?: 0) + 1
            // Shared across the team, Pokemon style: a bigger team levels each pet more slowly.
            val xp = XP_PER_OPPONENT_LEVEL * foes.sumOf { it.level } * (if (alpha) ALPHA_XP_MULTIPLIER else 1L) / team.size
            for (p in team) frame.petXp[p.id] = (frame.petXp[p.id] ?: 0L) + xp
            for (drop in zone.loot) {
                if (random.nextDouble() < drop.chance) {
                    val qty = if (drop.quantityMax > drop.quantityMin) random.nextInt(drop.quantityMin, drop.quantityMax + 1) else drop.quantityMin
                    if (qty > 0) frame.items[drop.item] = (frame.items[drop.item] ?: 0) + qty
                }
            }
            if (alpha && alphaPrize != null) frame.items[alphaPrize.item] = (frame.items[alphaPrize.item] ?: 0) + alphaPrize.quantityMin.coerceAtLeast(1)
            onEvent?.invoke(BattleEnd(t - 1, won = true, xpEach = xp))
            // Wounds carry into the next fight; a win buys a short breather (fainted pets get back up).
            for (i in team.indices) {
                teamHp[i] = (b.team[i].hp.coerceAtLeast(0) + (team[i].maxHp * RECOVER_AFTER_WIN).toInt()).coerceAtMost(team[i].maxHp)
            }
            t++  // a breath between battles
        }
        frames.last().hpEnd = hpMap()
        return toFrames(frames)
    }

    /** One fight against [trainer]'s team. Frames run until it ends; a win is a single kill on the last frame. */
    fun simulateTrainer(
        team: List<Fighter>,
        trainer: ArenaTrainer,
        creatures: Map<String, ArenaCreature>,
        random: Random = Random.Default,
        startHp: List<Int>? = null,
        onEvent: ((Event) -> Unit)? = null,
    ): List<SessionFrame> {
        require(team.isNotEmpty()) { "empty team" }
        val foes = trainer.team.mapNotNull { creatures[it] }.map { creatureFighter(it, trainer.level, TRAINER_STRENGTH) }
        require(foes.isNotEmpty()) { "trainer ${trainer.id} has no team" }
        val totalTicks = FRAMES_PER_SESSION * TICKS_PER_FRAME
        val hp = IntArray(team.size) { startHp?.getOrNull(it)?.coerceIn(0, team[it].maxHp) ?: team[it].maxHp }
        onEvent?.invoke(BattleStart(0, 1, foes, trainer.id, hp.toList()))
        val b = Battle(team, foes, hp)
        var t = 0
        while (t < totalTicks && b.teamAlive() && b.foesAlive()) { tick(b, t, random, onEvent); t++ }
        val frames = List((t + TICKS_PER_FRAME - 1).coerceAtLeast(1) / TICKS_PER_FRAME) { FrameAcc() }
        val last = frames.last()
        val won = b.teamAlive() && !b.foesAlive()
        val xp = if (won) XP_PER_OPPONENT_LEVEL * TRAINER_XP_MULTIPLIER * foes.sumOf { it.level } / team.size else 0L
        if (won) {
            last.wins = 1
            last.defeated[trainer.id] = 1
            for (p in team) last.petXp[p.id] = xp
        } else {
            last.died = true  // knocked out, or the hour ran out: either way the trainer holds
        }
        last.hpEnd = team.indices.associate { team[it].id to b.team[it].hp.coerceAtLeast(0) }
        onEvent?.invoke(BattleEnd((t - 1).coerceAtLeast(0), won, xp))
        return toFrames(frames)
    }

    /** Boss move names by its current type (pet_move_<key> strings), and its area attack. */
    private val BOSS_MOVES = mapOf("melee" to "primal_rend", "ranged" to "sky_lance", "magic" to "aether_flare")
    const val BOSS_AREA_MOVE = "aether_storm"

    fun ultimateFighter(u: ArenaUltimate): Fighter =
        Fighter(u.id, u.phases.first(), u.level, u.attack, u.defense, u.maxHp)

    /**
     * The Ultimate Challenge: the team against one boss that makes [ArenaUltimate.attacksPerTurn]
     * attacks a turn, strikes everyone every [ArenaUltimate.areaEvery] turns, cycles its type
     * every [ArenaUltimate.phaseTurns] turns and enrages at [ArenaUltimate.enrageTurn]. A win is a
     * single kill on the last frame and rolls the rare pet and the loot.
     */
    fun simulateUltimate(
        team: List<Fighter>,
        u: ArenaUltimate,
        random: Random = Random.Default,
        startHp: List<Int>? = null,
        onEvent: ((Event) -> Unit)? = null,
    ): List<SessionFrame> {
        require(team.isNotEmpty()) { "empty team" }
        val totalTicks = FRAMES_PER_SESSION * TICKS_PER_FRAME
        val hp = IntArray(team.size) { startHp?.getOrNull(it)?.coerceIn(0, team[it].maxHp) ?: team[it].maxHp }
        val boss = ultimateFighter(u)
        onEvent?.invoke(BattleStart(0, 1, listOf(boss), null, hp.toList(), ultimate = true))
        val b = Battle(team, listOf(boss), hp)
        val bc = b.foes[0]
        var t = 0
        while (t < totalTicks && b.teamAlive() && b.foesAlive()) {
            if (t > 0 && t % u.phaseTurns == 0) {
                bc.f = bc.f.copy(type = u.phases[(t / u.phaseTurns) % u.phases.size])
                onEvent?.invoke(PhaseShift(t, bc.f.type))
            }
            if (t == u.enrageTurn) onEvent?.invoke(Enrage(t))
            val rage = if (t >= u.enrageTurn) u.enrageMultiplier else 1.0
            burns(b, t, onEvent)
            strike(b.team, Side.TEAM, b.foes, Side.FOES, t, random, onEvent)
            if (bc.up) {
                repeat(u.attacksPerTurn) {
                    val alive = b.team.indices.filter { b.team[it].up }
                    if (alive.isEmpty()) return@repeat
                    val ti = alive[random.nextInt(alive.size)]
                    val target = b.team[ti]
                    val hit = bossHit(bc.f, target, rage, 1.0, random)
                    onEvent?.invoke(Attack(t, Side.FOES, 0, ti, BOSS_MOVES[bc.f.type] ?: "primal_rend", effectiveness(bc.f, target.f), listOf(hit), null))
                    if (!target.up) onEvent?.invoke(Faint(t, Side.TEAM, ti))
                }
                if (t > 0 && t % u.areaEvery == 0) {
                    val hits = b.team.indices.filter { b.team[it].up }.associateWith { i -> bossHit(bc.f, b.team[i], rage, u.areaFactor, random) }
                    onEvent?.invoke(AreaAttack(t, BOSS_AREA_MOVE, hits))
                    for (i in hits.keys) if (!b.team[i].up) onEvent?.invoke(Faint(t, Side.TEAM, i))
                }
            }
            t++
        }
        val frames = List((t + TICKS_PER_FRAME - 1).coerceAtLeast(1) / TICKS_PER_FRAME) { FrameAcc() }
        val last = frames.last()
        val won = b.teamAlive() && !b.foesAlive()
        if (won) {
            last.wins = 1
            last.defeated[u.id] = 1
            for (p in team) last.petXp[p.id] = u.xpEach
            for (drop in u.loot) {
                if (random.nextDouble() < drop.chance) {
                    val qty = if (drop.quantityMax > drop.quantityMin) random.nextInt(drop.quantityMin, drop.quantityMax + 1) else drop.quantityMin
                    if (qty > 0) last.items[drop.item] = (last.items[drop.item] ?: 0) + qty
                }
            }
            if (random.nextDouble() < u.petChance) last.items[u.petDrop] = 1
        } else {
            last.died = true
        }
        last.hpEnd = team.indices.associate { team[it].id to b.team[it].hp.coerceAtLeast(0) }
        onEvent?.invoke(BattleEnd((t - 1).coerceAtLeast(0), won, if (won) u.xpEach else 0L))
        return toFrames(frames)
    }

    /** One boss hit on [target] (guard halves it), scaled by enrage [rage] and [factor] for area attacks. */
    private fun bossHit(boss: Fighter, target: Combatant, rage: Double, factor: Double, rnd: Random): Hit {
        if (rnd.nextDouble() >= hitChance(boss, target.f)) return Hit(0, missed = true, crit = false, guarded = false)
        val maxDmg = maxHit(boss, target.f)
        val crit = rnd.nextDouble() < CRIT_CHANCE
        var dmg = ((if (crit) rnd.nextInt(maxDmg / 2, maxDmg + 1) * 3 / 2 else rnd.nextInt(0, maxDmg + 1)) * rage * factor).toInt()
        val guarded = target.guard && dmg > 0
        if (guarded) { dmg /= 2; target.guard = false }
        target.hp -= dmg
        return Hit(dmg, missed = false, crit = crit, guarded = guarded)
    }

    fun replayUltimate(team: List<Fighter>, u: ArenaUltimate, seed: Long, startHp: List<Int>? = null): List<Event> =
        buildList { simulateUltimate(team, u, Random(seed), startHp) { add(it) } }

    /** Every event of a seeded zone session, for the replay screen; same seed and team, same fight. */
    fun replayZone(team: List<Fighter>, zone: ArenaZone, creatures: Map<String, ArenaCreature>, seed: Long, startHp: List<Int>? = null): List<Event> =
        buildList { simulateZone(team, zone, creatures, Random(seed), startHp) { add(it) } }

    fun replayTrainer(team: List<Fighter>, trainer: ArenaTrainer, creatures: Map<String, ArenaCreature>, seed: Long, startHp: List<Int>? = null): List<Event> =
        buildList { simulateTrainer(team, trainer, creatures, Random(seed), startHp) { add(it) } }
}
