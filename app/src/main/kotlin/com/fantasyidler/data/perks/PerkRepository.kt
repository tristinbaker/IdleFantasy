package com.fantasyidler.data.perks

import com.fantasyidler.data.model.PerkState
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.PlayerRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/write surface for the perk system. Earned points are derived from skill
 * levels on every call (never persisted) — spent points and purchased tiers
 * live in [PlayerFlags.perks].
 */
@Singleton
class PerkRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
) {
    /** Snapshot of the player's points and purchased perks. */
    data class Snapshot(
        val earnedAp: Int,
        val earnedGathering: Int,
        val earnedCrafting: Int,
        val earnedCombat: Int,
        val state: PerkState,
    ) {
        val availableAp: Int        get() = (earnedAp        - state.spentAp).toInt().coerceAtLeast(0)
        val availableGathering: Int get() = (earnedGathering - state.spentGathering).toInt().coerceAtLeast(0)
        val availableCrafting: Int  get() = (earnedCrafting  - state.spentCrafting).toInt().coerceAtLeast(0)
        val availableCombat: Int    get() = (earnedCombat    - state.spentCombat).toInt().coerceAtLeast(0)

        fun availableFor(category: PerkCategory): Int = when (category) {
            PerkCategory.ADVANTAGE -> availableAp
            PerkCategory.GATHERING -> availableGathering
            PerkCategory.CRAFTING  -> availableCrafting
            PerkCategory.COMBAT    -> availableCombat
        }

        fun ownedTier(perkId: String): Int = state.purchased[perkId] ?: 0
    }

    suspend fun snapshot(): Snapshot {
        val flags  = playerRepo.getFlags()
        val levels = playerRepo.getSkillLevels()
        return buildSnapshot(levels, flags.perks)
    }

    /** Pure computation — also reused by the UI ViewModel for live recomposition. */
    fun buildSnapshot(skillLevels: Map<String, Int>, state: PerkState): Snapshot {
        val earnedAp        = skillLevels.values.sum()
        val earnedGathering = Skills.GATHERING.sumOf { skillLevels[it] ?: 1 }
        val earnedCrafting  = Skills.CRAFTING_SKILLS.sumOf { skillLevels[it] ?: 1 }
        val earnedCombat    = Skills.COMBAT.sumOf { skillLevels[it] ?: 1 }
        return Snapshot(earnedAp, earnedGathering, earnedCrafting, earnedCombat, state)
    }

    /**
     * Purchase the next tier of [perkId]. Returns true on success; false if the
     * perk is already maxed or the player can't afford the next tier.
     */
    suspend fun purchase(perkId: String): Boolean {
        val perk = PerkCatalog.BY_ID[perkId] ?: return false
        val snap = snapshot()
        val currentTier = snap.ownedTier(perkId)
        if (currentTier >= perk.tiers.size) return false
        val nextTier = perk.tiers[currentTier]
        val available = snap.availableFor(perk.category)
        if (available < nextTier.costPoints) return false

        val flags = playerRepo.getFlags()
        val state = flags.perks
        val updatedState = when (perk.category) {
            PerkCategory.ADVANTAGE -> state.copy(
                spentAp   = state.spentAp + nextTier.costPoints,
                purchased = state.purchased + (perkId to currentTier + 1),
            )
            PerkCategory.GATHERING -> state.copy(
                spentGathering = state.spentGathering + nextTier.costPoints,
                purchased      = state.purchased + (perkId to currentTier + 1),
            )
            PerkCategory.CRAFTING  -> state.copy(
                spentCrafting = state.spentCrafting + nextTier.costPoints,
                purchased     = state.purchased + (perkId to currentTier + 1),
            )
            PerkCategory.COMBAT    -> state.copy(
                spentCombat = state.spentCombat + nextTier.costPoints,
                purchased   = state.purchased + (perkId to currentTier + 1),
            )
        }
        playerRepo.updateFlags(flags.copy(perks = updatedState))
        return true
    }

    /**
     * Look up the current effect magnitude for a perk. Returns 0.0 if the perk
     * isn't owned. Use this from the engine so the simulators don't need to
     * know about the perk catalog.
     */
    suspend fun effectMagnitude(perkId: String): Double {
        val flags = playerRepo.getFlags()
        val tier = flags.perks.purchased[perkId] ?: return 0.0
        val perk = PerkCatalog.BY_ID[perkId] ?: return 0.0
        if (tier <= 0) return 0.0
        return perk.tiers[(tier - 1).coerceAtMost(perk.tiers.size - 1)].effectMagnitude
    }

    /**
     * Resolve the time-cut fraction (0.0..0.5) for whichever category a given
     * skill belongs to. Stacks on top of the existing agility passive — the
     * returned value should multiply the post-agility base duration by
     * `(1.0 - cut)`.
     */
    suspend fun timeCutForSkill(skillName: String): Double {
        val perkId = when (skillName) {
            com.fantasyidler.data.model.Skills.MINING,
            com.fantasyidler.data.model.Skills.FISHING,
            com.fantasyidler.data.model.Skills.WOODCUTTING,
            com.fantasyidler.data.model.Skills.FARMING,
            com.fantasyidler.data.model.Skills.FIREMAKING,
            com.fantasyidler.data.model.Skills.AGILITY,
            com.fantasyidler.data.model.Skills.RUNECRAFTING -> PerkCatalog.GATHERING_TIME_CUT

            com.fantasyidler.data.model.Skills.SMITHING,
            com.fantasyidler.data.model.Skills.COOKING,
            com.fantasyidler.data.model.Skills.FLETCHING,
            com.fantasyidler.data.model.Skills.CRAFTING,
            com.fantasyidler.data.model.Skills.HERBLORE,
            com.fantasyidler.data.model.Skills.PRAYER -> PerkCatalog.CRAFTING_TIME_CUT

            "combat", "boss",
            com.fantasyidler.data.model.Skills.ATTACK,
            com.fantasyidler.data.model.Skills.STRENGTH,
            com.fantasyidler.data.model.Skills.DEFENSE,
            com.fantasyidler.data.model.Skills.RANGED,
            com.fantasyidler.data.model.Skills.MAGIC,
            com.fantasyidler.data.model.Skills.HITPOINTS -> PerkCatalog.COMBAT_TIME_CUT

            else -> return 0.0
        }
        return effectMagnitude(perkId).coerceIn(0.0, 0.5)
    }
}
