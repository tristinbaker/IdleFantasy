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
                    if (count > 0) addProgress(questId, quest.amount, count)
                }
                "gather_any" -> {
                    if (totalGathered > 0) addProgress(questId, quest.amount, totalGathered)
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
                    if (count > 0) addProgress(questId, quest.amount, count)
                }
                "craft_any" -> {
                    if (totalCrafted > 0) addProgress(questId, quest.amount, totalCrafted)
                }
            }
        }
    }

    /**
     * Called when a combat session is collected.
     *
     * [dungeonKey] = e.g. "goblin_cave"
     * [killsByEnemy] = map of enemyKey -> count killed
     * [loot] = all items received
     */
    suspend fun recordCombat(
        dungeonKey: String,
        killsByEnemy: Map<String, Int>,
        loot: Map<String, Int>,
    ) {
        val totalKills = killsByEnemy.values.sum()

        for ((questId, quest) in gameData.quests) {
            when (quest.type) {
                "kill" -> {
                    if (totalKills > 0) addProgress(questId, quest.amount, totalKills)
                }
                "kill_enemy" -> {
                    val count = killsByEnemy[quest.target] ?: continue
                    if (count > 0) addProgress(questId, quest.amount, count)
                }
                "dungeon" -> {
                    if (quest.target == dungeonKey) addProgress(questId, quest.amount, 1)
                }
                // collect, dungeon_melee_only, dungeon_ranged_only, dungeon_magic_only,
                // dungeon_no_food — not yet tracked; progress stays at 0
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
                addProgress(questId, quest.amount, amount)
            }
        }
    }

    /**
     * Marks a quest as reward-claimed. Returns the [QuestRewards] if it was claimable;
     * null otherwise.
     *
     * Claimable = progress >= quest.amount && !completed
     */
    suspend fun claimReward(questId: String): QuestRewards? {
        val quest = gameData.quests[questId] ?: return null
        val current = questProgressDao.getQuestProgress(questId) ?: return null

        if (current.completed) return null
        if (current.progress < quest.amount) return null

        questProgressDao.upsert(
            current.copy(
                completed   = true,
                completedAt = System.currentTimeMillis(),
            )
        )
        return quest.rewards
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Adds [delta] to the stored progress for [questId], creating a row if absent.
     * Skips already-completed quests. Does not cap progress at [requiredAmount].
     */
    private suspend fun addProgress(questId: String, requiredAmount: Int, delta: Int) {
        val current = questProgressDao.getQuestProgress(questId)
            ?: QuestProgress(questId)

        if (current.completed) return

        questProgressDao.upsert(current.copy(progress = current.progress + delta))
    }
}
