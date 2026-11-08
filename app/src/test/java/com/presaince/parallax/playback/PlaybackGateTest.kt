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
}
