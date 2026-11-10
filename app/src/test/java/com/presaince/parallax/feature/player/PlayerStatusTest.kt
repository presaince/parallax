/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pill used to say "Queueing" for a fetch, a first frame, a rebuffer and a dead stream alike,
 * so a reported stall could not be read off the screen. These pin which fact wins.
 */
class PlayerStatusTest {

    @Test
    fun an_error_says_broken_even_while_it_is_still_waiting() {
        assertEquals(
            PlayerStatus.Broken,
            playerStatus(
                loading = true,
                hasError = true,
                buffering = true,
                firstFrameReady = false,
                stalledForMs = 60_000,
            ),
        )
    }

    @Test
    fun a_stall_outranks_a_load_that_is_still_running() {
        assertEquals(
            PlayerStatus.Stalled,
            playerStatus(
                loading = true,
                hasError = false,
                buffering = true,
                firstFrameReady = false,
                stalledForMs = 20_000,
            ),
        )
    }

    @Test
    fun one_millisecond_below_the_limit_is_not_yet_a_stall() {
        assertEquals(
            PlayerStatus.WaitingForFirstFrame,
            playerStatus(
                loading = false,
                hasError = false,
                buffering = true,
                firstFrameReady = false,
                stalledForMs = 19_999,
            ),
        )
    }

    @Test
    fun fetching_streams_is_named_as_fetching_not_as_buffering() {
        assertEquals(
            PlayerStatus.Fetching,
            playerStatus(
                loading = true,
                hasError = false,
                buffering = false,
                firstFrameReady = false,
                stalledForMs = 0,
            ),
        )
    }

    @Test
    fun buffering_before_the_first_frame_reads_as_loading_the_video() {
        assertEquals(
            PlayerStatus.WaitingForFirstFrame,
            playerStatus(
                loading = false,
                hasError = false,
                buffering = true,
                firstFrameReady = false,
                stalledForMs = 0,
            ),
        )
    }

    @Test
    fun buffering_after_the_first_frame_reads_as_buffering() {
        assertEquals(
            PlayerStatus.Buffering,
            playerStatus(
                loading = false,
                hasError = false,
                buffering = true,
                firstFrameReady = true,
                stalledForMs = 0,
            ),
        )
    }

    @Test
    fun a_settled_player_shows_nothing() {
        assertEquals(
            PlayerStatus.Hidden,
            playerStatus(
                loading = false,
                hasError = false,
                buffering = false,
                firstFrameReady = true,
                stalledForMs = 0,
            ),
        )
    }

    @Test
    fun a_lost_network_says_so_instead_of_calling_the_stream_broken() {
        // 110003: the phone dropped DNS mid-video and the screen blamed the stream.
        assertEquals(
            PlayerStatus.Offline,
            playerStatus(
                loading = false,
                hasError = true,
                buffering = false,
                firstFrameReady = true,
                stalledForMs = 0,
                networkDown = true,
            ),
        )
    }
}
