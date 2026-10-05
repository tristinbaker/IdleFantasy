package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Problema 3: doble vía display. La tarjeta usa la cadena completa
 * (2x*blessing*prestigio) y el payout además sigils (+cape si loot vacío);
 * el resumen solo prestigio y los sheets ignoran prestigio/sigils.
 * El display debe espejar el payout: base * prestigio * sigils (isle: base).
 * El render suma 2x*blessing encima, igual que la tarjeta.
 */
class DisplayParityTest {

    @Test
    fun `summary stores prestige times sigils on mainland`() {
        // base 600, prestigio +10%, 1 zafiro (+5%): 600*1.1*1.05 = 693
        assertEquals(
            693L,
            displayStoredXp(
                baseXp = 600L,
                prestigeMult = 1.1,
                sigilMult = sigilXpMult(mapOf("chest" to "elder_sapphire")),
                isElder = false,
            ),
        )
    }

    @Test
    fun `summary stores raw base on isle`() {
        assertEquals(
            600L,
            displayStoredXp(
                baseXp = 600L,
                prestigeMult = 1.1,
                sigilMult = 1.05,
                isElder = true,
            ),
        )
    }

    @Test
    fun `no sigils means sigil mult is one`() {
        assertEquals(1.0, sigilXpMult(emptyMap()), 0.0)
        assertEquals(1.1, sigilXpMult(mapOf("a" to "elder_sapphire", "b" to "elder_sapphire")), 0.0)
    }
}
