/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player

import com.presaince.parallax.playback.STALL_LIMIT_MS

/**
 * What the pill under the picture says. One fact, not a chain of guesses: the old line folded a
 * fetch, a first frame, a rebuffer and a dead stream into the word "Queueing", which told a
 * reader nothing and could not be acted on.
 */
enum class PlayerStatus { Hidden, Fetching, WaitingForFirstFrame, Buffering, Stalled, Broken }

/**
 * The pill's one decision. An error outranks everything because it already arms the retry; a
 * stall outranks loading because a stream that went quiet needs help even while something else
 * is still loading. Before the first frame there is no picture to buffer behind, so the two
 * buffering waits are named apart.
 */
fun playerStatus(
    loading: Boolean,
    hasError: Boolean,
    buffering: Boolean,
    firstFrameReady: Boolean,
    stalledForMs: Long,
    stallLimitMs: Long = STALL_LIMIT_MS,
): PlayerStatus = when {
    hasError -> PlayerStatus.Broken
    stalledForMs >= stallLimitMs -> PlayerStatus.Stalled
    loading -> PlayerStatus.Fetching
    buffering && !firstFrameReady -> PlayerStatus.WaitingForFirstFrame
    buffering -> PlayerStatus.Buffering
    else -> PlayerStatus.Hidden
}
