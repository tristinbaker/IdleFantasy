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

    // PART2: catastrophic windows (bare-origin, mid-import workers, concurrent pair)
    // plus warning characterizations A-F1, A-F2, B-W-1, B-W-2 and corrupt markers.
    // Commit as batch 2.
    /**
     * Catastrophic: bare marker with a good origin file and live outgoing rolls back.
     * Expected: material live fails empty branch, sessions present fail heal probe.
     */
    @Test
    fun `bare marker with existing origin file rolls back`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        snapshotOutgoingA(1)
        assertFalse(File(slotsDir(), "slot_2.json").exists())
        val liveBefore = playerRepo.getOrCreatePlayer()
        writeSwitchMarker("2")

        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(1, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertEquals(liveBefore, playerRepo.getOrCreatePlayer())
        assertEquals(111_111L, json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText()).coins)
    }

    /**
     * Catastrophic mid-import with worker sessions: player row already target, sessions
     * still outgoing. Recovery re-imports the file (source of truth) including workers.
     */
    @Test
    fun `mid-import player commit with workers completes from file`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        val base = System.currentTimeMillis()
        seedCharacterB()
        db.questProgressDao().upsert(QuestProgress("cooks_assistant", progress = 1))
        db.farmingPatchDao().upsert(FarmingPatch(patchNumber = 1, cropType = "potato_seed", plantedAt = base))
        sessionRepo.insertSession(
            SkillSession("b_w_active", skillName = "fishing", startedAt = base, endsAt = base + 3_600_000L, completed = false, activityKey = "raw_shrimp", isWorkerSession = true, workerSlot = 1)
        )
        sessionRepo.insertSession(
            SkillSession("b_w_done", skillName = "mining", startedAt = base - 3_600_000L, endsAt = base - 1_800_000L, completed = true, activityKey = "iron_ore", isWorkerSession = true, workerSlot = 2)
        )
        val targetJson = saveSlotRepo.exportFullSave()
        writeSlotFile(2, targetJson)
        snapshotOutgoingA(1)

        val target = json.decodeFromString<PlayerExport>(targetJson)
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = target.skillLevels, skillXp = target.skillXp, inventory = target.inventory,
                equipped = target.equipped, pets = target.pets, coins = target.coins,
            )
        )
        writeSwitchMarker("2")

        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(2, saveSlotRepo.activeSlot())
        assertEquals(222_222L, playerRepo.getOrCreatePlayer().coins)
        assertEquals("b_w_active", sessionRepo.getActiveWorkerSession(1)?.sessionId)
        assertEquals(listOf("b_w_done"), sessionRepo.getAllCompletedWorkerSessions(2).map { it.sessionId })
        assertEquals(111_111L, json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_1.json").readText()).coins)
    }

    /**
     * Catastrophic concurrent switchTo x2 end-to-end: two imports race from one origin.
     * Mutex serializes; final live is one target, no marker, origin snapshot intact.
     */
    @Test
    fun `concurrent switchTo pair serializes end-to-end`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        writeTargetSlotB(2)
        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(coins = 333_333L))
        writeSlotFile(3, saveSlotRepo.exportFullSave())
        seedCharacterA(System.currentTimeMillis())

        val d1 = async(Dispatchers.IO) { saveSlotRepo.switchTo(2) }
        val d2 = async(Dispatchers.IO) { saveSlotRepo.switchTo(3) }
        d1.await()
        d2.await()

        val active = saveSlotRepo.activeSlot()
        assertTrue(active == 2 || active == 3)
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        val coins = playerRepo.getOrCreatePlayer().coins
        assertTrue(coins == 222_222L || coins == 333_333L)
        assertTrue(File(slotsDir(), "slot_1.json").exists())
    }

    /**
     * A-F1 FIX (Option 2, predicate-only): rich inventory/gear/pet with zero coins and zero XP
     * is material under the widened predicate, so a legacy marker with no target file rolls
     * back instead of completing pointer-only: the outgoing character stays live at its slot
     * and no snapshot file is created by recovery.
     */
    @Test
    fun `A-F1 rich inventory with zero coins and XP rolls back`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"mining":1}""",
                skillXp = """{"mining":0}""",
                inventory = """{"iron_ore":99,"shrimp":50}""",
                equipped = """{"head":"bronze_helm"}""",
                pets = """[{"id":"rock_golem"}]""",
                coins = 0L,
            )
        )
        assertFalse(File(slotsDir(), "slot_1.json").exists())
        writeSwitchMarker("2")

        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(1, saveSlotRepo.activeSlot())
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        assertFalse(File(slotsDir(), "slot_1.json").exists())
        val live = playerRepo.getOrCreatePlayer()
        assertEquals("""{"iron_ore":99,"shrimp":50}""", live.inventory)
        assertEquals("""{"head":"bronze_helm"}""", live.equipped)
        assertEquals("""[{"id":"rock_golem"}]""", live.pets)
        assertEquals(0L, live.coins)
    }

    /**
     * A-F2 CHARACTERIZATION: when both the empty branch and the heal probe fire (zero
     * coins/XP live matching a quest/patch/session snapshot with wiped live sections), the empty
     * branch wins by order so quests stay empty (temporary detour). The origin snapshot is
     * untouched by the empty path, so switching back reimports quests/patches/sessions
     * (self-heal). Pins CURRENT ordering + self-heal.
     */
    @Test
    fun `A-F2 empty branch wins over heal when both fire`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        val base = System.currentTimeMillis()
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"mining":1}""", skillXp = """{"mining":0}""",
                inventory = """{}""", coins = 0L,
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
        writeSlotFile(1, saveSlotRepo.exportFullSave())
        val snapshotText = File(slotsDir(), "slot_1.json").readText()
        // Simulate the medio-wipeado crash window: player row intact, live sections wiped.
        sessionRepo.deleteAllSessions()
        sessionRepo.deleteAllWorkerSessions()
        db.questProgressDao().deleteAll()
        db.farmingPatchDao().clearAll()
        assertNull(sessionRepo.getActiveSession())
        assertTrue(sessionRepo.getAllCompletedSessions().isEmpty())
        writeSwitchMarker("2")

        // Empty branch wins by order: detour to slot 2 with live sections still empty.
        assertTrue(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(2, saveSlotRepo.activeSlot())
        assertTrue(db.questProgressDao().getAllProgress().isEmpty())
        assertTrue(db.farmingPatchDao().getAllPatches().all { it.cropType == null })
        // Origin snapshot untouched by the empty path — precondition for self-heal.
        assertEquals(snapshotText, File(slotsDir(), "slot_1.json").readText())

        // Switch-back self-heal: reimporting the untouched snapshot restores everything.
        saveSlotRepo.switchTo(1)
        assertEquals(1, saveSlotRepo.activeSlot())
        assertEquals(3, db.questProgressDao().getAllProgress().single { it.questId == "sheep_shearer" }.progress)
        assertEquals("guam_seed", db.farmingPatchDao().getAllPatches().single { it.patchNumber == 0 }.cropType)
        assertEquals("a_active", sessionRepo.getActiveSession()?.sessionId)
        assertEquals(listOf("a_completed"), sessionRepo.getAllCompletedSessions().map { it.sessionId })
        assertFalse(File(slotsDir(), "pending_switch.marker").exists())
    }

    /**
     * B-W-1 CHARACTERIZATION: inventory compares as raw strings, so the same map in a
     * different key order mismatches and the legacy identity guard returns false (rollback).
     * Pins CURRENT string compare; map-equal would complete.
     */
    @Test
    fun `B-W-1 reordered inventory keys mismatch identity`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        db.playerDao().upsert(
            playerRepo.getOrCreatePlayer().copy(
                skillLevels = """{"mining":7}""", skillXp = """{"mining":9000}""",
                inventory = """{"iron_ore":99,"shrimp":5}""", coins = 222_222L,
            )
        )
        writeSlotFile(2, saveSlotRepo.exportFullSave())
        db.playerDao().upsert(playerRepo.getOrCreatePlayer().copy(inventory = """{"shrimp":5,"iron_ore":99}"""))
        val liveInv = json.decodeFromString<Map<String, Int>>(playerRepo.getOrCreatePlayer().inventory)
        val fileInv = json.decodeFromString<Map<String, Int>>(json.decodeFromString<PlayerExport>(File(slotsDir(), "slot_2.json").readText()).inventory)
        assertEquals(fileInv, liveInv)
        writeSwitchMarker("2")

        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(1, saveSlotRepo.activeSlot())
        assertEquals("""{"shrimp":5,"iron_ore":99}""", playerRepo.getOrCreatePlayer().inventory)
    }

    /**
     * B-W-2 CHARACTERIZATION: a live completed-only session blocks the heal probe (probe
     * requires zero active and zero completed in player and both worker slots). Pins CURRENT.
     */
    @Test
    fun `B-W-2 completed-only live session blocks heal`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        snapshotOutgoingA(1)
        sessionRepo.deleteAllSessions()
        sessionRepo.deleteAllWorkerSessions()
        db.questProgressDao().deleteAll()
        db.farmingPatchDao().clearAll()
        sessionRepo.insertSession(
            SkillSession("only_done", skillName = "woodcutting", startedAt = 1L, endsAt = 2L, completed = true, activityKey = "oak_tree")
        )
        writeSwitchMarker("2")

        assertFalse(saveSlotRepo.recoverInterruptedSwitch())
        assertEquals(1, saveSlotRepo.activeSlot())
        assertEquals(listOf("only_done"), sessionRepo.getAllCompletedSessions().map { it.sessionId })
        assertTrue(db.questProgressDao().getAllProgress().isEmpty())
    }

    /**
     * Corrupt markers (garbage, out-of-range, malformed fresh) clear without touching Room.
     * Expected from parsePendingSwitch null path.
     */
    @Test
    fun `corrupt markers clear without touching Room`() = runBlocking {
        globalStateRepo.setActiveSaveSlot(1)
        seedCharacterA(System.currentTimeMillis())
        val liveBefore = playerRepo.getOrCreatePlayer()

        for (bad in listOf("garbage!!", "ready:99", "fresh:x:2", "fresh:1", "")) {
            writeSwitchMarker(bad)
            assertFalse(saveSlotRepo.recoverInterruptedSwitch())
            assertEquals(1, saveSlotRepo.activeSlot())
            assertEquals(liveBefore, playerRepo.getOrCreatePlayer())
            assertFalse(File(slotsDir(), "pending_switch.marker").exists())
        }
    }
}
