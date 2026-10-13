package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.PlayerExport
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QuestProgress
import com.fantasyidler.data.model.SkillSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import javax.inject.Provider

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SaveSlotIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var json: Json
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var saveSlotRepo: SaveSlotRepository
    private lateinit var globalStateRepo: GlobalStateRepository

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
            db.playerDao(), db.questProgressDao(), db.farmingPatchDao(), json,
            dailyQuestRepo, weeklyQuestRepo, buffNotifScheduler, gameData, boostRepo, db,
        )
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo)
        val backupScheduler = BackupScheduler(context, sessionRepo, GlobalStateRepository(db.globalStateDao()))
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        globalStateRepo = GlobalStateRepository(db.globalStateDao())
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
        slotsDir().listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        slotsDir().listFiles()?.forEach { it.delete() }
        db.close()
    }

    private suspend fun seedCharacterA(base: Long) {
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"mining":42}""",
                skillXp = """{"mining":123456}""",
                inventory = """{"iron_ore":99}""",
                equipped = """{"head":"bronze_helm"}""",
                pets = """[{"id":"rock_golem"}]""",
                coins = 111_111L,
                flags = json.encodeToString<PlayerFlags>(PlayerFlags(currentHp = 66)),
            )
        )
        db.questProgressDao().upsert(QuestProgress("sheep_shearer", progress = 3))
        db.farmingPatchDao().upsert(FarmingPatch(patchNumber = 0, cropType = "guam_seed", plantedAt = base))
        sessionRepo.insertSession(
            SkillSession("a_completed", skillName = "woodcutting", startedAt = base - 3_600_000L, endsAt = base - 1_800_000L, completed = true, activityKey = "oak_tree")
        )
        sessionRepo.insertSession(
            SkillSession("a_active", skillName = "mining", startedAt = base, endsAt = base + 3_600_000L, completed = false, activityKey = "iron_ore")
        )
    }

    private suspend fun seedCharacterB() {
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"fishing":7}""",
                skillXp = """{"fishing":9000}""",
                inventory = """{"shrimp":5}""",
                coins = 222_222L,
                flags = json.encodeToString<PlayerFlags>(PlayerFlags(currentHp = 33, characterName = "B")),
            )
        )
    }

    private fun slotsDir(): File = File(context.filesDir, "save_slots").apply { mkdirs() }

    private fun writeSlotFile(slot: Int, content: String) {
        File(slotsDir(), "slot_$slot.json").writeText(content)
    }

    private fun writeSwitchMarker(content: String) {
        File(slotsDir(), "pending_switch.marker").writeText(content)
    }

    private suspend fun writeTargetSlotB(slot: Int): String {
        seedCharacterB()
        return saveSlotRepo.exportFullSave().also { writeSlotFile(slot, it) }
    }

    private suspend fun snapshotOutgoingA(slot: Int) {
        seedCharacterA(System.currentTimeMillis())
        writeSlotFile(slot, saveSlotRepo.exportFullSave())
    }

    // PART1: untested-method coverage (slotInfos, hasMultiple, guards, demote/emit,
    // freeze remap, writeAtomic). Commit as batch 1.
    /**
     * slotInfos: active row is live, inactive prefers meta, corrupt meta falls back to the
     * full save, doubly-corrupt reads as missing. Expected from readInactiveSlot order.
     */
    @Test
    fun `slotInfos prefers meta falls back to save then missing`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        writeTargetSlotB(2)
        seedCharacterA(System.currentTimeMillis())

        val infos = saveSlotRepo.slotInfos()
        assertEquals(3, infos.size)
        assertTrue(infos[0].isActive && infos[0].exists && infos[0].coins == 111_111L)
        assertTrue(!infos[1].isActive && infos[1].exists && infos[1].coins == 222_222L)
        assertFalse(infos[2].exists)

        File(slotsDir(), "slot_2.meta.json").writeText("{corrupt!!")
        assertEquals(222_222L, saveSlotRepo.slotInfos()[1].coins)

        File(slotsDir(), "slot_2.json").writeText("{corrupt!!")
        val missing = saveSlotRepo.slotInfos()[1]
        assertFalse(missing.exists)
        assertEquals(0L, missing.coins)
    }

    /**
     * hasMultipleCharacters counts any inactive meta-or-file; deleteSlot on a missing slot
     * is a no-op that keeps the pointer. Expected from the any(meta||file) check.
     */
    @Test
    fun `hasMultipleCharacters counts meta-or-file and missing delete is no-op`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        playerRepo.getOrCreatePlayer()
        assertFalse(saveSlotRepo.hasMultipleCharacters())

        seedCharacterA(System.currentTimeMillis())
        saveSlotRepo.switchTo(2)
        assertTrue(saveSlotRepo.hasMultipleCharacters())

        // Meta-only still counts: drop the json, keep the meta.
        File(slotsDir(), "slot_1.json").delete()
        assertTrue(File(slotsDir(), "slot_1.meta.json").exists())
        assertTrue(saveSlotRepo.hasMultipleCharacters())

        saveSlotRepo.deleteSlot(1)
        assertFalse(saveSlotRepo.hasMultipleCharacters())
        saveSlotRepo.deleteSlot(3)
        assertEquals(2, saveSlotRepo.activeSlot())
    }

    /**
     * switchTo guards: out-of-range requires, same-slot returns false with no marker,
     * no snapshot and no event. Expected from require + early return before backup.
     */
    @Test
    fun `switchTo guards reject bad slots and ignore same-slot`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())

        try {
            saveSlotRepo.switchTo(0)
            fail("expected IllegalArgumentException for slot 0")
        } catch (_: IllegalArgumentException) { }
        try {
            saveSlotRepo.switchTo(4)
            fail("expected IllegalArgumentException for slot 4")
        } catch (_: IllegalArgumentException) { }

        assertFalse(saveSlotRepo.switchTo(1))
        assertEquals(1, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertFalse(File(slotsDir(), "slot_1.json").exists())
        assertNull(withTimeoutOrNull(200) { saveSlotRepo.switchEvents.first() })
    }

    /**
     * switchTo demotes an edited ironman target and emits once. Expected from importSave
     * sig check (coins tamper keeps stale sig) plus tryEmit after pointer move.
     */
    @Test
    fun `switchTo demotes edited ironman and emits`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        playerRepo.resetProgression(ironman = true)
        val exported = saveSlotRepo.exportFullSave()
        val parsed = json.decodeFromString<PlayerExport>(exported)
        assertTrue(json.decodeFromString<PlayerFlags>(parsed.flags).ironman)
        val tampered = json.encodeToString(parsed.copy(coins = parsed.coins + 1))
        writeSlotFile(2, tampered)
        seedCharacterA(System.currentTimeMillis())

        val first = async { withTimeout(5000) { saveSlotRepo.switchEvents.first() } }
        val demoted = saveSlotRepo.switchTo(2)

        assertTrue(demoted)
        assertEquals(2, saveSlotRepo.activeSlot())
        assertFalse(json.decodeFromString<PlayerFlags>(playerRepo.getOrCreatePlayer().flags).ironman)
        first.await()
    }

    /**
     * importFullSave freeze remaps remaining (endsAt-exportedAt) while slot mode keeps the
     * absolute endsAt. Expected from the freezeSessionTimers branch.
     */
    @Test
    fun `importFullSave freeze remaps remaining absolute mode keeps endsAt`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        val base = System.currentTimeMillis()
        seedCharacterA(base)
        val exported = saveSlotRepo.exportFullSave()
        val parsed = json.decodeFromString<PlayerExport>(exported)
        val origActive = parsed.sessions.single { !it.completed }
        val oldExport = json.encodeToString(parsed.copy(exportedAt = parsed.exportedAt - 7_200_000L))
        val remaining = origActive.endsAt - (parsed.exportedAt - 7_200_000L)

        playerRepo.resetProgression()
        sessionRepo.deleteAllSessions()
        val before = System.currentTimeMillis()
        saveSlotRepo.importFullSave(oldExport, freezeSessionTimers = true)
        val after = System.currentTimeMillis()
        val frozenEnds = sessionRepo.getSession(origActive.sessionId)!!.endsAt
        assertTrue(frozenEnds in (before + remaining)..(after + remaining))

        playerRepo.resetProgression()
        sessionRepo.deleteAllSessions()
        saveSlotRepo.importFullSave(oldExport, freezeSessionTimers = false)
        assertEquals(origActive.endsAt, sessionRepo.getSession(origActive.sessionId)!!.endsAt)
    }

    /**
     * writeAtomic happy path leaves parseable files and no tmp leftovers. The rename-fallback
     * needs filesystem fault injection (not pinned here). Expected from snapshotCurrent.
     */
    @Test
    fun `snapshots leave no tmp files`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        saveSlotRepo.switchTo(2)

        assertTrue(File(slotsDir(), "slot_1.json").exists())
        assertTrue(File(slotsDir(), "slot_1.meta.json").exists())
        assertTrue(slotsDir().listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

