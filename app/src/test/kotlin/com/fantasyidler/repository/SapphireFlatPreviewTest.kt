package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Issue #2056 (mainland): previewFlatXpGrant must mirror the payout —
 * applyMultiSkillResultsUnlocked folds sigils.xpMult into finalXp, so the
 * lamp/quest confirmation preview must include the same +5%-per-sapphire.
 * Scope: mainland only; isle flat grants are out of scope (dev decision).
 *
 * run-by-orchestrator (worker sin Gradle por disciplina de lote).
 * RED en codigo viejo: el preview ignora los zafiros y devuelve la base.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SapphireFlatPreviewTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        val boostRepo = BoostRepository(gameData)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            DailyQuestRepository(gameData),
            WeeklyQuestRepository(gameData),
            BuffNotificationScheduler(context),
            gameData,
            boostRepo,
            db,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun eightSapphires(): Map<String, String> = mapOf(
        "elder_helm" to "elder_sapphire",
        "elder_platebody" to "elder_sapphire",
        "elder_platelegs" to "elder_sapphire",
        "elder_boots" to "elder_sapphire",
        "elder_cape" to "elder_sapphire",
        "elder_shield" to "elder_sapphire",
        "elder_signet_ring" to "elder_sapphire",
        "elder_amulet" to "elder_sapphire",
    )

    @Test
    fun `flat preview scales with embedded sapphires like the payout`() = runBlocking {
        val base = 10_000L
        val plain = playerRepo.previewFlatXpGrant("mining", base)
        assertEquals("preview echoes the base amount", base, plain.baseXp)

        playerRepo.updateFlags(playerRepo.getFlags().copy(embeddedSigils = eightSapphires()))
        val boosted = playerRepo.previewFlatXpGrant("mining", base)

        // +40% exacto salvo truncacion entera (±2); en codigo viejo boosted == plain.
        assertTrue(
            "8 sapphires must raise the preview: plain=${plain.finalXp} boosted=${boosted.finalXp}",
            boosted.finalXp > plain.finalXp,
        )
        assertTrue(
            "preview must carry the full +40%: plain=${plain.finalXp} boosted=${boosted.finalXp}",
            abs(boosted.finalXp - plain.finalXp * 1.40) <= 2.0,
        )
    }

    @Test
    fun `flat preview without sapphires is unchanged`() = runBlocking {
        val base = 10_000L
        val preview = playerRepo.previewFlatXpGrant("mining", base)
        assertEquals(base, preview.baseXp)
        // Sin zafiros ni boosts/blessings/prestigio de base, el preview es la base.
        assertEquals(base, preview.finalXp)
    }
}
