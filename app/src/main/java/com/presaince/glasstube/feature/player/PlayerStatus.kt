/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import com.presaince.glasstube.playback.STALL_LIMIT_MS

/**
 * What the pill under the picture says. One fact, not a chain of guesses: the old line folded a
 * fetch, a first frame, a rebuffer and a dead stream into the word "Queueing", which told a
 * reader nothing and could not be acted on.
 */
enum class PlayerStatus { Hidden, Fetching, WaitingForFirstFrame, Buffering, Stalled, Broken, Offline }

/**
 * The pill's one decision. A lost network outranks a broken stream, because there is nothing to
 * fix on this side of the phone. An error outranks everything else because it already arms the
 * retry; a stall outranks loading because a stream that went quiet needs help even while
 * something else is still loading. Before the first frame there is no picture to buffer behind,
 * so the two buffering waits are named apart.
 */
fun playerStatus(
    loading: Boolean,
    hasError: Boolean,
    buffering: Boolean,
    firstFrameReady: Boolean,
    stalledForMs: Long,
    stallLimitMs: Long = STALL_LIMIT_MS,
    networkDown: Boolean = false,
): PlayerStatus = when {
    networkDown -> PlayerStatus.Offline
    hasError -> PlayerStatus.Broken
    stalledForMs >= stallLimitMs -> PlayerStatus.Stalled
    loading -> PlayerStatus.Fetching
    buffering && !firstFrameReady -> PlayerStatus.WaitingForFirstFrame
    buffering -> PlayerStatus.Buffering
    else -> PlayerStatus.Hidden
}
