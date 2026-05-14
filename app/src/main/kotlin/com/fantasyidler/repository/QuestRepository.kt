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
                    val count = items[quest.target] ?: continue
                    if (count > 0) addProgress(questId, quest.amount, count, quest.requiresPrevious)
                }
                "craft_any" -> {
                    if (totalCrafted > 0) addProgress(questId, quest.amount, totalCrafted, quest.requiresPrevious)
                }
            }
        }
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
                    val count = killsByEnemy[quest.target] ?: continue
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

    private suspend fun addProgress(questId: String, requiredAmount: Int, delta: Int, requiresPrevious: String?) {
        if (!isPrerequisiteDone(requiresPrevious)) return
        val current = questProgressDao.getQuestProgress(questId) ?: QuestProgress(questId)
        if (current.completed) return
        questProgressDao.upsert(current.copy(progress = current.progress + delta))
    }

    private suspend fun isPrerequisiteDone(prerequisiteId: String?): Boolean {
        if (prerequisiteId == null) return true
        return questProgressDao.getQuestProgress(prerequisiteId)?.completed == true
    }
}
