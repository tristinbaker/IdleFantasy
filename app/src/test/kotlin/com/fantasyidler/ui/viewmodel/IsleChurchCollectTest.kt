package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fix #2057: Elder Isle church bonus XP applies to reward (run-by-orchestrator).
 *
 * NO ejecutar en esta sesion (laptop chica, disciplina de lote).
 * El orquestador corre en Fase 3: `./gradlew :app:testDebugUnitTest
 * --tests "com.fantasyidler.ui.viewmodel.IsleChurchCollectTest"`.
 *
 * R0 RED esperado (bug sin fix): `collectBlessingXpMult(false, true, 1.55f)`
 * devolvia 1.55 (aplica church a isle) en vez de 1.0. El display
 * `collectSkillDisplayXp(1000, isle=1000, boost=1, church=1.55)` devolvia
 * (1550, 550) en vez de (1000, 0).
 */
class IsleChurchCollectTest {

    // Church +55% como en el reporte (buff XP de church activo).
    private val church55 = 1.55f

    @Test
    fun `isle collect runs at base church`() {
        // Buggy: devolvia church55 (aplica bonus a isle).
        assertEquals(1.0f, collectBlessingXpMult(isIronman = false, isIsle = true, churchMult = church55))
    }

    @Test
    fun `isle without church stays base`() {
        assertEquals(1.0f, collectBlessingXpMult(isIronman = false, isIsle = true, churchMult = 1.0f))
    }

    @Test
    fun `mainland collect keeps church bonus`() {
        assertEquals(church55, collectBlessingXpMult(isIronman = false, isIsle = false, churchMult = church55))
    }

    @Test
    fun `ironman stays base on both provenances`() {
        assertEquals(1.0f, collectBlessingXpMult(isIronman = true, isIsle = false, churchMult = church55))
        assertEquals(1.0f, collectBlessingXpMult(isIronman = true, isIsle = true, churchMult = church55))
    }

    @Test
    fun `pure isle display shows base with zero church bonus`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 1000L, boostFactor = 1L, churchMult = church55)
        assertEquals(1000L, display)
        assertEquals(0L, bonus)
    }

    @Test
    fun `pure mainland display keeps church bonus`() {
        // OJO Float: 1.55f.toDouble() = 1.54999995…; 1000 * eso trunca a 1549.
        // La fórmula vieja daba exactamente lo mismo (Fase 3: mainland intacto bit a bit).
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 0L, boostFactor = 1L, churchMult = church55)
        assertEquals(1549L, display)
        assertEquals(549L, bonus)
    }

    @Test
    fun `mixed batch splits church only to mainland`() {
        // Misma skill con XP mainland + isle en un solo cobro (viaje sin cobrar).
        // Misma salvedad Float que arriba: mainland 1000 * 1.55f trunca a 1549 (igual que la fórmula vieja).
        val (display, bonus) = collectSkillDisplayXp(totalXp = 2000L, isleXp = 1000L, boostFactor = 1L, churchMult = church55)
        assertEquals(2549L, display)
        assertEquals(549L, bonus)
    }

    @Test
    fun `isle keeps boost but gates church`() {
        // Boost fuera de alcance: se declara, no se toca. Isle conserva boost, pierde church.
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 1000L, boostFactor = 2L, churchMult = church55)
        assertEquals(2000L, display)
        assertEquals(0L, bonus)
    }

    @Test
    fun `isle clamps above total`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 5000L, boostFactor = 1L, churchMult = church55)
        assertEquals(1000L, display)
        assertEquals(0L, bonus)
    }
}
