/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The press that did nothing. Reports 210828 and 210851 ("it won't give error toast or play or
 * log … restart app fixed it") are an IDLE player with its media still loaded: `play()` on it is
 * ignored, so the press produced no sound, no message and no log line. These pin the four cases
 * the button has to tell apart.
 */
class PlayActionTest {

    @Test
    fun an_idle_player_with_a_video_is_prepared_again() {
        assertEquals(
            PlayAction.Reprepare,
            playAction(
                playbackState = Player.STATE_IDLE,
                hasMedia = true,
                hasError = false,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun an_idle_player_with_nothing_loaded_does_nothing() {
        assertEquals(
            PlayAction.Nothing,
            playAction(
                playbackState = Player.STATE_IDLE,
                hasMedia = false,
                hasError = false,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun a_failed_stream_is_prepared_again() {
        assertEquals(
            PlayAction.Reprepare,
            playAction(
                playbackState = Player.STATE_IDLE,
                hasMedia = true,
                hasError = true,
                isPlaying = false,
            ),
        )
        // The error can survive a state the player has already left.
        assertEquals(
            PlayAction.Reprepare,
            playAction(
                playbackState = Player.STATE_READY,
                hasMedia = true,
                hasError = true,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun the_end_of_a_video_starts_it_over() {
        assertEquals(
            PlayAction.Restart,
            playAction(
                playbackState = Player.STATE_ENDED,
                hasMedia = true,
                hasError = false,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun playing_pauses_and_a_ready_player_starts() {
        assertEquals(
            PlayAction.Pause,
            playAction(
                playbackState = Player.STATE_READY,
                hasMedia = true,
                hasError = false,
                isPlaying = true,
            ),
        )
        assertEquals(
            PlayAction.Play,
            playAction(
                playbackState = Player.STATE_READY,
                hasMedia = true,
                hasError = false,
                isPlaying = false,
            ),
        )
    }
}
