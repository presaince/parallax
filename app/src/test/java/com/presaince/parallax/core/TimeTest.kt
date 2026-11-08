/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

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
        assertEquals("5m ago", timeAgo(now - 5 * 60_000, now))
        assertEquals("3h ago", timeAgo(now - 3 * 3_600_000, now))
        assertEquals("12d ago", timeAgo(now - 12L * 86_400_000L, now))
        assertEquals("2mo ago", timeAgo(now - 70L * 86_400_000L, now))
        assertEquals("1y ago", timeAgo(now - 400L * 86_400_000L, now))
    }

    @Test
    fun `a future timestamp does not go negative`() {
        val now = 1_800_000_000_000L
        assertEquals("now", timeAgo(now + 60_000, now))
    }
}
