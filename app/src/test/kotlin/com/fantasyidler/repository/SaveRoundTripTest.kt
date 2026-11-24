package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerExport
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QuestProgress
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.SkillSessionExport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SaveRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var json: Json
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var saveSlotRepo: SaveSlotRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        val dailyQuestRepo = DailyQuestRepository(gameData)
        val weeklyQuestRepo = WeeklyQuestRepository(gameData)
        val boostRepo = BoostRepository(gameData)
        val buffNotifScheduler = BuffNotificationScheduler(context)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            dailyQuestRepo,
            weeklyQuestRepo,
            buffNotifScheduler,
            gameData,
            boostRepo,
            db,
        )
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo, StoryRepository(playerRepo))
        val backupScheduler = BackupScheduler(context, sessionRepo, GlobalStateRepository(db.globalStateDao()))
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        val globalStateRepo = GlobalStateRepository(db.globalStateDao())
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        val queuedSessionStarter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        )
        val workerStarter = WorkerQueuedSessionStarter(boostRepo, playerRepo, sessionRepo, gameData, json)
        val seasonalEventRepo = SeasonalEventRepository(playerRepo, gameData, dailyQuestRepo, context)
        val farmingRepo = FarmingRepository(
            context, db, db.farmingPatchDao(), playerRepo, gameData,
            seasonalEventRepo, globalStateRepo, json, boostRepo,
        )
        val guildRepo = GuildRepository(playerRepo, db.questProgressDao(), gameData, Provider { townRepo })
        saveSlotRepo = SaveSlotRepository(
            context, playerRepo, sessionRepo, questRepo, farmingRepo, guildRepo,
            globalStateRepo, queuedSessionStarter, workerStarter, backupScheduler,
            buffNotifScheduler, json,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedCharacter(now: Long) {
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"mining":42,"fishing":17}""",
                skillXp = """{"mining":123456,"fishing":4321}""",
                inventory = """{"iron_ore":99,"raw_shrimp":7,"bronze_pickaxe":1}""",
                equipped = """{"head":"bronze_helm","weapon_atk":null}""",
                pets = """[{"id":"rock_golem","boost_percent":5}]""",
                coins = 777_777L,
                flags = json.encodeToString<PlayerFlags>(PlayerFlags(currentHp = 66, foodEatThresholdPct = 77)),
            )
        )
        db.questProgressDao().upsert(QuestProgress("sheep_shearer", progress = 3))
        db.questProgressDao().upsert(
            QuestProgress("cooks_assistant", progress = 1, completed = true, completedAt = now)
        )
        db.farmingPatchDao().upsert(FarmingPatch(patchNumber = 0, cropType = "guam_seed", plantedAt = now))
        db.farmingPatchDao().upsert(FarmingPatch(patchNumber = 2))
        sessionRepo.insertSession(
            SkillSession(
                sessionId = "completed_main",
                skillName = "woodcutting",
                startedAt = now - 7_200_000L,
                endsAt = now - 3_600_000L,
                frames = """[{"i":1}]""",
                completed = true,
                activityKey = "oak_tree",
                levelAtStart = 40,
            )
        )
        sessionRepo.insertSession(
            SkillSession(
                sessionId = "active_main",
                skillName = "mining",
                startedAt = now,
                endsAt = now + 3_600_000L,
                frames = """[{"i":2}]""",
                completed = false,
                activityKey = "iron_ore",
            )
        )
        sessionRepo.insertSession(
            SkillSession(
                sessionId = "completed_worker1",
                skillName = "fishing",
                startedAt = now - 3_000_000L,
                endsAt = now - 600_000L,
                frames = "[]",
                completed = true,
                activityKey = "raw_shrimp",
                isWorkerSession = true,
                efficiencyMultiplier = 1.25f,
                workerSlot = 1,
            )
        )
        sessionRepo.insertSession(
            SkillSession(
                sessionId = "active_worker2",
                skillName = "mining",
                startedAt = now,
                endsAt = now + 1_800_000L,
                frames = "[]",
                completed = false,
                activityKey = "coal_ore",
                isWorkerSession = true,
                efficiencyMultiplier = 1.5f,
                workerSlot = 2,
            )
        )
    }

    @Test
    fun `export wipe then frozen import restores player quests patches and shifts session end times`() = runBlocking {
        val now = System.currentTimeMillis()
        seedCharacter(now)

        val exported = saveSlotRepo.exportFullSave()
        val parsed = json.decodeFromString<PlayerExport>(exported)
        assertEquals(4, parsed.sessions.size)
        assertEquals(777_777L, parsed.coins)

        playerRepo.resetProgression()
        db.questProgressDao().deleteAll()
        db.farmingPatchDao().clearAll()
        sessionRepo.deleteAllSessions()
        sessionRepo.deleteAllWorkerSessions()
        assertEquals(0L, playerRepo.getOrCreatePlayer().coins)
        assertTrue(db.questProgressDao().getAllProgress().isEmpty())
        assertTrue(db.farmingPatchDao().getAllPatches().all { it.cropType == null && it.plantedAt == null })

        val beforeImport = System.currentTimeMillis()
        val ironmanDemoted = saveSlotRepo.importFullSave(exported, freezeSessionTimers = true)
        val afterImport = System.currentTimeMillis()

        assertFalse(ironmanDemoted)

        val player = playerRepo.getOrCreatePlayer()
        assertEquals("""{"mining":42,"fishing":17}""", player.skillLevels)
        assertEquals("""{"mining":123456,"fishing":4321}""", player.skillXp)
        assertEquals("""{"iron_ore":99,"raw_shrimp":7,"bronze_pickaxe":1}""", player.inventory)
        assertEquals("""{"head":"bronze_helm","weapon_atk":null}""", player.equipped)
        assertEquals(listOf(OwnedPet("rock_golem", 5)), json.decodeFromString<List<OwnedPet>>(player.pets))
        assertEquals(777_777L, player.coins)
        val restoredFlags = json.decodeFromString<PlayerFlags>(player.flags)
        assertEquals(66, restoredFlags.currentHp)
        assertEquals(77, restoredFlags.foodEatThresholdPct)

        val expectedQuests = listOf(
            QuestProgress("cooks_assistant", progress = 1, completed = true, completedAt = now),
            QuestProgress("sheep_shearer", progress = 3),
        ).sortedBy { it.questId }
        assertEquals(expectedQuests, db.questProgressDao().getAllProgress().sortedBy { it.questId })

        val expectedPatches = listOf(
            FarmingPatch(patchNumber = 0, cropType = "guam_seed", plantedAt = now),
            FarmingPatch(patchNumber = 2),
        ).sortedBy { it.patchNumber }
        assertEquals(expectedPatches, db.farmingPatchDao().getAllPatches().sortedBy { it.patchNumber })

        val importedById = buildMap<String, SkillSession> {
            sessionRepo.getActiveSession()?.let { put(it.sessionId, it) }
            sessionRepo.getAllCompletedSessions().forEach { put(it.sessionId, it) }
            for (slot in 1..2) {
                sessionRepo.getActiveWorkerSession(slot)?.let { put(it.sessionId, it) }
                sessionRepo.getAllCompletedWorkerSessions(slot)?.forEach { put(it.sessionId, it) }
            }
        }
        assertEquals(parsed.sessions.map { it.sessionId }.toSet(), importedById.keys)

        for (exportedSession in parsed.sessions) {
            val restored = importedById.getValue(exportedSession.sessionId)
            assertSameExceptEndsAt(exportedSession, restored)
            if (exportedSession.completed) {
                assertEquals(exportedSession.endsAt, restored.endsAt)
            } else {
                val remainingMs = exportedSession.endsAt - parsed.exportedAt
                assertTrue(remainingMs > 0L)
                val shiftedLow = beforeImport + remainingMs
                val shiftedHigh = afterImport + remainingMs
                assertTrue(restored.endsAt in shiftedLow..shiftedHigh)
            }
        }
    }

    private fun assertSameExceptEndsAt(expected: SkillSessionExport, actual: SkillSession) {
        assertEquals(expected.skillName, actual.skillName)
        assertEquals(expected.activityKey, actual.activityKey)
        assertEquals(expected.startedAt, actual.startedAt)
        assertEquals(expected.frames, actual.frames)
        assertEquals(expected.completed, actual.completed)
        assertEquals(expected.isWorkerSession, actual.isWorkerSession)
        assertEquals(expected.efficiencyMultiplier, actual.efficiencyMultiplier)
        assertEquals(expected.workerSlot, actual.workerSlot)
    }
}
