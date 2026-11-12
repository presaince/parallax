/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

/**
 * What one tick of the player's watchdog decided to do about a stream that is not delivering.
 *
 * This used to be two rules in two places: the recovery budget lived in the controller and the
 * quality ladder lived in the player screen. They could not see each other, so a connection that
 * stalled twice spent both quiet tries at the same height and the picture never stepped down — the
 * log from 2026-11-12 shows `stall recovery 1/2 … target=720p` followed by `2/2 … target=720p`
 * with the ladder's episode count still at two of three. Worse, the ladder counted choppy moments
 * only, and choppy needs a playing picture: the hard stall the reader complained about could never
 * count as an episode at all.
 *
 * One rule, one owner, and the ladder goes first when the fault is the connection's speed. A step
 * down costs the same extractor call as a retry and is the only one of the two that can change the
 * answer.
 */
internal enum class TickAction {
    None,

    /** One rung down, reload at the playhead, keep the quiet tries in hand. */
    Degrade,

    /** Spend one quiet try: fresh signed streams at the same height. */
    Recover,

    /** Nothing left to try. Say so once instead of buffering in silence. */
    GiveUp,
}

internal data class TickSignals(
    val hasVideo: Boolean,
    val hasError: Boolean,
    val networkDown: Boolean,
    val recoveryInFlight: Boolean,
    val attemptsUsed: Int,
    val stalledForMs: Long,
    val autoDegrade: Boolean,
    /** The tolerance rule's verdict: enough short episodes inside the window. */
    val choppyWanted: Boolean,
    /** There is a stop below the one in force. */
    val rungAvailable: Boolean,
)

internal fun tickAction(signals: TickSignals): TickAction {
    if (!signals.hasVideo) return TickAction.None
    // A phone with no connection is not a stream that failed. Spending a try there is how a DNS
    // drop used to leave a video dead with nothing left for the moment the connection returned.
    if (signals.networkDown) return TickAction.None
    if (signals.recoveryInFlight) return TickAction.None
    val stalled = signals.stalledForMs >= STALL_LIMIT_MS
    if (!stalled && !signals.hasError && !signals.choppyWanted) return TickAction.None
    // A rejected URL is not a height problem, so an error skips the ladder and goes straight to a
    // fresh extract.
    if (signals.autoDegrade && !signals.hasError && signals.rungAvailable) {
        // A full stall is conclusive on its own. The tolerance window cannot hold three of them —
        // they arrive twenty seconds apart — so waiting for the count is waiting for the quiet
        // tries to run out, which is exactly how the 2026-11-12 log ends in silence at 720p. A
        // picture that keeps playing badly has to earn its step down instead.
        if (stalled || signals.choppyWanted) return TickAction.Degrade
    }
    // A choppy picture that cannot step down has nothing left to spend: a quiet try is for a
    // stream that stopped, not for one that is merely slow, and "gave up" over a picture that is
    // still playing would be a lie.
    if (!stalled && !signals.hasError) return TickAction.None
    // The budget rule itself is unchanged and stays where it was tested: this only decides what
    // happens when it says no.
    if (
        shouldRecover(
            hasVideo = signals.hasVideo,
            hasError = signals.hasError,
            stalledForMs = signals.stalledForMs,
            attemptsUsed = signals.attemptsUsed,
            networkDown = signals.networkDown,
        )
    ) {
        return TickAction.Recover
    }
    return TickAction.GiveUp
}
