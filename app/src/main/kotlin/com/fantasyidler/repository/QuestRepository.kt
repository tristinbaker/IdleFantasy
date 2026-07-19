package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.QuestProgressDao
import com.fantasyidler.data.json.QuestRewards
import com.fantasyidler.data.model.QuestProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestRepository @Inject constructor(
    private val questProgressDao: QuestProgressDao,
    private val gameData: GameDataRepository,
) {

    fun observeProgress(): Flow<List<QuestProgress>> =
        questProgressDao.observeAllProgress()

    /**
     * Called when a gathering session is collected (mining, woodcutting, fishing,
     * agility, firemaking, runecrafting).
     *
     * [skillName] = e.g. "mining"
     * [items] = all items collected in the session
     */
    suspend fun recordGathering(skillName: String, items: Map<String, Int>) {
        val totalGathered = items.values.sum()

        for ((questId, quest) in gameData.quests) {
            if (quest.skill != skillName) continue

            when (quest.type) {
                "gather" -> {
                    val count = items[quest.target] ?: continue
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
                "gather_any" -> {
                    if (totalGathered > 0) addProgress(questId, quest.amount, totalGathered, quest.requiresPrevious)
                }
            }
        }
    }

    /**
     * Called when a crafting session is collected (smithing, cooking, fletching, crafting).
     *
     * [skillName] = e.g. "smithing"
     * [items] = all crafted output items
     */
    suspend fun recordCrafting(skillName: String, items: Map<String, Int>) {
        val totalCrafted = items.values.sum()

        for ((questId, quest) in gameData.quests) {
            if (quest.skill != skillName) continue

            when (quest.type) {
                "craft" -> {
                    val count = (items[quest.target] ?: 0) + (items["enhanced_${quest.target}"] ?: 0)
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
                "craft_any" -> {
                    val count = if (quest.target == "any_fish") {
                        items.filterKeys { it in fishCookedItems }.values.sum()
                    } else {
                        totalCrafted
                    }
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
            }
        }
    }

    private val fishCookedItems: Set<String> by lazy {
        val fishRaw = gameData.fish.keys
        gameData.cookingRecipes.values
            .filter { it.rawItem in fishRaw }
            .map { it.cookedItem }
            .toSet()
    }

    /**
     * Called when a combat session is collected.
     *
     * [dungeonKey]  = e.g. "goblin_cave"
     * [killsByEnemy] = map of enemyKey -> count killed
     * [loot]        = all items received
     * [combatStyle] = "melee" | "ranged" | "magic" (derived from XP distribution)
     */
    suspend fun recordCombat(
        dungeonKey: String,
        killsByEnemy: Map<String, Int>,
        loot: Map<String, Int>,
        combatStyle: String = "",
        foodConsumedTotal: Int = 0,
    ) {
        val totalKills = killsByEnemy.values.sum()

        for ((questId, quest) in gameData.quests) {
            when (quest.type) {
                "kill" -> {
                    if (totalKills > 0) addProgress(questId, quest.amount, totalKills, quest.requiresPrevious)
                }
                "kill_enemy" -> {
                    val count = killsByEnemy.entries.sumOf { (enemyKey, kills) ->
                        if (enemyKey == quest.target) kills
                        else if (gameData.enemies[enemyKey]?.tags?.contains(quest.target) == true) kills
                        else if (gameData.bosses[enemyKey]?.tags?.contains(quest.target) == true) kills
                        else 0
                    }
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
                "dungeon" -> {
                    if (quest.target == dungeonKey) addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
                "dungeon_melee_only" -> {
                    if (quest.target == dungeonKey && combatStyle == "melee")
                        addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
                "dungeon_ranged_only" -> {
                    if (quest.target == dungeonKey && combatStyle == "ranged")
                        addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
                "dungeon_magic_only" -> {
                    if (quest.target == dungeonKey && combatStyle == "magic")
                        addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
                "dungeon_no_food" -> {
                    if (quest.target == dungeonKey && foodConsumedTotal == 0)
                        addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
                "collect" -> {
                    val count = loot[quest.target] ?: continue
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
                "boss" -> {
                    if (quest.target == dungeonKey) addProgress(questId, quest.amount, 1, quest.requiresPrevious)
                }
            }
        }
    }

    /** Called when a Slayer task is fully completed. Advances any active slayer_task quests by 1. */
    suspend fun recordSlayerTaskCompleted() {
        for ((questId, quest) in gameData.quests) {
            if (quest.type == "slayer_task") {
                addProgress(questId, quest.amount, 1, quest.requiresPrevious)
            }
        }
    }

    /**
     * Called when a thieving session is collected.
     *
     * [npcKey]       = the NPC pickpocketed (e.g. "peasant")
     * [successCount] = number of frames where success=true
     * [loot]         = items looted (coins already separated out)
     */
    suspend fun recordThieving(npcKey: String, successCount: Int, loot: Map<String, Int>) {
        if (successCount <= 0 && loot.isEmpty()) return

        for ((questId, quest) in gameData.quests) {
            when (quest.type) {
                "pickpocket" -> {
                    if (quest.target == npcKey && successCount > 0)
                        addProgress(questId, quest.amount, successCount, quest.requiresPrevious)
                }
                "steal" -> {
                    val count = loot[quest.target] ?: continue
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
            }
        }
    }

    /**
     * Called when a town building is successfully upgraded.
     *
     * [buildingKey] = e.g. "inn", "guild_hall", "church"
     */
    suspend fun recordBuildingUpgraded(buildingKey: String) {
        for ((questId, quest) in gameData.quests) {
            if (quest.type == "upgrade_building" && quest.target == buildingKey) {
                addProgress(questId, quest.amount, 1, quest.requiresPrevious)
            }
        }
    }

    /**
     * Called when bones are buried (prayer XP).
     *
     * [amount] = total bones buried this session
     */
    suspend fun recordBuried(amount: Int) {
        if (amount <= 0) return

        for ((questId, quest) in gameData.quests) {
            if (quest.type == "prayer") {
                addProgress(questId, quest.amount, amount, quest.requiresPrevious)
            }
        }
    }

    /**
     * Result of an attempted reward claim. [Success] carries the rewards;
     * [BlockedByPrerequisite] tells the caller which quest still needs to be claimed first
     * so the UI can name it in a snackbar; [NotReady] covers progress-too-low,
     * already-completed, and unknown-quest cases.
     */
    sealed class ClaimResult {
        data class Success(val rewards: QuestRewards) : ClaimResult()
        data class BlockedByPrerequisite(val prerequisiteId: String) : ClaimResult()
        object NotReady : ClaimResult()
    }

    suspend fun claimReward(questId: String): ClaimResult {
        val quest = gameData.quests[questId] ?: return ClaimResult.NotReady
        val current = questProgressDao.getQuestProgress(questId) ?: return ClaimResult.NotReady

        if (current.completed) return ClaimResult.NotReady
        if (current.progress < quest.amount) return ClaimResult.NotReady
        val prereq = quest.requiresPrevious
        if (prereq != null && !isPrerequisiteDone(prereq)) {
            return ClaimResult.BlockedByPrerequisite(prereq)
        }

        questProgressDao.upsert(
            current.copy(
                completed   = true,
                completedAt = System.currentTimeMillis(),
            )
        )
        return ClaimResult.Success(quest.rewards)
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /** Adds [delta] to the stored progress for [questId], creating a row if absent. Skips already-completed quests. */
    suspend fun resetAllProgress() = questProgressDao.deleteAll()

    /**
     * Debug helper: clears progress/completion for [questId] and every quest that
     * transitively lists it (or one of its dependents) as [QuestData.requiresPrevious].
     * Does not claw back rewards already granted.
     */
    suspend fun resetQuestAndDependents(questId: String) {
        // Return if quest is unknown
        if (questId !in gameData.quests) return
        // Map prerequisites -> dependents
        val dependentsByPrereq = mutableMapOf<String, MutableList<String>>()
        for ((id, quest) in gameData.quests) {
            val prereq = quest.requiresPrevious ?: continue
            dependentsByPrereq.getOrPut(prereq) { mutableListOf() }.add(id)
        }
        // Find all affected quests (questId and dependents)
        val toReset = linkedSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(questId) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!toReset.add(current)) continue
            dependentsByPrereq[current]?.forEach { queue.add(it) }
        }
        // Reset progress
        for (id in toReset) {
            questProgressDao.upsert(QuestProgress(questId = id))
        }
    }

    private suspend fun addProgress(questId: String, requiredAmount: Int, delta: Int, requiresPrevious: String?) {
        val current = questProgressDao.getQuestProgress(questId) ?: QuestProgress(questId)
        if (current.completed) return
        questProgressDao.upsert(current.copy(progress = current.progress + delta))
    }

    private suspend fun isPrerequisiteDone(prerequisiteId: String?): Boolean {
        if (prerequisiteId == null) return true
        return questProgressDao.getQuestProgress(prerequisiteId)?.completed == true
    }
}
