/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTest {

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("4:09", formatDuration(249))
        assertEquals("0:05", formatDuration(5))
    }

    @Test
    fun `formats hours`() {
        assertEquals("1:05:07", formatDuration(3907))
    }

    @Test
    fun `unknown length`() {
        assertEquals("--:--", formatDuration(0))
        assertEquals("--:--", formatDuration(-1))
    }

    @Test
    fun `time ago covers each unit`() {
        val now = 1_800_000_000_000L
        assertEquals("now", timeAgo(now - 30_000, now))
        assertEquals("5m\u00A0ago", timeAgo(now - 5 * 60_000, now))
        assertEquals("3h\u00A0ago", timeAgo(now - 3 * 3_600_000, now))
        assertEquals("12d\u00A0ago", timeAgo(now - 12L * 86_400_000L, now))
        assertEquals("2mo\u00A0ago", timeAgo(now - 70L * 86_400_000L, now))
        assertEquals("1y\u00A0ago", timeAgo(now - 400L * 86_400_000L, now))
    }

    @Test
    fun `a future timestamp does not go negative`() {
        val now = 1_800_000_000_000L
        assertEquals("now", timeAgo(now + 60_000, now))
    }

    @Test
    fun `a seek undo names the seconds as well as where it landed`() {
        // The report: "seek undo should say secs seek too". The distance is the part a person
        // remembers; the position is the part they check.
        assertEquals("Seek +12s", seekUndoLabel(beforeMs = 48_000, afterMs = 60_000))
        assertEquals("Seek \u221245s", seekUndoLabel(beforeMs = 60_000, afterMs = 15_000))
    }

    @Test
    fun `a seek under a second does not round itself into a lie`() {
        assertEquals("Seek 0s", seekUndoLabel(beforeMs = 30_000, afterMs = 30_400))
        assertEquals("+1s", seekDelta(1))
        assertEquals("0s", seekDelta(0))
    }
}
