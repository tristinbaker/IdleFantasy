package com.fantasyidler.repository

import com.fantasyidler.data.json.PrestigeSkillTreeData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.PrestigeBoosts
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Single source of truth for every player-facing multiplier (v1.14.0 boost unification).
 *
 * Combines the 2x XP boost purchase, church blessings, and prestige-node effects,
 * with ironman zeroing handled here rather than at each call site. Pet boosts keep
 * their existing per-call plumbing (they are per-equipped-pet and session-specific).
 */
@Singleton
class BoostRepository @Inject constructor(
    private val gameData: GameDataRepository,
) {
    private val trees: Map<String, PrestigeSkillTreeData> get() = gameData.prestigeTrees

    fun effectTotal(skill: String, flags: PlayerFlags, effect: String): Double =
        PrestigeBoosts.effectTotal(trees, flags, skill, effect)

    /**
     * Combined 2x-boost factor for [skill]: the purchased 48h boost (non-ironman only)
     * and the post-prestige 48h boost for this skill (earned, so ironmen included)
     * stack multiplicatively — 1, 2, or 4 (issue #1523).
     */
    fun xpBoostFactor(skill: String, flags: PlayerFlags, now: Long = System.currentTimeMillis()): Long =
        (if (!flags.ironman && flags.xpBoostExpiresAt > now) 2L else 1L) *
            (if ((flags.prestigeXpBoosts[skill] ?: 0L) > now) 2L else 1L)

    /**
     * Combined XP multiplier for [skill]: purchased and post-prestige 2x boosts, church
     * blessing, and prestige xp_pct nodes. Purchases and blessings are inert for ironmen.
     */
    fun xpMultiplier(skill: String, flags: PlayerFlags, prayerCapeMult: Float, now: Long = System.currentTimeMillis()): Double {
        val prestigeMult = 1.0 + effectTotal(skill, flags, PrestigeBoosts.XP_PCT) / 100.0
        val boostMult = xpBoostFactor(skill, flags, now).toDouble()
        // Prestige effects are earned, not bought, so they apply to ironmen too; the
        // purchased boost (excluded in xpBoostFactor) and church blessings stay inert.
        if (flags.ironman) return boostMult * prestigeMult
        val blessingMult = ChurchRepository.xpMultiplier(flags, prayerCapeMult, gameData.blessings).toDouble()
        return boostMult * blessingMult * prestigeMult
    }

    /** Item yield multiplier for [skill] from prestige yield_pct nodes. */
    fun yieldMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.YIELD_PCT) / 100.0

    /** Flow-state multiplier for [elapsedMs] of continuous [skill] activity. */
    fun flowMultiplier(skill: String, flags: PlayerFlags, elapsedMs: Long): Double =
        PrestigeBoosts.flowMultiplier(trees, flags, skill, elapsedMs)

    /**
     * Extra effective combat levels for [skill] (replaces the legacy +5 per prestige).
     * [level] is the skill's current base level, feeding the human Resourceful nodes.
     */
    fun combatStatBonus(skill: String, flags: PlayerFlags, level: Int): Int =
        effectTotal(skill, flags, PrestigeBoosts.COMBAT_STAT_FLAT).toInt() +
            // Rounded, not floored, so max Resourceful reaches +5 at 99 (4.95) instead
            // of sitting at +4 from level 80 all the way to the cap.
            (effectTotal(skill, flags, PrestigeBoosts.PER_LEVEL_BONUS) * level).roundToInt()

    /** Minutes shaved off the level-99 session floor (agility Endurance nodes). */
    fun sessionFloorReductionMin(flags: PlayerFlags): Double =
        effectTotal(Skills.AGILITY, flags, PrestigeBoosts.SESSION_FLOOR_MIN)

    /** Cape-bonus scaling per skill for [resolveCapeMultiplier] call sites. */
    fun capeScalingBySkill(flags: PlayerFlags): Map<String, Int> =
        PrestigeBoosts.capeScalingBySkill(trees, flags)

    /** Unspent prestige points per skill (skills with 0 unspent are omitted). */
    fun unspentPointsBySkill(flags: PlayerFlags): Map<String, Int> =
        trees.keys.mapNotNull { skill ->
            val unspent = PrestigeBoosts.unspentPoints(trees, flags, skill)
            if (unspent > 0) skill to unspent else null
        }.toMap()

    /** Prestige XP percent for [skill] alone (for display breakdowns). */
    fun prestigeXpPct(skill: String, flags: PlayerFlags): Int =
        effectTotal(skill, flags, PrestigeBoosts.XP_PCT).toInt()

    /** Chance (0..1) of a second melee hit against a still-living enemy (orc Double Hit nodes). */
    fun doubleHitChance(flags: PlayerFlags): Double =
        effectTotal(Skills.STRENGTH, flags, PrestigeBoosts.DOUBLE_HIT_PCT) / 100.0

    /** True when missed melee accuracy rolls are rerolled once (attack Second Chance node). */
    fun secondChanceActive(flags: PlayerFlags): Boolean =
        effectTotal(Skills.ATTACK, flags, PrestigeBoosts.SECOND_CHANCE) >= 1.0

    /** Extra slayer foretell queue slots from Foresight nodes. */
    fun foretellSlotBonus(flags: PlayerFlags): Int =
        effectTotal(Skills.SLAYER, flags, PrestigeBoosts.FORETELL_SLOTS).toInt()

    /** True when dungeon kills also progress matching foretold tasks (Foresight final tier). */
    fun slayerMultiTaskActive(flags: PlayerFlags): Boolean =
        effectTotal(Skills.SLAYER, flags, PrestigeBoosts.SLAYER_MULTI_TASK) >= 1.0

    /** Recipe keys unlocked by owned unlock_recipe nodes (race-checked via activeNodes). */
    fun unlockedRecipeKeys(flags: PlayerFlags): Set<String> =
        trees.keys.flatMapTo(mutableSetOf()) { skill ->
            PrestigeBoosts.activeNodes(trees, flags, skill)
                .mapNotNull { (_, node) -> if (node.effect == PrestigeBoosts.UNLOCK_RECIPE) node.unlock else null }
        }

    /** Every recipe key gated behind an unlock_recipe node, owned or not. */
    val gatedRecipeKeys: Set<String> by lazy {
        trees.values.flatMapTo(mutableSetOf()) { tree ->
            tree.paths.flatMap { p -> p.nodes.mapNotNull { it.unlock } }
        }
    }

    /** Multiplier on secondary drop chances (mining gem rolls). */
    fun bonusRollMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.BONUS_ROLL_PCT) / 100.0

    /** Coin multiplier for [skill] session payouts from prestige coin_pct nodes. */
    fun coinMultiplier(skill: String, flags: PlayerFlags): Double =
        1.0 + effectTotal(skill, flags, PrestigeBoosts.COIN_PCT) / 100.0

    /** Farming yield bonus percent; [rotated] = the new crop differs from the patch's last one. */
    fun cropRotationBonusPct(flags: PlayerFlags, rotated: Boolean): Double {
        val always = effectTotal(Skills.FARMING, flags, PrestigeBoosts.CROP_ROTATION_ALWAYS) > 0.0
        if (!rotated && !always) return 0.0
        return effectTotal(Skills.FARMING, flags, PrestigeBoosts.CROP_ROTATION_PCT)
    }

    /**
     * Continuous-activity time credited to flow-state at collection: this session's
     * wall clock, extended by the streak of immediately preceding sessions of the
     * same skill in the recent activity log.
     */
    fun flowElapsedMs(flags: PlayerFlags, skill: String, sessionDurationMs: Long): Long {
        val streak = flags.recentSessions.takeWhile { it.skillName == skill }.count()
        return sessionDurationMs * (streak + 1)
    }

    /**
     * Gathering tool efficiency multiplier for [skill] (pickaxe, axe, rod).
     * [level] is the skill's current base level, feeding the human Resourceful nodes.
     */
    fun toolEffMultiplier(skill: String, flags: PlayerFlags, level: Int): Float =
        (1.0 + (effectTotal(skill, flags, PrestigeBoosts.TOOL_EFF_PCT) +
                effectTotal(skill, flags, PrestigeBoosts.PER_LEVEL_BONUS) * level) / 100.0).toFloat()

    /** Flat thieving success-chance bonus (fraction, e.g. 0.10). */
    fun thievingSuccessBonus(flags: PlayerFlags): Double =
        effectTotal(Skills.THIEVING, flags, PrestigeBoosts.SUCCESS_CHANCE_PCT) / 100.0

    /** Extra arrow reclaim chance (ranged + fletching trees), as a fraction. */
    fun arrowReclaimBonus(flags: PlayerFlags): Double =
        (effectTotal(Skills.RANGED, flags, PrestigeBoosts.RECLAIM_PCT) +
         effectTotal(Skills.FLETCHING, flags, PrestigeBoosts.RECLAIM_PCT)) / 100.0

    /** Extra rune reclaim chance (magic + runecrafting trees), as a fraction. */
    fun runeReclaimBonus(flags: PlayerFlags): Double =
        (effectTotal(Skills.MAGIC, flags, PrestigeBoosts.RECLAIM_PCT) +
         effectTotal(Skills.RUNECRAFTING, flags, PrestigeBoosts.RECLAIM_PCT)) / 100.0

    /** Food heal values boosted by cooking + hitpoints heal nodes. */
    fun boostedFoodHeal(flags: PlayerFlags, healValues: Map<String, Int>): Map<String, Int> {
        val pct = effectTotal(Skills.COOKING, flags, PrestigeBoosts.HEAL_PCT) +
            effectTotal(Skills.HITPOINTS, flags, PrestigeBoosts.HEAL_PCT)
        if (pct <= 0.0) return healValues
        return healValues.mapValues { (_, v) -> (v * (1.0 + pct / 100.0)).toInt().coerceAtLeast(v) }
    }

    /** Fraction of XP/loot kept on combat death (base 0.10, defense + hitpoints nodes add). */
    fun deathKeepFraction(flags: PlayerFlags): Double =
        (0.10 + (effectTotal(Skills.DEFENSE, flags, PrestigeBoosts.DEATH_KEEP_PCT) +
            effectTotal(Skills.HITPOINTS, flags, PrestigeBoosts.DEATH_KEEP_PCT)) / 100.0)
            .coerceAtMost(0.60)

    /** Extra session queue slots from prestige (gnome construction capstone). */
    fun extraQueueSlots(flags: PlayerFlags): Int =
        effectTotal(Skills.CONSTRUCTION, flags, PrestigeBoosts.QUEUE_SLOT).toInt()

    /** Pet boost percent for [skill], strengthened by pet_boost_pct nodes. */
    fun boostedPetPct(skill: String, flags: PlayerFlags, basePct: Int): Int {
        if (basePct <= 0) return basePct
        val pct = effectTotal(skill, flags, PrestigeBoosts.PET_BOOST_PCT)
        if (pct <= 0.0) return basePct
        return (basePct * (1.0 + pct / 100.0)).toInt().coerceAtLeast(basePct)
    }

    /** Blessing duration multiplier (prayer Devotion + elf Forest Grace). */
    fun blessingDurationMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(Skills.PRAYER, flags, PrestigeBoosts.BLESSING_DURATION_PCT) / 100.0

    /** Blessing bone-cost multiplier (gnome Trickster's Favor), never below 1 bone. */
    fun blessingCostMultiplier(flags: PlayerFlags): Double =
        (1.0 - effectTotal(Skills.PRAYER, flags, PrestigeBoosts.BLESSING_COST_PCT) / 100.0)
            .coerceAtLeast(0.5)

    /** Flat bonus added to every stat of an active combat potion (herblore Potent Potions). */
    fun potionBonusFlat(flags: PlayerFlags): Int =
        effectTotal(Skills.HERBLORE, flags, PrestigeBoosts.POTION_BONUS_FLAT).toInt()

    /** Potion stat map with the herblore flat bonus applied (empty map stays empty). */
    fun boostedPotionEffects(flags: PlayerFlags, effects: Map<String, Int>): Map<String, Int> {
        val bonus = potionBonusFlat(flags)
        if (bonus <= 0 || effects.isEmpty()) return effects
        return effects.mapValues { (_, v) -> v + bonus }
    }

    /** Fraction of crafting inputs refunded for [skill] (smithing/crafting thrift + race branches). */
    fun inputSaveFraction(skill: String, flags: PlayerFlags): Double =
        (effectTotal(skill, flags, PrestigeBoosts.INPUT_SAVE_PCT) / 100.0).coerceAtMost(0.5)

    /** Extra builder discount in per-mille (construction Efficient Builder + dwarf Master Mason). */
    fun builderDiscountPerMille(flags: PlayerFlags): Int =
        (effectTotal(Skills.CONSTRUCTION, flags, PrestigeBoosts.BUILDER_DISCOUNT_PCT) * 10).toInt()

    /** Slayer task point multiplier (slayer Bounty Hunter + orc Headhunter). */
    fun slayerPointsMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(Skills.SLAYER, flags, PrestigeBoosts.SLAYER_POINTS_PCT) / 100.0

    /** Shop sell price multiplier (mercantile Trade Baron + dwarf Gold Sense). */
    fun sellPriceMultiplier(flags: PlayerFlags): Double =
        1.0 + effectTotal(Skills.MERCANTILE, flags, PrestigeBoosts.SELL_PRICE_PCT) / 100.0

    /** All active node effects per skill, for the profile Bonuses tab: skill -> effect -> total. */
    fun activeEffectsBySkill(flags: PlayerFlags): Map<String, Map<String, Double>> =
        trees.keys.mapNotNull { skill ->
            val nodes = PrestigeBoosts.activeNodes(trees, flags, skill)
            if (nodes.isEmpty()) return@mapNotNull null
            val byEffect = nodes.map { (_, n) -> n.effect }.distinct().associateWith { effect ->
                PrestigeBoosts.effectTotal(trees, flags, skill, effect)
            }
            skill to byEffect
        }.toMap()
}
