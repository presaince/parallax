/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the watchdog tries things in. The bug these pin was not a missing rule but two rules
 * in two files: the recovery budget sat in the controller, the quality ladder sat in the screen,
 * and the screen only counted a picture that was still playing — so a hard stall spent both quiet
 * tries at 720p and the ladder was never asked. The 2026-11-12 log shows it line for line.
 */
class PlaybackTickTest {

    private fun signals(
        hasVideo: Boolean = true,
        hasError: Boolean = false,
        networkDown: Boolean = false,
        recoveryInFlight: Boolean = false,
        attemptsUsed: Int = 0,
        stalledForMs: Long = 0L,
        autoDegrade: Boolean = true,
        choppyWanted: Boolean = false,
        rungAvailable: Boolean = true,
    ) = TickSignals(
        hasVideo = hasVideo,
        hasError = hasError,
        networkDown = networkDown,
        recoveryInFlight = recoveryInFlight,
        attemptsUsed = attemptsUsed,
        stalledForMs = stalledForMs,
        autoDegrade = autoDegrade,
        choppyWanted = choppyWanted,
        rungAvailable = rungAvailable,
    )

    @Test
    fun `nothing loaded is nothing to do`() {
        assertEquals(TickAction.None, tickAction(signals(hasVideo = false, stalledForMs = 60_000)))
    }

    @Test
    fun `a phone with no connection is not a failed stream`() {
        assertEquals(
            TickAction.None,
            tickAction(signals(networkDown = true, stalledForMs = 60_000, attemptsUsed = 2)),
        )
    }

    @Test
    fun `a retry already running is not started again`() {
        assertEquals(
            TickAction.None,
            tickAction(signals(recoveryInFlight = true, stalledForMs = 60_000)),
        )
    }

    @Test
    fun `a healthy stream is left alone`() {
        assertEquals(TickAction.None, tickAction(signals(stalledForMs = 5_000)))
    }

    @Test
    fun `a player error gets a fresh extract and not a smaller picture`() {
        // A rejected URL is not a height problem, so the ladder must not be spent on it.
        assertEquals(
            TickAction.Recover,
            tickAction(signals(hasError = true, choppyWanted = true, rungAvailable = true)),
        )
    }

    @Test
    fun `a short choppy spell under the tolerance is left alone`() {
        // Five seconds of a short runway is a rebuffer ExoPlayer recovers from by itself.
        assertEquals(
            TickAction.None,
            tickAction(signals(stalledForMs = 5_000, choppyWanted = false)),
        )
    }

    @Test
    fun `a full stall steps down at once instead of spending a try`() {
        // The 2026-11-12 log: a 15 s stall, then both quiet tries at 720p, then silence. Twenty
        // seconds of a still picture is conclusive, and the tolerance window cannot hold three
        // stalls twenty seconds apart, so waiting for the count waits for the budget to run out.
        assertEquals(
            TickAction.Degrade,
            tickAction(signals(stalledForMs = 25_000, choppyWanted = false, attemptsUsed = 0)),
        )
    }

    @Test
    fun `a choppy picture past the tolerance steps down before spending a try`() {
        // A picture that keeps playing badly has to earn its step down.
        assertEquals(
            TickAction.Degrade,
            tickAction(signals(stalledForMs = 0, choppyWanted = true, attemptsUsed = 0)),
        )
    }

    @Test
    fun `a second stall still steps down when a rung is left`() {
        // The 2026-11-12 case: one try already spent, still stalling. The old order spent the
        // second try at the same height and gave up at 720p.
        assertEquals(
            TickAction.Degrade,
            tickAction(signals(stalledForMs = 25_000, attemptsUsed = 1)),
        )
    }

    @Test
    fun `with no rung left the quiet retry is what remains`() {
        assertEquals(
            TickAction.Recover,
            tickAction(signals(stalledForMs = 25_000, rungAvailable = false)),
        )
    }

    @Test
    fun `a choppy picture with nowhere to step does not spend a retry`() {
        // A quiet try is for a stream that stopped, not for one that is merely slow, and "gave up"
        // over a picture that is still playing would be a lie.
        assertEquals(
            TickAction.None,
            tickAction(signals(stalledForMs = 0, choppyWanted = true, rungAvailable = false)),
        )
    }

    @Test
    fun `auto degrade off means the old behaviour`() {
        assertEquals(
            TickAction.Recover,
            tickAction(signals(stalledForMs = 25_000, autoDegrade = false)),
        )
    }

    @Test
    fun `out of tries and still stalling gives up instead of buffering in silence`() {
        assertEquals(
            TickAction.GiveUp,
            tickAction(signals(stalledForMs = 25_000, attemptsUsed = 2, rungAvailable = false)),
        )
    }

    @Test
    fun `out of tries with an error also gives up`() {
        assertEquals(TickAction.GiveUp, tickAction(signals(hasError = true, attemptsUsed = 2)))
    }
}
