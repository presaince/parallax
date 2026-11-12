/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The speed pill fills against the reader's own presets. A fixed 0.25..5.0 scale left it a third
 * full at the 2.0x the default list stops at, so a full pill was a speed they had switched off.
 */
class SpeedFillTest {

    /** The default presets: 0.8x to 2.0x, in tenths 8..20. */
    private val defaults = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

    @Test
    fun `the top preset is a full pill`() {
        assertEquals(1f, speedFill(2.0f, defaults), 0.0001f)
    }

    @Test
    fun `the bottom preset is an empty pill`() {
        assertEquals(0f, speedFill(0.8f, defaults), 0.0001f)
    }

    @Test
    fun `1x sits a sixth of the way up the default list`() {
        assertEquals(2f / 12f, speedFill(1.0f, defaults), 0.0001f)
    }

    @Test
    fun `a list that reaches 5x fills at 2x only a third`() {
        // Fifth of the range, not two fifths: the reading is a place in their list.
        assertEquals(15f / 45f, speedFill(2.0f, listOf(0.5f, 2.0f, 5.0f)), 0.0001f)
    }

    @Test
    fun `a speed past either end of the presets clamps`() {
        assertEquals(1f, speedFill(9.0f, defaults), 0.0001f)
        assertEquals(0f, speedFill(0.1f, defaults), 0.0001f)
    }

    @Test
    fun `one preset left is still a full pill`() {
        assertEquals(1f, speedFill(1.5f, listOf(1.5f)), 0.0001f)
    }

    @Test
    fun `the range is the presets in tenths`() {
        assertEquals(8..20, speedRangeTenths(defaults))
    }

    @Test
    fun `the range stays inside the player's limits`() {
        assertEquals(3..50, speedRangeTenths(listOf(0.1f, 9.0f)))
        assertEquals(3..50, speedRangeTenths(emptyList()))
    }
}
