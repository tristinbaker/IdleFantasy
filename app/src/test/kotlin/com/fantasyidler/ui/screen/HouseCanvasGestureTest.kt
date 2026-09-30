package com.fantasyidler.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replication for #1953: the house page doesn't scroll in portrait edit
 * mode because the canvas drag detector consumes every gesture — even ones
 * that do nothing (Select-mode drag on empty ground at 1x zoom pans by
 * exactly zero). Those must be handed to the parent vertical scroll.
 */
class HouseCanvasGestureTest {

    @Test
    fun `empty Select drag at 1x hands off to parent scroll`() {
        // The reported bug: this consumed the gesture, starving the scroll.
        assertFalse(shouldCanvasConsumeDrag(hitPiece = false, placing = false, zoomed = false))
    }

    @Test
    fun `furniture move stays owned`() {
        assertTrue(shouldCanvasConsumeDrag(hitPiece = true, placing = false, zoomed = false))
    }

    @Test
    fun `painting stays owned`() {
        assertTrue(shouldCanvasConsumeDrag(hitPiece = false, placing = true, zoomed = false))
    }

    @Test
    fun `zoomed pan stays owned`() {
        assertTrue(shouldCanvasConsumeDrag(hitPiece = false, placing = false, zoomed = true))
    }
}
