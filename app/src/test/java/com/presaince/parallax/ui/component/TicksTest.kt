package com.presaince.parallax.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicksTest {

    @Test
    fun seekCancel_fires_on_returning_to_zero_and_on_jumping_over_it() {
        assertTrue(seekCancel(3_000L, 0L))
        assertTrue(seekCancel(-3_000L, 0L))
        // A seek moves in jumps, so crossing zero without landing on it still means cancel.
        assertTrue(seekCancel(3_000L, -400L))
        assertTrue(seekCancel(-3_000L, 400L))
    }

    @Test
    fun seekCancel_silent_when_away_from_zero_or_still() {
        assertFalse(seekCancel(0L, 0L))
        assertFalse(seekCancel(0L, 3_000L))
        assertFalse(seekCancel(3_000L, 1_000L))
        assertFalse(seekCancel(0L, -1_000L))
        assertFalse(seekCancel(-3_000L, -1_000L))
        assertFalse(seekCancel(1_000L, 1_000L))
    }

    @Test
    fun speedHomeCross_fires_landing_on_or_jumping_over_one() {
        assertTrue(speedHomeCross(9, 10))
        assertTrue(speedHomeCross(11, 10))
        assertTrue(speedHomeCross(9, 11))
        assertTrue(speedHomeCross(11, 9))
    }

    @Test
    fun speedHomeCross_silent_when_not_crossing_or_already_home() {
        assertFalse(speedHomeCross(10, 11))
        assertFalse(speedHomeCross(10, 9))
        assertFalse(speedHomeCross(8, 9))
        assertFalse(speedHomeCross(12, 11))
        assertFalse(speedHomeCross(10, 10))
    }

    @Test
    fun crossedMilestone_only_when_a_mark_lies_between() {
        val marks = listOf(10_000L, 20_000L, 30_000L)
        assertTrue(crossedMilestone(5_000L, 15_000L, marks))
        assertTrue(crossedMilestone(15_000L, 5_000L, marks))
        assertFalse(crossedMilestone(11_000L, 19_000L, marks))
        assertFalse(crossedMilestone(10_000L, 10_000L, marks))
        assertFalse(crossedMilestone(5_000L, 9_000L, marks))
    }

    @Test
    fun clampHit_only_on_arriving_at_an_end() {
        assertTrue(clampHit(0.9f, 1.0f, 0.01f, 1.0f))
        assertTrue(clampHit(0.1f, 0.01f, 0.01f, 1.0f))
        assertFalse(clampHit(1.0f, 1.0f, 0.01f, 1.0f))
        assertFalse(clampHit(0.5f, 0.6f, 0.01f, 1.0f))
        assertFalse(clampHit(0.5f, 0.5f, 0.01f, 1.0f))
    }

    @Test
    fun hapticsAllowed_needs_both_the_app_and_the_system() {
        assertEquals(true, hapticsAllowed(true, true))
        assertEquals(false, hapticsAllowed(false, true))
        assertEquals(false, hapticsAllowed(true, false))
        assertEquals(false, hapticsAllowed(false, false))
    }
}
