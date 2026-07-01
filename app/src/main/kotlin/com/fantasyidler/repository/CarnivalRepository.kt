package com.fantasyidler.repository

import com.fantasyidler.data.json.CarnivalPrize
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarnivalRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {

    val prizes: Map<String, CarnivalPrize> by lazy {
        json.decodeFromString(gameData.loadAsset("data/carnival_prizes.json"))
    }

    suspend fun ticketBalance(): Int =
        playerRepo.getInventory()["carnival_ticket"] ?: 0

    /** Deduct [ticketCost] tickets and add [itemKey] to inventory. Returns false if insufficient tickets. */
    suspend fun redeemForItem(itemKey: String, ticketCost: Int): Boolean = playerRepo.withLock {
        val inventory = playerRepo.getInventoryUnlocked()
        if ((inventory["carnival_ticket"] ?: 0) < ticketCost) return@withLock false
        playerRepo.consumeItemsUnlocked(mapOf("carnival_ticket" to ticketCost))
        playerRepo.addItemUnlocked(itemKey, 1)
        true
    }

    /** Deduct [ticketCost] tickets and grant [xpAmount] XP in [skillKey]. Returns false if insufficient tickets. */
    suspend fun redeemForXp(skillKey: String, xpAmount: Long, ticketCost: Int): Boolean = playerRepo.withLock {
        val inventory = playerRepo.getInventoryUnlocked()
        if ((inventory["carnival_ticket"] ?: 0) < ticketCost) return@withLock false
        playerRepo.consumeItemsUnlocked(mapOf("carnival_ticket" to ticketCost))
        playerRepo.applyMultiSkillResultsUnlocked(
            mapOf(skillKey to xpAmount),
            emptyMap(),
            0L,
        )
        true
    }


    /** Award tickets directly (used by active minigames). */
    suspend fun awardTickets(count: Int) {
        if (count > 0) playerRepo.addItem("carnival_ticket", count)
    }
}
