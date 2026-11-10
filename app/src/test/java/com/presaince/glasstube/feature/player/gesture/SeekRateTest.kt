/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared seek rate: the base at the start, eight times it once the finger has travelled. */
class SeekRateTest {

    @Test
    fun `the first tenth moves exactly the base`() {
        assertEquals(5_000L, SeekRate.movedMs(0.10f, 0f, 5f))
    }

    @Test
    fun `the ramp reaches eight times at forty percent of the width`() {
        assertEquals(8f, SeekRate.ramp(SeekRate.FULL_RAMP_TRAVEL))
        assertEquals(8f, SeekRate.ramp(1f))
        assertEquals(1f, SeekRate.ramp(0f))
    }

    @Test
    fun `a negative fraction mirrors the move`() {
        assertEquals(-5_000L, SeekRate.movedMs(-0.10f, 0f, 5f))
    }

    @Test
    fun `a ten second base doubles the move`() {
        assertEquals(10_000L, SeekRate.movedMs(0.10f, 0f, 10f))
    }
}
