/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPhaseTest {
    @Test
    fun `a fresh play stays fresh until it is ready and playing`() {
        val (phase, write) = advanceHistory(HistoryPhase.Fresh, false, 0, 30_000)
        assertEquals(HistoryPhase.Fresh, phase)
        assertNull(write)
    }

    @Test
    fun `the first played frame opens`() {
        val (phase, write) = advanceHistory(HistoryPhase.Fresh, true, 0, 30_000)
        assertEquals(HistoryPhase.Open, phase)
        assertEquals(HistoryWrite.Open, write)
    }

    @Test
    fun `an open play that has not reached the threshold stays open`() {
        val (phase, write) = advanceHistory(HistoryPhase.Open, true, 10_000, 30_000)
        assertEquals(HistoryPhase.Open, phase)
        assertNull(write)
    }

    @Test
    fun `reaching the threshold stamps the watch`() {
        val (phase, write) = advanceHistory(HistoryPhase.Open, true, 30_000, 30_000)
        assertEquals(HistoryPhase.Watched, phase)
        assertEquals(HistoryWrite.Watch, write)
    }

    @Test
    fun `a watched play never writes again`() {
        val (phase, write) = advanceHistory(HistoryPhase.Watched, true, 60_000, 30_000)
        assertEquals(HistoryPhase.Watched, phase)
        assertNull(write)
    }
}
