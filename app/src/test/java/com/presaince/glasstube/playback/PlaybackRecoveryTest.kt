/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two quiet tries used to live in the player screen, so a stall on the mini-player or with
 * the activity gone was never retried, and the budget reset every time the screen was opened.
 * These pin the rule now that the controller owns it.
 */
class PlaybackRecoveryTest {

    @Test
    fun nothing_loaded_means_nothing_to_retry() {
        assertFalse(shouldRecover(hasVideo = false, hasError = false, stalledForMs = 60_000, attemptsUsed = 0))
        assertFalse(shouldRecover(hasVideo = false, hasError = true, stalledForMs = 0, attemptsUsed = 0))
    }

    @Test
    fun a_player_error_spends_a_try_at_once() {
        assertTrue(shouldRecover(hasVideo = true, hasError = true, stalledForMs = 0, attemptsUsed = 0))
    }

    @Test
    fun a_stall_has_to_reach_the_limit_first() {
        assertFalse(shouldRecover(hasVideo = true, hasError = false, stalledForMs = 19_999, attemptsUsed = 0))
        assertTrue(
            shouldRecover(hasVideo = true, hasError = false, stalledForMs = STALL_LIMIT_MS, attemptsUsed = 0),
        )
    }

    @Test
    fun the_budget_is_two_and_then_the_pill_is_the_way_back() {
        assertTrue(shouldRecover(hasVideo = true, hasError = false, stalledForMs = 60_000, attemptsUsed = 1))
        assertFalse(shouldRecover(hasVideo = true, hasError = false, stalledForMs = 60_000, attemptsUsed = 2))
    }

    @Test
    fun a_short_wait_is_not_yet_a_stall() {
        // Buffering after a seek is normal; only a wait past the limit is the error that never
        // arrives, and only then does the player throw away its buffer to try again.
        assertFalse(shouldRecover(hasVideo = true, hasError = false, stalledForMs = 3_000, attemptsUsed = 0))
    }
}
