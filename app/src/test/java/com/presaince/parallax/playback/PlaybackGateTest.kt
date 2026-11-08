/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.playback

import androidx.media3.common.Player
import org.junit.Test

class PlaybackGateTest {

    @Test
    fun `sponsor seek runs only in ready state`() {
        assert(shouldSkipSegment(Player.STATE_READY))
        assert(!shouldSkipSegment(Player.STATE_BUFFERING))
        assert(!shouldSkipSegment(Player.STATE_IDLE))
        assert(!shouldSkipSegment(Player.STATE_ENDED))
    }

    @Test
    fun `fast start buffer is balanced`() {
        // Tiny start regressed to a 27s READY, so keep the last known fast values.
        assert(PlaybackController.BUFFER_FOR_PLAYBACK_MS == 1_200)
        assert(PlaybackController.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS == 2_500)
        // Min must stay at or above after-rebuffer, and max must allow growth.
        assert(PlaybackController.MIN_BUFFER_MS >= PlaybackController.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assert(PlaybackController.MAX_BUFFER_MS >= PlaybackController.MIN_BUFFER_MS)
    }
}
