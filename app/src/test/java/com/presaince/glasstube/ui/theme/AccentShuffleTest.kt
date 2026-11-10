/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AccentShuffleTest {

    @Test
    fun focus_rolls_on_every_foreground() {
        assertTrue(shouldRollOnForeground(ShuffleCadence.ON_OPEN, null, ""))
        assertTrue(shouldRollOnForeground(ShuffleCadence.ON_OPEN, "2026-11-08", "2026-11-08"))
        assertTrue(shouldRollOnForeground(ShuffleCadence.ON_OPEN, "open-1", "open-1"))
    }

    @Test
    fun off_never_rolls() {
        assertFalse(shouldRollOnForeground(ShuffleCadence.OFF, null, ""))
        assertFalse(shouldRollOnForeground(ShuffleCadence.OFF, "x", "y"))
    }

    @Test
    fun daily_rolls_only_when_the_day_changes() {
        assertTrue(shouldRollOnForeground(ShuffleCadence.DAILY, "2026-11-07", "2026-11-08"))
        assertTrue(shouldRollOnForeground(ShuffleCadence.DAILY, null, "2026-11-08"))
        assertFalse(shouldRollOnForeground(ShuffleCadence.DAILY, "2026-11-08", "2026-11-08"))
    }

    @Test
    fun hourly_rolls_only_when_the_hour_changes() {
        assertTrue(shouldRollOnForeground(ShuffleCadence.HOURLY, "2026-11-08-09", "2026-11-08-10"))
        assertFalse(shouldRollOnForeground(ShuffleCadence.HOURLY, "2026-11-08-10", "2026-11-08-10"))
    }

    @Test
    fun `period keys separate the day and the hour`() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("2026-11-08", periodKeyFor(ShuffleCadence.DAILY, date, LocalDateTime.of(2026, 11, 8, 23, 59)))
        assertEquals(
            "2026-11-08-09",
            periodKeyFor(ShuffleCadence.HOURLY, date, LocalDateTime.of(2026, 11, 8, 9, 59)),
        )
        assertEquals("", periodKeyFor(ShuffleCadence.ON_OPEN, date, LocalDateTime.of(2026, 11, 8, 9, 0)))
        assertEquals("", periodKeyFor(ShuffleCadence.OFF, date, LocalDateTime.of(2026, 11, 8, 9, 0)))
    }
}
