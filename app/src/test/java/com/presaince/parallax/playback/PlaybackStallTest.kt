/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stall stopwatch behind the 2026-11-09 "huge queue time" report: a stream that goes quiet
 * raises no error, so the player has to time the silence itself.
 */
class PlaybackStallTest {

    @Test
    fun nothing_being_timed_is_zero() {
        assertEquals(0L, stalledForMs(sinceMs = 0L, nowMs = 5_000L))
    }

    @Test
    fun a_wall_clock_that_stepped_backwards_never_reports_a_negative_wait() {
        assertEquals(0L, stalledForMs(sinceMs = 5_000L, nowMs = 4_000L))
    }

    @Test
    fun the_wait_is_the_difference_between_the_stamp_and_now() {
        assertEquals(21_000L, stalledForMs(sinceMs = 1_000L, nowMs = 22_000L))
    }

    @Test
    fun the_measured_report_wait_reads_as_a_stall() {
        // The second video on the 2026-11-09 report reached READY in 30567 ms.
        assertEquals(30_000L, stalledForMs(sinceMs = 1_000L, nowMs = 31_000L))
    }
}
