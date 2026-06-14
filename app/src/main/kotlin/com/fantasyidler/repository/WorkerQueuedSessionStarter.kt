package com.fantasyidler.repository

import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

private val GATHERING_SKILLS = setOf(
    Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
    Skills.AGILITY, "combat", "boss",
)

/**
 * Starts the next queued action for the hired worker.
 * Mirrors [QueuedSessionStarter] but reads from [PlayerFlags.hiredWorker.sessionQueue]
 * and calls [SessionRepository.startWorkerSession].
 */
@Singleton
class WorkerQueuedSessionStarter @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun startNextQueued(slot: Int = 1): Boolean {
        mutex.withLock {
            val current = sessionRepo.getActiveWorkerSession(slot)
            if (current != null && !current.completed) return false
            val next = playerRepo.dequeueNextWorkerAction(slot) ?: return false
            return try {
                startQueuedAction(slot, next)
                true
            } catch (_: Exception) {
                playerRepo.requeueWorkerActionAtFront(slot, next)
                false
            }
        }
        return false
    }

    private suspend fun startQueuedAction(slot: Int, action: QueuedAction) {
        val player    = playerRepo.getOrCreatePlayer()
        val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
        val xpMap:    Map<String, Long>    = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val worker = (if (slot == 2) flags.hiredWorker2 else flags.hiredWorker) ?: return
        val tier = worker.tier
        val agilityLevel = levels[Skills.AGILITY] ?: 1

        val isGathering = action.skillName in GATHERING_SKILLS
        val efficiencyMultiplier = if (isGathering) tier.combinedGatheringMultiplier
                                   else tier.efficiencyMultiplier
        val durationMs = if (isGathering) tier.durationMs
                         else action.estimatedDurationMs.takeIf { it > 0 } ?: tier.durationMs

        when (action.skillName) {
            Skills.MINING -> {
                val oreKey  = action.activityKey
                val oreData = gameData.ores[oreKey] ?: return
                val result  = SkillSimulator.simulateMining(
                    oreKey         = oreKey,
                    oreData        = oreData,
                    gems           = gameData.gems,
                    startXp        = xpMap[Skills.MINING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    toolEfficiency = toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier)
            }
            Skills.WOODCUTTING -> {
                val treeKey  = action.activityKey
                val treeData = gameData.trees[treeKey] ?: return
                val result   = SkillSimulator.simulateWoodcutting(
                    treeData       = treeData,
                    startXp        = xpMap[Skills.WOODCUTTING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    toolEfficiency = toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier)
            }
            Skills.FISHING -> {
                val fishKey  = action.activityKey
                val fishData = gameData.fish[fishKey] ?: return
                val result   = SkillSimulator.simulateFishing(
                    fishKey        = fishKey,
                    fishData       = fishData,
                    startXp        = xpMap[Skills.FISHING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    rodEfficiency  = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier)
            }
            Skills.AGILITY -> {
                val courseKey  = action.activityKey
                val courseData = gameData.agilityCourses[courseKey] ?: return
                val result     = SkillSimulator.simulateAgility(
                    courseData   = courseData,
                    startXp      = xpMap[Skills.AGILITY] ?: 0L,
                    agilityLevel = agilityLevel,
                    petBoostPct  = 0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier)
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val logData = gameData.logs[logKey] ?: return
                val ashKey  = ashForLog(logKey)
                val frames  = buildCraftFrames(xpMap[Skills.FIREMAKING] ?: 0L, qty, logData.xpPerLog.toDouble(), 1, ashKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.RUNECRAFTING -> {
                val runeKey  = action.activityKey
                val runeData = gameData.runes[runeKey] ?: return
                val qty      = action.qty.takeIf { it > 0 } ?: return
                val startXp  = xpMap[Skills.RUNECRAFTING] ?: 0L
                val level    = XpTable.levelForXp(startXp)
                val mult     = when { level >= 75 -> 3; level >= 50 -> 2; else -> 1 }
                val totalRunes   = mult * qty
                val totalXpGain  = (runeData.xpPerRune * totalRunes).toInt()
                val xpAfter      = startXp + totalXpGain
                val frames = listOf(SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = level,
                    levelAfter  = XpTable.levelForXp(xpAfter),
                    items       = mapOf(runeKey to totalRunes),
                    leveledUp   = XpTable.levelForXp(xpAfter) > level,
                    kills       = qty,
                ))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.PRAYER -> {
                val boneKey     = action.activityKey
                val bone        = gameData.bones[boneKey] ?: return
                val qty         = action.qty.takeIf { it > 0 } ?: return
                val startXp     = xpMap[Skills.PRAYER] ?: 0L
                val totalXpGain = bone.xpPerBone.toInt() * qty
                val xpAfter     = startXp + totalXpGain
                val frames = listOf(SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = XpTable.levelForXp(startXp),
                    levelAfter  = XpTable.levelForXp(xpAfter),
                    leveledUp   = XpTable.levelForXp(xpAfter) > XpTable.levelForXp(startXp),
                    kills       = qty,
                ))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            Skills.HERBLORE -> {
                val r   = gameData.herbloreRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.HERBLORE] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier)
            }
            "boss" -> {
                val bossKey = action.activityKey
                val boss    = gameData.bosses[bossKey] ?: return
                val bossWeapon = (flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON).let { equipped[it] }.let { gameData.equipment[it] }
                val combatStyle = when (bossWeapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "melee"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> bossWeapon?.rangedAttackBonus ?: bossWeapon?.attackBonus ?: 0; "magic" -> bossWeapon?.magicAttackBonus ?: 0; else -> bossWeapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (bossWeapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (bossWeapon?.magicDamageBonus ?: 0) else 0
                val equippedFoodKeys = flags.equippedFood.keys
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                val spell = gameData.spells[flags.activeSpell]
                val preferredArrow = flags.equippedArrows?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()
                val bossFrames = CombatSimulator.simulateBoss(
                    boss               = boss,
                    bossKey            = bossKey,
                    playerAttack       = levels[Skills.ATTACK]    ?: 1,
                    playerStrength     = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence      = (levels[Skills.DEFENSE]  ?: 1) + totalDefBonus,
                    playerHp           = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus  = totalAtkBonus,
                    weaponStrBonus     = totalStrBonus,
                    combatStyle        = combatStyle,
                    playerRanged       = levels[Skills.RANGED] ?: 1,
                    playerMagic        = levels[Skills.MAGIC]  ?: 1,
                    arrowStrengthBonus = arrowBonus,
                    spellMaxHit        = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows    = availableArrows,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = ChurchRepository.defBonus(flags),
                )
                startSession(slot, action, bossFrames, durationMs, efficiencyMultiplier)
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                val activeWeaponSlot = flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey  = equipped[activeWeaponSlot]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val equippedFoodKeys = flags.equippedFood.keys
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                val spell = gameData.spells[flags.activeSpell]
                val preferredArrow = flags.equippedArrows?.takeIf { (inventory[it] ?: 0) > 0 }
                val bestArrow = preferredArrow ?: ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val availableArrows = if (bestArrow != null) mapOf(bestArrow to (inventory[bestArrow] ?: 0)) else emptyMap()
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = levels[Skills.ATTACK]    ?: 1,
                    playerStrength      = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence       = (levels[Skills.DEFENSE]  ?: 1) + totalDefBonus,
                    playerHp            = levels[Skills.HITPOINTS] ?: 1,
                    blessingDefBonus    = ChurchRepository.defBonus(flags),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = levels[Skills.RANGED]    ?: 1,
                    playerMagic         = levels[Skills.MAGIC]     ?: 1,
                    arrowStrengthBonus  = arrowBonus,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    petBoostPct         = 0,
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                    availableArrows     = availableArrows,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier)
            }
        }
    }

    private suspend fun startSession(
        slot: Int,
        action: QueuedAction,
        frames: List<SessionFrame>,
        durationMs: Long,
        efficiencyMultiplier: Float,
    ) {
        sessionRepo.startWorkerSession(
            workerSlot           = slot,
            skillName            = action.skillName,
            activityKey          = action.activityKey,
            frames               = encodeFrames(frames),
            durationMs           = durationMs,
            skillDisplayName     = action.skillDisplayName,
            efficiencyMultiplier = efficiencyMultiplier,
        )
    }

    private fun encodeFrames(frames: List<SessionFrame>): String =
        json.encodeToString(json.serializersModule.serializer<List<SessionFrame>>(), frames)

    private fun ashForLog(logKey: String): String = when (logKey) {
        "oak_log"     -> "oak_ashes"
        "willow_log"  -> "willow_ashes"
        "maple_log"   -> "maple_ashes"
        "yew_log"     -> "yew_ashes"
        "magic_log"   -> "magic_ashes"
        "redwood_log" -> "redwood_ashes"
        else          -> "ashes"
    }

    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

    private fun tierIndex(level: Int): Int = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    private fun toolEfficiency(itemKey: String?, slot: String, resourceLevelRequired: Int = 0): Float {
        if (itemKey == null) return 1.0f
        val eq   = gameData.equipment[itemKey] ?: return 1.0f
        val base = when (slot) {
            EquipSlot.PICKAXE     -> eq.miningEfficiency      ?: 1.0f
            EquipSlot.AXE         -> eq.woodcuttingEfficiency ?: 1.0f
            EquipSlot.FISHING_ROD -> eq.fishingEfficiency     ?: 1.0f
            else                  -> 1.0f
        }
        if (resourceLevelRequired <= 0) return base
        val skillKey = when (slot) {
            EquipSlot.PICKAXE     -> Skills.MINING
            EquipSlot.AXE         -> Skills.WOODCUTTING
            EquipSlot.FISHING_ROD -> Skills.FISHING
            else                  -> return base
        }
        val toolReqLevel = eq.requirements[skillKey] ?: 1
        val tierDiff     = tierIndex(toolReqLevel) - tierIndex(resourceLevelRequired)
        return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
    }

    private fun buildCraftFrames(startXp: Long, qty: Int, xpPerItem: Double, outputQty: Int, outputKey: String): List<SessionFrame> {
        val totalXpGain = (xpPerItem * qty).toInt()
        val xpAfter     = startXp + totalXpGain
        return listOf(SessionFrame(
            minute      = 1,
            xpGain      = totalXpGain,
            xpBefore    = startXp,
            xpAfter     = xpAfter,
            levelBefore = XpTable.levelForXp(startXp),
            levelAfter  = XpTable.levelForXp(xpAfter),
            items       = mapOf(outputKey to outputQty * qty),
            leveledUp   = XpTable.levelForXp(xpAfter) > XpTable.levelForXp(startXp),
            kills       = qty,
        ))
    }

    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 0,
        "iron_arrow"       to 2,
        "steel_arrow"      to 4,
        "mithril_arrow"    to 6,
        "adamantite_arrow" to 8,
        "runite_arrow"     to 10,
    )
}
