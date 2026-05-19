package com.fantasyidler.data.perks

/**
 * Perk taxonomy. Advantage = the generic top-level pool, fed by total level.
 * The three skill categories are fed by the sum of levels in that category
 * and only buy perks tagged for that category.
 */
enum class PerkCategory { ADVANTAGE, GATHERING, CRAFTING, COMBAT }

/**
 * One tier of one perk. [costPoints] is paid out of the perk's category pool;
 * [effectMagnitude] is interpreted per-perk (a fraction for multiplier perks,
 * an absolute integer for slot perks, 1.0 = "active" for unlock perks).
 */
data class PerkTier(val costPoints: Int, val effectMagnitude: Double)

data class PerkDef(
    val id: String,
    val category: PerkCategory,
    val displayName: String,
    val description: String,
    /** Index = tier−1. tiers[0] is the first level you can buy. */
    val tiers: List<PerkTier>,
)

/**
 * Static catalog. Tier-2 costs ~2× tier-1, tier-3 ~4× — heavier perks cost
 * more so the player has to trade off depth-vs-breadth.
 */
object PerkCatalog {

    // ---- Effect ids consumed by the engine (string-literal keyed lookups
    //      so the simulators don't drag the PerkCatalog into their compile
    //      unit). Use these constants when reading PerkRepository.effectMagnitude
    //      from gameplay code. ---------------------------------------------

    const val GATHERING_TIME_CUT = "gathering_time_cut"
    const val GATHERING_XP_BOOST = "gathering_xp_boost"
    const val GATHERING_YIELD    = "gathering_yield"

    const val CRAFTING_TIME_CUT  = "crafting_time_cut"
    const val CRAFTING_XP_BOOST  = "crafting_xp_boost"

    const val COMBAT_TIME_CUT    = "combat_time_cut"
    const val COMBAT_XP_BOOST    = "combat_xp_boost"
    const val COMBAT_LOOT_BOOST  = "combat_loot_boost"

    const val EXTRA_QUEUE_SLOT   = "extra_session_queue_slot"

    val ALL: List<PerkDef> = listOf(
        // ---- Gathering ----
        PerkDef(
            id          = GATHERING_TIME_CUT,
            category    = PerkCategory.GATHERING,
            displayName = "Brisk Pace",
            description = "Reduce time on every gathering session.",
            tiers       = listOf(
                PerkTier(costPoints = 20,  effectMagnitude = 0.05),
                PerkTier(costPoints = 40,  effectMagnitude = 0.08),
                PerkTier(costPoints = 80,  effectMagnitude = 0.12),
            ),
        ),
        PerkDef(
            id          = GATHERING_XP_BOOST,
            category    = PerkCategory.GATHERING,
            displayName = "Veteran Hands",
            description = "Permanent XP bonus on gathering skills.",
            tiers       = listOf(
                PerkTier(costPoints = 15, effectMagnitude = 0.03),
                PerkTier(costPoints = 30, effectMagnitude = 0.06),
                PerkTier(costPoints = 60, effectMagnitude = 0.10),
            ),
        ),
        PerkDef(
            id          = GATHERING_YIELD,
            category    = PerkCategory.GATHERING,
            displayName = "Heavy Hauler",
            description = "Permanent yield bonus on gathered drops.",
            tiers       = listOf(
                PerkTier(costPoints = 25, effectMagnitude = 0.03),
                PerkTier(costPoints = 50, effectMagnitude = 0.06),
                PerkTier(costPoints = 100, effectMagnitude = 0.10),
            ),
        ),

        // ---- Crafting ----
        PerkDef(
            id          = CRAFTING_TIME_CUT,
            category    = PerkCategory.CRAFTING,
            displayName = "Steady Forge",
            description = "Reduce time on crafting jobs.",
            tiers       = listOf(
                PerkTier(costPoints = 20, effectMagnitude = 0.05),
                PerkTier(costPoints = 40, effectMagnitude = 0.08),
                PerkTier(costPoints = 80, effectMagnitude = 0.12),
            ),
        ),
        PerkDef(
            id          = CRAFTING_XP_BOOST,
            category    = PerkCategory.CRAFTING,
            displayName = "Master Smith",
            description = "Permanent XP bonus on crafting skills.",
            tiers       = listOf(
                PerkTier(costPoints = 15, effectMagnitude = 0.03),
                PerkTier(costPoints = 30, effectMagnitude = 0.06),
                PerkTier(costPoints = 60, effectMagnitude = 0.10),
            ),
        ),

        // ---- Combat ----
        PerkDef(
            id          = COMBAT_TIME_CUT,
            category    = PerkCategory.COMBAT,
            displayName = "Quick Step",
            description = "Combat sessions finish faster.",
            tiers       = listOf(
                PerkTier(costPoints = 20, effectMagnitude = 0.05),
                PerkTier(costPoints = 40, effectMagnitude = 0.08),
                PerkTier(costPoints = 80, effectMagnitude = 0.12),
            ),
        ),
        PerkDef(
            id          = COMBAT_XP_BOOST,
            category    = PerkCategory.COMBAT,
            displayName = "Battle Tested",
            description = "Permanent XP bonus on combat skills.",
            tiers       = listOf(
                PerkTier(costPoints = 15, effectMagnitude = 0.03),
                PerkTier(costPoints = 30, effectMagnitude = 0.06),
                PerkTier(costPoints = 60, effectMagnitude = 0.10),
            ),
        ),
        PerkDef(
            id          = COMBAT_LOOT_BOOST,
            category    = PerkCategory.COMBAT,
            displayName = "Looter's Eye",
            description = "Bonus loot from dungeons and bosses.",
            tiers       = listOf(
                PerkTier(costPoints = 25, effectMagnitude = 0.04),
                PerkTier(costPoints = 50, effectMagnitude = 0.08),
                PerkTier(costPoints = 100, effectMagnitude = 0.14),
            ),
        ),

        // ---- Advantage (cross-cutting) ----
        PerkDef(
            id          = EXTRA_QUEUE_SLOT,
            category    = PerkCategory.ADVANTAGE,
            displayName = "Forward Planner",
            description = "Queue one extra session.",
            tiers       = listOf(PerkTier(costPoints = 50, effectMagnitude = 1.0)),
        ),
    )

    val BY_ID: Map<String, PerkDef> = ALL.associateBy { it.id }

    fun byCategory(category: PerkCategory): List<PerkDef> =
        ALL.filter { it.category == category }
}
