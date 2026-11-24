package com.fantasyidler.ui.viewmodel

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.BuffNotificationScheduler
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GlobalStateRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.MercenaryRepository
import com.fantasyidler.repository.MonumentRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SaveSlotRepository
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.repository.TitleRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.repository.StoryRepository
import javax.inject.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Issue #2013: dismissing a worker on a runecrafting/herblore task that uses ashes
 * returns the primary materials but NOT the ashes, contradicting the dismiss dialog
 * ("your hire fee and any queued materials will be returned").
 *
 * Both tests drive the REAL [HomeViewModel.dismissWorker]. The setup mirrors exactly
 * what production consumes/enqueues before the dismiss:
 * - runecrafting: WorkerSkillsViewModel.startRunecraftingSession consumes rune_essence
 *   plus (qty+9)/10 ashes at queue time and enqueues a QueuedAction carrying
 *   catalystKey but no catalystQty.
 * - herblore: WorkerSkillsViewModel.craft consumes the recipe materials at queue time
 *   and WorkerQueuedSessionStarter consumes catalystKey x qty when the session starts.
 *
 * dismissWorker refunds only playerSessionMaterials (primaries) and never the catalyst,
 * unlike the player paths (abandonSession/removeFromQueue) which refund
 * catalystKey/catalystQty. Both tests are RED until that refund exists.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkerDismissAshRefundTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var gameData: GameDataRepository
    private lateinit var queuedStarter: QueuedSessionStarter
    private lateinit var workerStarter: WorkerQueuedSessionStarter
    private lateinit var viewModel: HomeViewModel
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameData = GameDataRepository(context, json)
        val boostRepo = BoostRepository(gameData)
        val dailyRepo = DailyQuestRepository(gameData)
        val weeklyRepo = WeeklyQuestRepository(gameData)
        val buffScheduler = BuffNotificationScheduler(context)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            dailyRepo,
            weeklyRepo,
            buffScheduler,
            gameData,
            boostRepo,
            db,
        )
        sessionRepo = SessionRepository(
            db.skillSessionDao(),
            context,
            json,
            gameData,
            db.playerDao(),
            playerRepo,
            StoryRepository(playerRepo),
        )
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        val queuedStarter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        ).also { this.queuedStarter = it }
        val workerStarter = WorkerQueuedSessionStarter(
            boostRepo, playerRepo, sessionRepo, gameData, json,
        ).also { this.workerStarter = it }
        val guildRepo = GuildRepository(
            playerRepo, db.questProgressDao(), gameData, Provider { townRepo },
        )
        val slayerRepo = SlayerRepository(boostRepo, playerRepo, questRepo, gameData, guildRepo)
        val monumentRepo = MonumentRepository(playerRepo, buffScheduler)
        val seasonalRepo = SeasonalEventRepository(playerRepo, gameData, dailyRepo, context)
        val titleRepo = TitleRepository(playerRepo, gameData, db.questProgressDao(), guildRepo)
        val globalStateRepo = GlobalStateRepository(db.globalStateDao())
        val farmingRepo = FarmingRepository(
            context, db, db.farmingPatchDao(), playerRepo, gameData,
            seasonalRepo, globalStateRepo, json, boostRepo,
        )
        val backupScheduler = BackupScheduler(context, sessionRepo, globalStateRepo)
        val saveSlotRepo = SaveSlotRepository(
            context, playerRepo, sessionRepo, questRepo, farmingRepo, guildRepo,
            globalStateRepo, queuedStarter, workerStarter, backupScheduler, buffScheduler, json,
        )
        viewModel = HomeViewModel(
            boostRepo, context, playerRepo, sessionRepo, gameData, questRepo, guildRepo,
            townRepo, queuedStarter, workerStarter, slayerRepo, monumentRepo, seasonalRepo,
            titleRepo, saveSlotRepo, StoryRepository(playerRepo), json,
        )
        runBlocking { playerRepo.getOrCreatePlayer() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * viewModelScope runs on Robolectric's Main, and @Test methods also run on Main:
     * blocking it with runBlocking wedges the dispatcher (issue #2013 repro lesson).
     * So the test body runs on a background thread while THIS (main) thread pumps
     * the main looper until the body finishes. future.get rethrows assertion errors.
     */
    private fun driveFromBackground(timeoutSec: Long = 120, block: suspend () -> Unit) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { runBlocking { block() } }
            val main = Shadows.shadowOf(Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + timeoutSec * 1000L
            while (!future.isDone) {
                main.idle()
                if (System.currentTimeMillis() > deadline) {
                    future.cancel(true)
                    throw AssertionError("timed out driving the main looper")
                }
                Thread.sleep(25)
            }
            future.get()
        } finally {
            executor.shutdownNow()
        }
    }

    /** viewModelScope runs on Main (pumped by [driveFromBackground]): idle it until [condition] holds. */
    private suspend fun awaitMainCondition(what: String, condition: suspend () -> Boolean) {
        withTimeout(30_000L) {
            while (!condition()) delay(50)
        }
    }

    private suspend fun awaitWorkerDismissed(slot: Int) =
        awaitMainCondition("worker slot $slot cleared") {
            val flags = playerRepo.getFlags()
            (if (slot == 2) flags.hiredWorker2 else flags.hiredWorker) == null
        }

    @Test
    fun `dismiss returns ashes for queued runecrafting task`() = driveFromBackground {
        playerRepo.addItems(mapOf("rune_essence" to 100, "ashes" to 20))
        playerRepo.updateFlags(
            playerRepo.getFlags().copy(hiredWorker = HiredWorker(WorkerTier.APPRENTICE, "repro")),
        )

        // Exactly what WorkerSkillsViewModel.startRunecraftingSession consumes/enqueues.
        val qty = 20
        val ashCost = (qty + 9) / 10
        assertTrue(playerRepo.consumeItems(mapOf("rune_essence" to 20, "ashes" to ashCost)))
        assertTrue(
            playerRepo.enqueueWorkerAction(
                1,
                QueuedAction(
                    skillName = Skills.RUNECRAFTING,
                    activityKey = "air_rune",
                    skillDisplayName = "Runecrafting",
                    qty = qty,
                    catalystKey = "ashes",
                ),
            ),
        )

        viewModel.dismissWorker(1)
        awaitWorkerDismissed(1)

        val inv = playerRepo.getInventory()
        assertEquals("essence refunded", 100, inv["rune_essence"])
        assertEquals("ashes refunded (issue #2013)", 20, inv["ashes"])
    }

    @Test
    fun `dismiss returns ashes for active herblore session`() = driveFromBackground {
        playerRepo.addItems(mapOf("potato" to 10, "ashes" to 10))
        playerRepo.updateFlags(
            playerRepo.getFlags().copy(hiredWorker = HiredWorker(WorkerTier.APPRENTICE, "repro")),
        )

        // Exactly what the worker herblore path consumes: recipe materials at queue time
        // (WorkerSkillsViewModel.craft) plus catalystKey x qty when the session starts
        // (WorkerQueuedSessionStarter HERBLORE branch).
        val qty = 5
        assertTrue(playerRepo.consumeItems(mapOf("potato" to 2 * qty)))
        assertTrue(playerRepo.consumeItems(mapOf("ashes" to qty)))
        val frames = listOf(
            SessionFrame(
                minute = 1,
                xpGain = 50,
                xpBefore = 0L,
                xpAfter = 50L,
                levelBefore = 1,
                levelAfter = 1,
                kills = qty,
            ),
        )
        sessionRepo.startWorkerSession(
            workerSlot = 1,
            skillName = Skills.HERBLORE,
            activityKey = "attack_brew",
            frames = json.encodeToString(ListSerializer(SessionFrame.serializer()), frames),
            durationMs = 60_000L,
            skillDisplayName = "Herblore",
            efficiencyMultiplier = 1.0f,
            // Post-fix production shape: the starter records the ashes it consumed at start.
            catalystKey = "ashes",
            catalystQty = qty,
        )

        viewModel.dismissWorker(1)
        awaitWorkerDismissed(1)

        val inv = playerRepo.getInventory()
        assertEquals("herb refunded", 10, inv["potato"])
        assertEquals("ashes refunded (issue #2013)", 10, inv["ashes"])
    }

    @Test
    fun `dismiss returns ashes for active runecrafting session`() = driveFromBackground {
        playerRepo.addItems(mapOf("rune_essence" to 100, "ashes" to 20))
        playerRepo.updateFlags(
            playerRepo.getFlags().copy(hiredWorker = HiredWorker(WorkerTier.APPRENTICE, "repro")),
        )

        // Worker runecrafting pays essence + ashes up front at order time; the active
        // session below is what WorkerQueuedSessionStarter would have started.
        val qty = 20
        val ashCost = (qty + 9) / 10
        assertTrue(playerRepo.consumeItems(mapOf("rune_essence" to 20, "ashes" to ashCost)))
        val frames = listOf(
            SessionFrame(
                minute = 1,
                xpGain = 100,
                xpBefore = 0L,
                xpAfter = 100L,
                levelBefore = 1,
                levelAfter = 1,
                kills = qty,
            ),
        )
        sessionRepo.startWorkerSession(
            workerSlot = 1,
            skillName = Skills.RUNECRAFTING,
            activityKey = "air_rune",
            frames = json.encodeToString(ListSerializer(SessionFrame.serializer()), frames),
            durationMs = 60_000L,
            skillDisplayName = "Runecrafting",
            efficiencyMultiplier = 1.0f,
            // Post-fix production shape: the starter forwards the order-time ash cost.
            catalystKey = "ashes",
            catalystQty = ashCost,
        )

        viewModel.dismissWorker(1)
        awaitWorkerDismissed(1)

        val inv = playerRepo.getInventory()
        assertEquals("essence refunded", 100, inv["rune_essence"])
        assertEquals("ashes refunded (issue #2013)", 20, inv["ashes"])
    }

    @Test
    fun `dismiss of never-started herblore order refunds primaries and keeps ashes`() = driveFromBackground {
        // Guard: a queued herblore order never paid ashes (they are charged at session
        // start), so dismissing must return primaries and leave ashes untouched — the fix
        // must not over-refund here.
        playerRepo.addItems(mapOf("potato" to 10, "ashes" to 10))
        playerRepo.updateFlags(
            playerRepo.getFlags().copy(hiredWorker = HiredWorker(WorkerTier.APPRENTICE, "repro")),
        )

        // Exactly what WorkerSkillsViewModel.craft consumes/enqueues for herblore: recipe
        // materials now, ashes only later at session start (which never happens here).
        assertTrue(playerRepo.consumeItems(mapOf("potato" to 10)))
        assertTrue(
            playerRepo.enqueueWorkerAction(
                1,
                QueuedAction(
                    skillName = Skills.HERBLORE,
                    activityKey = "attack_brew",
                    skillDisplayName = "Herblore",
                    qty = 5,
                    catalystKey = "ashes",
                ),
            ),
        )

        viewModel.dismissWorker(1)
        awaitWorkerDismissed(1)

        val inv = playerRepo.getInventory()
        assertEquals("herb refunded", 10, inv["potato"])
        assertEquals("untouched ashes stay untouched", 10, inv["ashes"])
    }

    @Test
    fun `discarded worker order refunds prepaid essence and ashes`() = driveFromBackground {
        // Post-level-drop gate (#1605): chaos_rune needs 35, the worker account sits at 1,
        // so the starter discards the prepaid order instead of starting it.
        playerRepo.addItems(mapOf("rune_essence" to 100, "ashes" to 20))
        playerRepo.updateFlags(
            playerRepo.getFlags().copy(hiredWorker = HiredWorker(WorkerTier.APPRENTICE, "repro")),
        )

        val qty = 10
        val ashCost = (qty + 9) / 10
        assertTrue(playerRepo.consumeItems(mapOf("rune_essence" to qty, "ashes" to ashCost)))
        assertTrue(
            playerRepo.enqueueWorkerAction(
                1,
                QueuedAction(
                    skillName = Skills.RUNECRAFTING,
                    activityKey = "chaos_rune",
                    skillDisplayName = "Runecrafting",
                    qty = qty,
                    catalystKey = "ashes",
                ),
            ),
        )

        val started = workerStarter.startNextQueued(1)

        assertEquals("nothing qualifies, nothing starts", false, started)
        assertEquals("discarded order leaves the queue", 0, playerRepo.getFlags().hiredWorker?.sessionQueue?.size)
        val inv = playerRepo.getInventory()
        assertEquals("discarded essence refunded", 100, inv["rune_essence"])
        assertEquals("discarded ashes refunded", 20, inv["ashes"])
    }

    @Test
    fun `discarded player order refunds prepaid essence and ashes`() = driveFromBackground {
        // Same gate on the player starter: a prepaid player order that no longer qualifies
        // is dropped and must pay back consumedMaterials + catalystQty.
        playerRepo.addItems(mapOf("rune_essence" to 100, "ashes" to 20))

        val qty = 10
        val ashCost = (qty + 9) / 10
        assertTrue(playerRepo.consumeItems(mapOf("rune_essence" to qty, "ashes" to ashCost)))
        assertTrue(
            playerRepo.enqueueAction(
                QueuedAction(
                    skillName = Skills.RUNECRAFTING,
                    activityKey = "chaos_rune",
                    skillDisplayName = "Runecrafting",
                    qty = qty,
                    catalystKey = "ashes",
                    catalystQty = ashCost,
                    consumedMaterials = mapOf("rune_essence" to qty),
                ),
            ),
        )

        val started = queuedStarter.startNextQueued()

        assertEquals("nothing qualifies, nothing starts", false, started)
        assertEquals("discarded order leaves the queue", 0, playerRepo.getFlags().sessionQueue.size)
        val inv = playerRepo.getInventory()
        assertEquals("discarded essence refunded", 100, inv["rune_essence"])
        assertEquals("discarded ashes refunded", 20, inv["ashes"])
    }

    @Test
    fun `repo-level prestige leaves prepaid order to be discarded without refund`() = driveFromBackground {
        // The prestige-tree screen prestiges through the repository with no queue eviction
        // (unlike the skill screens). The prepaid order survives at level 1 and the starter
        // then discards it — today with zero refund.
        playerRepo.applyMultiSkillResults(mapOf(Skills.RUNECRAFTING to 14_000_000L), emptyMap())
        assertEquals("test premise: runecrafting at 99", 99, playerRepo.getSkillLevels()[Skills.RUNECRAFTING])
        playerRepo.addItems(mapOf("rune_essence" to 100, "ashes" to 20))

        val qty = 10
        val ashCost = (qty + 9) / 10
        assertTrue(playerRepo.consumeItems(mapOf("rune_essence" to qty, "ashes" to ashCost)))
        assertTrue(
            playerRepo.enqueueAction(
                QueuedAction(
                    skillName = Skills.RUNECRAFTING,
                    activityKey = "fire_rune",
                    skillDisplayName = "Runecrafting",
                    qty = qty,
                    catalystKey = "ashes",
                    catalystQty = ashCost,
                    consumedMaterials = mapOf("rune_essence" to qty),
                ),
            ),
        )

        // Exactly what the prestige-tree screen does: repo prestige, no eviction, no refund.
        playerRepo.prestigeSkill(Skills.RUNECRAFTING)
        assertEquals("prestige ran", 1, playerRepo.getSkillLevels()[Skills.RUNECRAFTING])
        assertEquals(
            "no eviction on the tree-screen path",
            1,
            playerRepo.getFlags().sessionQueue.size,
        )

        val started = queuedStarter.startNextQueued()

        assertEquals("nothing qualifies, nothing starts", false, started)
        val inv = playerRepo.getInventory()
        assertEquals("discarded essence refunded", 100, inv["rune_essence"])
        assertEquals("discarded ashes refunded", 20, inv["ashes"])
    }
}
