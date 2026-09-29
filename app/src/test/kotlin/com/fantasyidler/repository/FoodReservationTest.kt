package com.fantasyidler.repository

import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED tests for #1960 (Elder Isle food over-consumption + disagreeing counts).
 *
 * Report mirror: 836 Manta Ray equipped, ~10 queued isle dungeon sessions each
 * simulating ~170 units against a near-full supply → ~1700 "used" from 836 owned,
 * while gear/combat/reward screens show 1 vs 230 vs 221.
 *
 * Prime suspect: reservation only subtracts the ACTIVE completed-but-uncollected
 * session (pendingFoodConsumed), so sessions 2..N simulate against full inventory.
 * These tests pin the contract: the whole uncollected backlog is reserved, and all
 * "food left" reads derive from ONE pending-aware source.
 */
class FoodReservationTest {

    private fun frameWithFood(food: Map<String, Int>, minute: Int = 0) = SessionFrame(
        minute = minute,
        xpGain = 0,
        xpBefore = 0L,
        xpAfter = 0L,
        levelBefore = 0,
        levelAfter = 0,
        foodConsumed = food,
    )

    private fun completedSession(
        id: String,
        skill: String,
        foodPerFrame: List<Map<String, Int>>,
        startedAt: Long,
    ) = SkillSession(
        sessionId = id,
        skillName = skill,
        startedAt = startedAt,
        endsAt = startedAt + 3_600_000L,
        frames = framesJson(foodPerFrame),
        completed = true,
        activityKey = "ancient_forest",
    )

    // -- available(): inventory minus backlog, restricted to equipped ---------

    @Test
    fun `available subtracts the whole backlog not just the active session`() {
        val inventory = mapOf("manta_ray" to 836)
        val equipped = setOf("manta_ray")
        // 4 older sessions x 170 already simulated-but-uncollected.
        val pending = mapOf("manta_ray" to 680)

        val available = FoodReservation.available(inventory, equipped, pending)

        assertEquals(mapOf("manta_ray" to 156), available)
    }

    @Test
    fun `available is empty once the backlog consumed the whole supply`() {
        val inventory = mapOf("manta_ray" to 836)
        val equipped = setOf("manta_ray")
        // 5 x 170 = 850 >= 836: sessions 6..N must see NO phantom supply.
        val pending = mapOf("manta_ray" to 850)

        val available = FoodReservation.available(inventory, equipped, pending)

        assertTrue("expected no food to simulate with, got $available", available.isEmpty())
    }

    @Test
    fun `available keeps the equipped-only convention and clamps per key`() {
        val inventory = mapOf("manta_ray" to 836, "shark" to 10)
        val equipped = setOf("manta_ray")
        val pending = mapOf("manta_ray" to 900)

        val available = FoodReservation.available(inventory, equipped, pending)

        assertTrue("expected empty, got $available", available.isEmpty())
    }

    // -- sumConsumed / aggregatePending ---------------------------------------

    @Test
    fun `sumConsumed totals foodConsumed across frames`() {
        val frames = listOf(
            frameWithFood(mapOf("manta_ray" to 100)),
            frameWithFood(mapOf("manta_ray" to 70, "shark" to 5), minute = 1),
            frameWithFood(emptyMap(), minute = 2),
        )

        assertEquals(mapOf("manta_ray" to 170, "shark" to 5), FoodReservation.sumConsumed(frames))
    }

    @Test
    fun `aggregatePending sums every completed combat session in the backlog`() {
        val sessions = listOf(
            completedSession("s1", "combat", listOf(mapOf("manta_ray" to 170)), startedAt = 1L),
            completedSession("s2", "combat", listOf(mapOf("manta_ray" to 170)), startedAt = 2L),
            completedSession("s3", "boss", listOf(mapOf("manta_ray" to 170)), startedAt = 3L),
            completedSession("s4", "mining", listOf(mapOf("manta_ray" to 999)), startedAt = 4L),
            completedSession("s5", "tower", listOf(mapOf("manta_ray" to 60)), startedAt = 5L),
        )

        val pending = FoodReservation.aggregatePending(sessions, ::framesList)

        // combat + boss + tower count; mining never consumes equipped food.
        assertEquals(mapOf("manta_ray" to 570), pending)
    }

    @Test
    fun `aggregatePending ignores sessions that are not completed yet`() {
        val sessions = listOf(
            completedSession("s1", "combat", listOf(mapOf("manta_ray" to 170)), startedAt = 1L),
            SkillSession(
                sessionId = "running",
                skillName = "combat",
                startedAt = 2L,
                endsAt = 3L,
                frames = framesJson(listOf(mapOf("manta_ray" to 500))),
                completed = false,
                activityKey = "ancient_forest",
            ),
        )

        val pending = FoodReservation.aggregatePending(sessions, ::framesList)

        assertEquals(mapOf("manta_ray" to 170), pending)
    }

    // -- gate predicate: any spendable equipped food left? ----------------------

    @Test
    fun `hasUsableFood true while backlog leaves remainder`() {
        assertTrue(
            FoodReservation.hasUsableFood(
                setOf("manta_ray"),
                mapOf("manta_ray" to 836),
                mapOf("manta_ray" to 340),
            ),
        )
    }

    @Test
    fun `hasUsableFood false once backlog exhausted the supply`() {
        // Gate trigger from review: owned 100, pending 100 -> warn, don't start blind.
        assertTrue(
            !FoodReservation.hasUsableFood(
                setOf("manta_ray"),
                mapOf("manta_ray" to 100),
                mapOf("manta_ray" to 100),
            ),
        )
    }

    @Test
    fun `hasUsableFood false when nothing equipped`() {
        assertTrue(
            !FoodReservation.hasUsableFood(
                emptySet(),
                mapOf("manta_ray" to 836),
                emptyMap(),
            ),
        )
    }

    // -- report scenario: cumulative simulated food can never exceed supply ---

    @Test
    fun `ten queued sessions against 836 cannot simulate more than 836 total`() {
        val inventory = mapOf("manta_ray" to 836)
        val equipped = setOf("manta_ray")
        var pending = emptyMap<String, Int>()
        var simulatedTotal = 0

        repeat(10) {
            val available = FoodReservation.available(inventory, equipped, pending)
            // Each session eats up to ~170 but never more than actually available.
            val simulated = minOf(170, available["manta_ray"] ?: 0)
            simulatedTotal += simulated
            pending = mergeAdd(pending, mapOf("manta_ray" to simulated))
        }

        assertTrue(
            "simulated $simulatedTotal from 836 owned (report: ~1700)",
            simulatedTotal <= 836,
        )
    }

    // -- helpers (test-only JSON round-trip) -----------------------------------

    private fun framesJson(foodPerFrame: List<Map<String, Int>>): String {
        val frames = foodPerFrame.mapIndexed { i, food -> frameWithFood(food, minute = i) }
        return testFramesJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SessionFrame.serializer()),
            frames,
        )
    }

    private fun framesList(raw: String): List<SessionFrame> =
        testFramesJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SessionFrame.serializer()),
            raw,
        )

    private fun mergeAdd(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> {
        val out = a.toMutableMap()
        b.forEach { (k, v) -> out[k] = (out[k] ?: 0) + v }
        return out
    }

    companion object {
        private val testFramesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
