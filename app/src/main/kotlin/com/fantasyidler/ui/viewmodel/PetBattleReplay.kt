package com.fantasyidler.ui.viewmodel

import com.fantasyidler.simulator.PetBattle
import com.fantasyidler.simulator.PetBattle.Fighter

/** One line in the battle screen's message box; the screen turns it into text. */
sealed interface BattleLine {
    data class WildAppeared(val foes: List<Fighter>) : BattleLine
    data class AlphaAppeared(val foe: Fighter) : BattleLine
    data class BossAppeared(val boss: Fighter) : BattleLine
    data class BossShifted(val boss: Fighter, val type: String) : BattleLine
    data class BossEnraged(val boss: Fighter) : BattleLine
    data class BossArea(val boss: Fighter, val move: String) : BattleLine
    data class TrainerChallenge(val trainerId: String) : BattleLine
    data class TrainerSentOut(val trainerId: String, val foes: List<Fighter>) : BattleLine
    data class Go(val team: List<Fighter>) : BattleLine
    data class Used(val user: Fighter, val move: String) : BattleLine
    data class Missed(val user: Fighter) : BattleLine
    data object SuperEffective : BattleLine
    data object NotVeryEffective : BattleLine
    data object Critical : BattleLine
    data class HitTimes(val times: Int) : BattleLine
    data class Softened(val target: Fighter) : BattleLine
    data class Braced(val user: Fighter) : BattleLine
    data class Burned(val target: Fighter) : BattleLine
    data class BurnHurt(val target: Fighter) : BattleLine
    data class Fainted(val fighter: Fighter) : BattleLine
    data class Won(val xpEach: Long) : BattleLine
    data object Lost : BattleLine
    data object TimeUp : BattleLine
}

data class FighterView(
    val fighter: Fighter,
    val hp: Int,
    val burned: Boolean,
    val guarded: Boolean,
    val fainted: Boolean,
    /** Took a hit or a burn in the last moment (flash it). */
    val hurt: Boolean,
    /** Just made a move (lunge it). */
    val attacking: Boolean,
)

data class ReplayView(
    val battleNumber: Int,
    val wins: Int,
    val trainerId: String?,
    val team: List<FighterView>,
    val foes: List<FighterView>,
    val lines: List<BattleLine>,
    /** Every event has played (the session is over). */
    val finished: Boolean,
    /** The Ultimate boss has enraged. */
    val enraged: Boolean = false,
)

/**
 * Rebuilds the battle at any moment from the seeded event stream ([PetBattle.replayZone]).
 * Time is measured in ticks (2.4 s each); the events of one tick are spread across it in
 * order (burns, then the team's moves, then the foes'), so the screen plays them one by one.
 */
class PetBattleReplay(private val team: List<Fighter>, events: List<PetBattle.Event>) {

    /** Each event with its playback time in ticks. */
    private val timed: List<Pair<Double, PetBattle.Event>> = buildList {
        var i = 0
        while (i < events.size) {
            val tick = events[i].tick
            var j = i
            while (j < events.size && events[j].tick == tick) j++
            val n = j - i
            for (k in 0 until n) add(tick + k.toDouble() / n to events[i + k])
            i = j
        }
    }
    private val winTimes: List<Double> = timed.filter { (_, e) -> e is PetBattle.BattleEnd && e.won }.map { it.first }
    val lastTime: Double = timed.lastOrNull()?.first ?: 0.0

    private fun upperBound(list: List<Double>, pos: Double): Int {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid] <= pos) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private val times: List<Double> = timed.map { it.first }

    /** The battle as it stands at playback time [pos] (in ticks since the session started). */
    fun at(pos: Double, maxLines: Int = 4, recent: Double = 0.15): ReplayView {
        val end = upperBound(times, pos)
        var startIdx = end - 1
        while (startIdx >= 0 && timed[startIdx].second !is PetBattle.BattleStart) startIdx--
        val start = timed.getOrNull(startIdx)?.second as? PetBattle.BattleStart
        // The Ultimate boss changes type mid-fight, so foes are tracked as they stand right now.
        val foes = start?.foes.orEmpty().toMutableList()
        var enraged = false

        // HP carries between wild battles; each battle starts from what the team brought in.
        val teamHp = IntArray(team.size) { start?.teamHp?.getOrNull(it) ?: team[it].maxHp }
        val foeHp = IntArray(foes.size) { foes[it].maxHp }
        val teamBurn = BooleanArray(team.size)
        val foeBurn = BooleanArray(foes.size)
        val teamGuard = BooleanArray(team.size)
        val foeGuard = BooleanArray(foes.size)
        val teamHurt = BooleanArray(team.size)
        val foeHurt = BooleanArray(foes.size)
        val teamActing = BooleanArray(team.size)
        val foeActing = BooleanArray(foes.size)
        val lines = ArrayDeque<BattleLine>()
        fun say(line: BattleLine) { lines.addLast(line); while (lines.size > maxLines) lines.removeFirst() }
        fun fighter(side: PetBattle.Side, i: Int): Fighter? = if (side == PetBattle.Side.TEAM) team.getOrNull(i) else foes.getOrNull(i)

        if (start != null) {
            if (start.trainerId != null) {
                say(BattleLine.TrainerChallenge(start.trainerId))
                say(BattleLine.TrainerSentOut(start.trainerId, foes))
            } else if (start.ultimate) {
                say(BattleLine.BossAppeared(foes[0]))
            } else if (foes.size == 1 && foes[0].alpha) {
                say(BattleLine.AlphaAppeared(foes[0]))
            } else {
                say(BattleLine.WildAppeared(foes))
            }
            say(BattleLine.Go(team))
        }
        for (k in (startIdx + 1) until end) {
            val (time, e) = timed[k]
            val fresh = pos - time < recent
            when (e) {
                is PetBattle.Attack -> {
                    val user = fighter(e.side, e.attacker) ?: continue
                    val target = fighter(if (e.side == PetBattle.Side.TEAM) PetBattle.Side.FOES else PetBattle.Side.TEAM, e.target) ?: continue
                    val onTeam = e.side == PetBattle.Side.TEAM
                    val targetHp = if (onTeam) foeHp else teamHp
                    val targetGuard = if (onTeam) foeGuard else teamGuard
                    if (fresh) { if (onTeam) teamActing[e.attacker] = true else foeActing[e.attacker] = true }
                    say(BattleLine.Used(user, e.move))
                    val landed = e.hits.filter { !it.missed }
                    if (landed.isEmpty()) say(BattleLine.Missed(user))
                    for (h in landed) {
                        targetHp[e.target] -= h.damage
                        if (h.guarded) { targetGuard[e.target] = false; say(BattleLine.Softened(target)) }
                        if (h.crit) say(BattleLine.Critical)
                    }
                    if (landed.any { it.damage > 0 } && fresh) { if (onTeam) foeHurt[e.target] = true else teamHurt[e.target] = true }
                    if (e.hits.size > 1 && landed.size > 1) say(BattleLine.HitTimes(landed.size))
                    if (landed.isNotEmpty()) when (e.effectiveness) {
                        1  -> say(BattleLine.SuperEffective)
                        -1 -> say(BattleLine.NotVeryEffective)
                    }
                    when (e.effect) {
                        "guard" -> { if (onTeam) teamGuard[e.attacker] = true else foeGuard[e.attacker] = true; say(BattleLine.Braced(user)) }
                        "burn"  -> { if (onTeam) foeBurn[e.target] = true else teamBurn[e.target] = true; say(BattleLine.Burned(target)) }
                    }
                }
                is PetBattle.BurnDamage -> {
                    val target = fighter(e.side, e.index) ?: continue
                    if (e.side == PetBattle.Side.TEAM) { teamHp[e.index] -= e.damage; if (fresh) teamHurt[e.index] = true }
                    else { foeHp[e.index] -= e.damage; if (fresh) foeHurt[e.index] = true }
                    say(BattleLine.BurnHurt(target))
                }
                is PetBattle.Faint -> {
                    val f = fighter(e.side, e.index) ?: continue
                    if (e.side == PetBattle.Side.TEAM) teamBurn[e.index] = false else foeBurn[e.index] = false
                    say(BattleLine.Fainted(f))
                }
                is PetBattle.BattleEnd -> say(if (e.won) BattleLine.Won(e.xpEach) else BattleLine.Lost)
                is PetBattle.TimeUp -> say(BattleLine.TimeUp)
                is PetBattle.PhaseShift -> if (foes.isNotEmpty()) {
                    foes[0] = foes[0].copy(type = e.type)
                    say(BattleLine.BossShifted(foes[0], e.type))
                }
                is PetBattle.Enrage -> if (foes.isNotEmpty()) { enraged = true; say(BattleLine.BossEnraged(foes[0])) }
                is PetBattle.AreaAttack -> if (foes.isNotEmpty()) {
                    say(BattleLine.BossArea(foes[0], e.move))
                    for ((i, h) in e.hits) if (i in teamHp.indices) {
                        teamHp[i] -= h.damage
                        if (h.guarded) teamGuard[i] = false
                        if (h.damage > 0 && fresh) teamHurt[i] = true
                    }
                }
                is PetBattle.BattleStart -> Unit
            }
        }
        fun views(fs: List<Fighter>, hp: IntArray, burn: BooleanArray, guard: BooleanArray, hurt: BooleanArray, acting: BooleanArray) =
            fs.mapIndexed { i, f ->
                FighterView(f, hp[i].coerceIn(0, f.maxHp), burn[i] && hp[i] > 0, guard[i] && hp[i] > 0, hp[i] <= 0, hurt[i], acting[i])
            }
        return ReplayView(
            battleNumber = start?.number ?: 0,
            wins         = upperBound(winTimes, pos),
            trainerId    = start?.trainerId,
            team         = views(team, teamHp, teamBurn, teamGuard, teamHurt, teamActing),
            foes         = views(foes, foeHp, foeBurn, foeGuard, foeHurt, foeActing),
            lines        = lines.toList(),
            finished     = pos >= lastTime,
            enraged      = enraged,
        )
    }
}
