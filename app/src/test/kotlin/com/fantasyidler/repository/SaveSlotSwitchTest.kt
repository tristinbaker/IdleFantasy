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
import com.fantasyidler.data.model.SkillSessionExport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import javax.inject.Provider

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SaveSlotSwitchTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var json: Json
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var saveSlotRepo: SaveSlotRepository
    private lateinit var globalStateRepo: GlobalStateRepository
    private lateinit var starter: QueuedSessionStarter

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
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo)
        val backupScheduler = BackupScheduler(context, sessionRepo, GlobalStateRepository(db.globalStateDao()))
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        globalStateRepo = GlobalStateRepository(db.globalStateDao())
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        val queuedSessionStarter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        )
        starter = queuedSessionStarter
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
            SkillSession(
                sessionId = "a_completed",
                skillName = "woodcutting",
                startedAt = base - 3_600_000L,
                endsAt = base - 1_800_000L,
                completed = true,
                activityKey = "oak_tree",
            )
        )
        sessionRepo.insertSession(
            SkillSession(
                sessionId = "a_active",
                skillName = "mining",
                startedAt = base,
                endsAt = base + 3_600_000L,
                completed = false,
                activityKey = "iron_ore",
            )
        )
    }

    /** Second character with values distinct from [seedCharacterA] so imports are observable. */
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

    /** Slot directory for the current Robolectric app, created on demand. */
    private fun slotsDir(): File = File(context.filesDir, "save_slots").apply { mkdirs() }

    private fun writeSlotFile(slot: Int, content: String) {
        File(slotsDir(), "slot_$slot.json").writeText(content)
    }

    private fun writeSwitchMarker(content: String) {
        File(slotsDir(), "pending_switch.marker").writeText(content)
    }

    /** Saves character B to [slot]'s file and returns its export JSON. */
    private suspend fun writeTargetSlotB(slot: Int): String {
        seedCharacterB()
        return saveSlotRepo.exportFullSave().also { writeSlotFile(slot, it) }
    }

    /** Seeds the outgoing character A and writes its complete snapshot to [slot]. */
    private suspend fun snapshotOutgoingA(slot: Int) {
        seedCharacterA(System.currentTimeMillis())
        writeSlotFile(slot, saveSlotRepo.exportFullSave())
    }

    @Test
    fun `switchTo empty slot snapshots outgoing character and creates fresh state`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())

        val ironmanDemoted = saveSlotRepo.switchTo(2)

        assertFalse(ironmanDemoted)
        assertEquals(2, saveSlotRepo.activeSlot())

        val freshPlayer = playerRepo.getOrCreatePlayer()
        assertEquals(0L, freshPlayer.coins)
        assertTrue(db.questProgressDao().getAllProgress().isEmpty())
        assertTrue(db.farmingPatchDao().getAllPatches().all { it.cropType == null && it.plantedAt == null })
        assertEquals(null, sessionRepo.getActiveSession())
        assertEquals(0, sessionRepo.getAllCompletedSessions().size)

        val slotsDir = File(context.filesDir, "save_slots")
        assertTrue(File(slotsDir, "slot_1.json").exists())
        assertTrue(File(slotsDir, "slot_1.meta.json").exists())
        val snapshot = json.decodeFromString<PlayerExport>(File(slotsDir, "slot_1.json").readText())
        assertEquals(111_111L, snapshot.coins)
        assertEquals("""{"mining":42}""", snapshot.skillLevels)
        val meta = json.decodeFromString<SaveSlotMeta>(File(slotsDir, "slot_1.meta.json").readText())
        assertEquals(111_111L, meta.coins)
    }

    @Test
    fun `switchTo back restores original coins xp and keeps absolute session end time`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        val base = System.currentTimeMillis()
        seedCharacterA(base)
        saveSlotRepo.switchTo(2)

        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(coins = 222_222L))

        val secondDemoted = saveSlotRepo.switchTo(1)

        assertFalse(secondDemoted)
        assertEquals(1, saveSlotRepo.activeSlot())
        assertTrue(saveSlotRepo.hasMultipleCharacters())

        val restored = playerRepo.getOrCreatePlayer()
        assertEquals(111_111L, restored.coins)
        assertEquals("""{"mining":42}""", restored.skillLevels)
        assertEquals("""{"mining":123456}""", restored.skillXp)
        assertEquals("""{"iron_ore":99}""", restored.inventory)
        assertEquals("""{"head":"bronze_helm"}""", restored.equipped)
        assertEquals("""[{"id":"rock_golem"}]""", restored.pets)
        assertEquals(66, json.decodeFromString<PlayerFlags>(restored.flags).currentHp)

        val active = sessionRepo.getActiveSession()
        assertNotNull(active)
        assertEquals("a_active", active!!.sessionId)
        assertFalse(active.completed)
        assertEquals(base + 3_600_000L, active.endsAt)
        val completed = sessionRepo.getAllCompletedSessions()
        assertEquals(listOf("a_completed"), completed.map { it.sessionId })

        assertTrue(File(context.filesDir, "save_slots/slot_2.json").exists())
    }

    @Test
    fun `imported incomplete session anchors monotonic clock so post-import clock jump cannot complete it`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        saveSlotRepo.switchTo(2)
        val snapshot = File(context.filesDir, "save_slots/slot_1.json").readText()

        saveSlotRepo.importFullSave(snapshot)

        val imported = sessionRepo.getActiveSession()!!
        assertEquals("a_active", imported.sessionId)
        assertFalse(imported.completed)
        assertNotNull(imported.startElapsedMs)
        assertTrue(imported.endsAt > System.currentTimeMillis())
        assertNull(
            sessionRepo.getAllCompletedSessions()
                .single { it.sessionId == "a_completed" }.startElapsedMs,
        )

        val jumpMs = 8L * 3_600_000L
        db.skillSessionDao().update(
            imported.copy(startedAt = imported.startedAt - jumpMs, endsAt = imported.endsAt - jumpMs),
        )

        sessionRepo.recoverActiveSession(starter)

        val after = sessionRepo.getSession("a_active")!!
        assertFalse(after.completed)
        assertEquals(1, sessionRepo.getAllCompletedSessions().size)
    }

    @Test
    fun `deleteSlot refuses to delete the active slot files`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        saveSlotRepo.switchTo(2)
        saveSlotRepo.switchTo(1)

        val slotOneJson = File(context.filesDir, "save_slots/slot_1.json")
        val slotOneMeta = File(context.filesDir, "save_slots/slot_1.meta.json")
        assertTrue(slotOneJson.exists())
        assertTrue(slotOneMeta.exists())

        saveSlotRepo.deleteSlot(1)

        assertTrue(slotOneJson.exists())
        assertTrue(slotOneMeta.exists())
        assertEquals(1, saveSlotRepo.activeSlot())
    }

    @Test
    fun `deleteSlot removes inactive slot files`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        saveSlotRepo.switchTo(2)
        saveSlotRepo.switchTo(1)

        val slotTwoJson = File(context.filesDir, "save_slots/slot_2.json")
        val slotTwoMeta = File(context.filesDir, "save_slots/slot_2.meta.json")
        assertTrue(slotTwoJson.exists())
        assertTrue(slotTwoMeta.exists())

        saveSlotRepo.deleteSlot(2)

        assertFalse(slotTwoJson.exists())
        assertFalse(slotTwoMeta.exists())
        assertTrue(File(context.filesDir, "save_slots/slot_1.json").exists())
    }

    // ------------------------------------------------------------------
    // Switch/recovery (D2 + review rounds: identity, fresh replay, guards).
    // run-by-orchestrator: workers run no Gradle; the orchestrator executes
    // these tests on this branch (GREEN) and on the base (RED).
    // ------------------------------------------------------------------

    /**
     * Legacy marker resolution by live material: the bare pre-fix marker rolls back when Room
     * holds the outgoing character (with or without a target file), and completes by pointer only
     * when there is no file and no live progress — the interrupted new-slot reset already ran.
     */
    @Test
    fun `legacy marker rolls back a live character and completes an empty one`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        writeTargetSlotB(2)
        seedCharacterA(System.currentTimeMillis())
        val live = playerRepo.getOrCreatePlayer()
        writeSwitchMarker("2")
        assertFalse(File(slotsDir(), "slot_1.json").exists())

        val recovered = saveSlotRepo.recoverInterruptedSwitch()

        assertFalse(recovered)
        assertEquals(1, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        // Rollback only clears the marker: no snapshot is written by recovery.
        assertFalse(File(slotsDir(), "slot_1.json").exists())
        assertEquals(live, playerRepo.getOrCreatePlayer())
        assertEquals(1, db.questProgressDao().getAllProgress().size)
        assertEquals(1, db.farmingPatchDao().getAllPatches().size)

        // With live material and no target file the rollback stands (outgoing still alive).
        writeSwitchMarker("3")
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(1, saveSlotRepo.activeSlot())

        // Case (b): the interrupted fresh reset wiped Room while the outgoing snapshot sat on
        // disk; completion must move the pointer only and keep that file intact.
        snapshotOutgoingA(1)
        playerRepo.resetProgression()
        sessionRepo.deleteAllSessions()
        writeSwitchMarker("3")

        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(3, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertEquals(111_111L, json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText()).coins)
        assertFalse(File(slotsDir(), "slot_3.json").exists())
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
    }

    /**
     * Legacy marker from inside the (non-atomic) import: the player row already holds the
     * target's material fields but the sessions were not swapped yet. Recovery must re-import the
     * file — the source of truth for sessions/quests/patches — or the target would inherit the
     * outgoing sessions. The tail repeats it with a double-written (garbage-trailing) file.
     */
    @Test
    fun `legacy marker completes the switch when Room already holds the target`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterB()
        sessionRepo.insertSession(
            SkillSession("b_completed", skillName = "fishing", startedAt = 1L, endsAt = 2L, completed = true))
        val targetFileJson = saveSlotRepo.exportFullSave()
        writeSlotFile(2, targetFileJson)
        sessionRepo.deleteAllSessions()
        snapshotOutgoingA(1)

        // Crash state: player row = B's material fields, sessions still A's (import half-done).
        val target = json.decodeFromString<PlayerExport>(targetFileJson)
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = target.skillLevels, skillXp = target.skillXp,
                inventory = target.inventory, equipped = target.equipped,
                pets = target.pets, coins = target.coins,
            ),
        )
        writeSwitchMarker("2")

        val recovered = saveSlotRepo.recoverInterruptedSwitch()

        assertTrue(recovered)
        assertEquals(2, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertEquals(222_222L, playerRepo.getOrCreatePlayer().coins)
        // getActiveSession() returns the newest row even when completed (DAO quirk, hunt finding
        // F8), so assert the swap at the row level: only the file's session remains.
        assertEquals(listOf("b_completed"), db.skillSessionDao().getAllSessions().map { it.sessionId })
        assertEquals(listOf("b_completed"), sessionRepo.getAllCompletedSessions().map { it.sessionId })
        assertEquals(111_111L, json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText()).coins)

        // Trailing garbage (a double write the repo tolerates) must not break the identity parse.
        globalStateRepo.setActiveSaveSlot(1)
        writeSlotFile(2, targetFileJson + targetFileJson)
        writeSwitchMarker("2")
        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(2, saveSlotRepo.activeSlot())

        // A legacy file still carrying the raw "hp" key must match too: the live row migrates it
        // away inside getOrCreatePlayer before the comparison.
        val hpXp = """{"hp":5000,"fishing":9000}"""
        val hpLevels = """{"hp":10,"fishing":7}"""
        writeSlotFile(2, json.encodeToString(json.decodeFromString<PlayerExport>(targetFileJson)
            .copy(skillXp = hpXp, skillLevels = hpLevels)))
        globalStateRepo.setActiveSaveSlot(1)
        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(skillXp = hpXp, skillLevels = hpLevels))
        writeSwitchMarker("2")

        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(2, saveSlotRepo.activeSlot())
        assertEquals(listOf("b_completed"), sessionRepo.getAllCompletedSessions().map { it.sessionId })
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
    }

    /** Crash after the ready marker: the outgoing character is on disk, so recovery imports
     *  the target and moves the pointer. */
    @Test
    fun `phase 2 crash completes the switch by importing the target`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        writeTargetSlotB(2)
        snapshotOutgoingA(1)
        writeSwitchMarker("ready:2")

        val recovered = saveSlotRepo.recoverInterruptedSwitch()

        assertTrue(recovered)
        assertEquals(2, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        val imported = playerRepo.getOrCreatePlayer()
        assertEquals(222_222L, imported.coins)
        assertEquals("""{"fishing":7}""", imported.skillLevels)
        assertEquals("""{"shrimp":5}""", imported.inventory)
        assertEquals(33, json.decodeFromString<PlayerFlags>(imported.flags).currentHp)
        // The outgoing snapshot is untouched by the completion.
        val outgoing = json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText())
        assertEquals(111_111L, outgoing.coins)
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
    }

    /** Crash in the new-slot branch during the fresh reset: recovery replays the idempotent
     *  reset to completion and moves the pointer. No slot file may be created. */
    @Test
    fun `fresh target marker replays the reset for a new slot`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        writeSwitchMarker("fresh:1:2")

        val recovered = saveSlotRepo.recoverInterruptedSwitch()

        assertTrue(recovered)
        assertEquals(2, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertFalse(File(slotsDir(), "slot_1.json").exists())
        assertFalse(File(slotsDir(), "slot_2.json").exists())
        assertNull(sessionRepo.getActiveSession())
        assertEquals(0, sessionRepo.getAllCompletedSessions().size)
        assertTrue(db.questProgressDao().getAllProgress().isEmpty())
        assertTrue(db.farmingPatchDao().getAllPatches().all { it.cropType == null && it.plantedAt == null })
        val fresh = playerRepo.getOrCreatePlayer()
        assertEquals(0L, fresh.coins)
        assertTrue(json.decodeFromString<PlayerFlags>(fresh.flags).ironman)

        // A `ready:` marker whose file vanished (slot deleted before recovery) replays the reset.
        seedCharacterA(System.currentTimeMillis())
        writeSwitchMarker("ready:3")
        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(3, saveSlotRepo.activeSlot())
        assertNull(sessionRepo.getActiveSession())
        assertEquals(0L, playerRepo.getOrCreatePlayer().coins)
        // ironman is assumed false: the ready marker does not record the choice.
        assertFalse(json.decodeFromString<PlayerFlags>(playerRepo.getOrCreatePlayer().flags).ironman)
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
    }

    /** A marker left after the pointer already moved (failed delete or crash after
     *  setActiveSaveSlot) must not re-import the slot file over progress made since the switch. */
    @Test
    fun `marker on an already-active target is cleared without re-importing`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterB()
        writeSlotFile(2, saveSlotRepo.exportFullSave())
        globalStateRepo.setActiveSaveSlot(2)
        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(coins = 333_333L))

        writeSwitchMarker("2")
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(333_333L, playerRepo.getOrCreatePlayer().coins)

        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(coins = 444_444L))
        writeSwitchMarker("ready:2")
        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(444_444L, playerRepo.getOrCreatePlayer().coins)

        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertEquals(2, saveSlotRepo.activeSlot())
    }

    /** Happy path regression: a completed switch leaves no marker and a full outgoing snapshot. */
    @Test
    fun `completed switch clears the marker and keeps a full outgoing snapshot`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())

        val demoted = saveSlotRepo.switchTo(2)

        assertFalse(demoted)
        assertEquals(2, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        val outgoing = json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText())
        assertEquals(111_111L, outgoing.coins)
        assertEquals("""{"mining":42}""", outgoing.skillLevels)
        assertEquals("""{"mining":123456}""", outgoing.skillXp)
        assertTrue(File(slotsDir(), "slot_1.meta.json").exists())
    }
}
