package com.fantasyidler.ui.viewmodel

import com.fantasyidler.repository.predictionXpMult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #2056: Elder Sapphire sigil (+5% XP each) must show and pay on mainland.
 * Scope: mainland only — isle stays at base rates by design (#1930, dev decision).
 *
 * run-by-orchestrator (worker sin Gradle por disciplina de lote).
 */
class SapphireXpMainlandTest {

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
    fun `eight sapphires give plus forty percent`() {
        assertEquals(1.40, sigilXpMult(eightSapphires()), 1e-9)
    }

    @Test
    fun `no sapphires passes through`() {
        assertEquals(1.0, sigilXpMult(emptyMap()), 1e-9)
    }

    @Test
    fun `only sapphires count toward xp`() {
        val mixed = mapOf(
            "elder_helm" to "elder_sapphire",
            "elder_platebody" to "elder_sapphire",
            "elder_platelegs" to "elder_sapphire",
            "elder_boots" to "elder_ruby",
            "elder_cape" to "elder_emerald",
            "elder_shield" to "elder_topaz",
        )
        assertEquals(1.15, sigilXpMult(mixed), 1e-9)
    }

    @Test
    fun `batch queue estimate bakes the sigil mult on mainland`() {
        // Base 1000, 2x activo, sin prestigio, 8 zafiros: 1000*2*1*1.40 = 2800.
        assertEquals(
            2800L,
            batchQueueEstimate(1000.0, 2.0, 1.0, sigilXpMult(eightSapphires())),
        )
    }

    @Test
    fun `batch queue estimate without sigils matches the legacy number`() {
        // Sin zafiros el estimado es byte-identico al historico.
        assertEquals(2100L, batchQueueEstimate(1000.0, 2.0, 1.05, 1.0))
    }

    @Test
    fun `isle prediction stays at base rates with sapphires embedded`() {
        val mainland = 2.0 * sigilXpMult(eightSapphires())
        assertEquals(1.0, predictionXpMult(false, true, mainland), 1e-9)
        assertEquals(1.0, predictionXpMult(true, false, mainland), 1e-9)
        assertEquals(mainland, predictionXpMult(false, false, mainland), 1e-9)
    }

    @Test
    fun `stored summary applies prestige and sigils on mainland only`() {
        // 500 base +0% prestigio +40% sigils = 700; isla: base cruda.
        assertEquals(
            700L,
            displayStoredXp(500L, 1.0, sigilXpMult(eightSapphires()), false),
        )
        assertEquals(500L, displayStoredXp(500L, 1.0, sigilXpMult(eightSapphires()), true))
    }
}
