package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression test for #1960 (Elder Isle food over-consumption).
 *
 * Report mirror: 836 Manta Ray equipped, N queued Beach and Cliffs sessions overnight.
 * Backlog = 5 completed-but-uncollected combat sessions x 170 manta (850 reserved,
 * i.e. the whole supply is spoken for) while the ACTIVE session is an unrelated
 * completed mining session — so the old active-only reservation sees nothing.
 *
 * Expected: the next queued combat session must simulate with NO phantom supply
 * (frame-0 foodAtStart for manta_ray must be absent/zero). Before the fix it
 * simulates against the full 836.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BacklogFoodReservationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var starter: QueuedSessionStarter

    private val json = Json { ignoreUnknownKeys = true }
    private val framesSerializer = ListSerializer(SessionFrame.serializer())

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val gameData = GameDataRepository(context, json)
        val dailyQuestRepo = DailyQuestRepository(gameData)
        val weeklyQuestRepo = WeeklyQuestRepository(gameData)
        val boostRepo = BoostRepository(gameData)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            dailyQuestRepo,
            weeklyQuestRepo,
            BuffNotificationScheduler(context),
            gameData,
            boostRepo,
            db,
        )
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo)
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        starter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        )
        runBlocking {
            playerRepo.getOrCreatePlayer()
            playerRepo.addItems(mapOf("manta_ray" to 836))
            playerRepo.updateFlags(playerRepo.getFlags().copy(equippedFood = mapOf("manta_ray" to 1)))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun backlogSession(id: String, foodEaten: Int, startedAt: Long) = SkillSession(
        sessionId = id,
        skillName = "combat",
        startedAt = startedAt,
        endsAt = startedAt + 3_600_000L,
        frames = json.encodeToString(
            framesSerializer,
            listOf(
                SessionFrame(
                    minute = 0, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                    levelBefore = 0, levelAfter = 0,
                    foodConsumed = mapOf("manta_ray" to foodEaten),
                ),
            ),
        ),
        completed = true,
        activityKey = "beach_and_cliffs",
    )

    @Test
    fun `queued combat sees no food once the uncollected backlog spent the supply`() = runBlocking {
        // 5 x 170 = 850 >= 836: every manta is already spoken for.
        var t = 1_000L
        for (i in 1..5) {
            db.skillSessionDao().insert(backlogSession("backlog-$i", foodEaten = 170, startedAt = t))
            t += 3_600_000L
        }
        // Active session is an unrelated completed mining session (old code only
        // looks here -> sees no food pending -> hands over the full 836).
        db.skillSessionDao().insert(
            SkillSession(
                sessionId = "active-mining",
                skillName = "mining",
                startedAt = t,
                endsAt = t + 3_600_000L,
                frames = "[]",
                completed = true,
                activityKey = "copper_ore",
            ),
        )

        val enqueued = playerRepo.enqueueAction(
            QueuedAction(
                skillName = "combat",
                activityKey = "beach_and_cliffs",
                skillDisplayName = "Beach and Cliffs",
            ),
        )
        assertTrue("enqueue failed", enqueued)
        val started = try {
            starter.startNextQueued()
        } catch (e: Exception) {
            throw AssertionError("startNextQueued threw ${e::class.simpleName}: ${e.message}", e)
        }
        assertTrue("queued combat session did not start", started)

        val frames: List<SessionFrame> = json.decodeFromString(
            framesSerializer,
            sessionRepo.getActiveSession()!!.frames,
        )
        val offered = frames.firstOrNull()?.foodAtStart?.get("manta_ray") ?: 0
        assertTrue(
            "backlog already spent all 836 manta but session was offered $offered (report: ~1700 used from 836)",
            offered <= 0,
        )
    }

    @Test
    fun `queued combat sees only the remainder when the backlog is partial`() = runBlocking {
        // 2 x 170 = 340 pending -> 836 - 340 = 496 left to offer, no more.
        var t = 1_000L
        for (i in 1..2) {
            db.skillSessionDao().insert(backlogSession("backlog-$i", foodEaten = 170, startedAt = t))
            t += 3_600_000L
        }
        db.skillSessionDao().insert(
            SkillSession(
                sessionId = "active-mining",
                skillName = "mining",
                startedAt = t,
                endsAt = t + 3_600_000L,
                frames = "[]",
                completed = true,
                activityKey = "copper_ore",
            ),
        )

        playerRepo.enqueueAction(
            QueuedAction(
                skillName = "combat",
                activityKey = "beach_and_cliffs",
                skillDisplayName = "Beach and Cliffs",
            ),
        )
        val started = starter.startNextQueued()
        assertTrue("queued combat session did not start", started)

        val frames: List<SessionFrame> = json.decodeFromString(
            framesSerializer,
            sessionRepo.getActiveSession()!!.frames,
        )
        val offered = frames.firstOrNull()?.foodAtStart?.get("manta_ray") ?: 0
        assertTrue(
            "340 pending from 836 leaves 496, but session was offered $offered",
            offered <= 496,
        )
    }

    @Test
    fun `ten sequential queued sessions never simulate more than owned total`() = runBlocking {
        // Closed loop, no seeded backlog: every session's REAL simulated consumption
        // feeds the next session's reservation — the exact overnight-queue shape from
        // the report (836 owned). Each offer must fit the remaining budget.
        var cumulative = 0
        repeat(10) { i ->
            val enqueued = playerRepo.enqueueAction(
                QueuedAction(
                    skillName = "combat",
                    activityKey = "beach_and_cliffs",
                    skillDisplayName = "Beach and Cliffs",
                ),
            )
            assertTrue("iteration ${i + 1}: enqueue failed", enqueued)
            assertTrue("iteration ${i + 1}: session did not start", starter.startNextQueued())
            val session = sessionRepo.getActiveSession()!!
            val frames: List<SessionFrame> = json.decodeFromString(framesSerializer, session.frames)
            val remainingBefore = 836 - cumulative
            val offered = frames.firstOrNull()?.foodAtStart?.get("manta_ray") ?: 0
            assertTrue(
                "session ${i + 1}: offered $offered but only $remainingBefore of 836 remained",
                offered <= remainingBefore,
            )
            cumulative += frames.sumOf { f -> f.foodConsumed["manta_ray"] ?: 0 }
            sessionRepo.markCompleted(session.sessionId)
        }
        assertTrue(
            "cumulative simulated $cumulative exceeds 836 owned (report: ~1700)",
            cumulative <= 836,
        )
    }
}
