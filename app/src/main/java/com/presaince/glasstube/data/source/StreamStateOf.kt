/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.data.model.StreamState
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Whether a listing entry is live, still to come, or an ordinary video.
 *
 * The order is the whole rule. The extractor types a scheduled stream as [StreamType.LIVE_STREAM]
 * exactly as it types one that is on air, so asking the type first made every premiere wear a LIVE
 * pill, a "now" timestamp from its future start, and then fail on open with "not started yet".
 * The date is the only thing that tells them apart, so it is read first.
 *
 * Pure, and takes the date as text, so the rule can be tested without a network.
 */
internal fun streamStateOf(
    streamType: StreamType,
    durationSec: Long,
    textualDate: String?,
    title: String,
): StreamState {
    val date = textualDate.orEmpty()
    if (durationSec <= 0 && UPCOMING_HINT.containsMatchIn(date)) return StreamState.UPCOMING
    if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
        return StreamState.LIVE
    }
    if (durationSec <= 0 && LIVE_HINT.containsMatchIn("$date $title")) return StreamState.LIVE
    return StreamState.NORMAL
}

/** How YouTube words a video that has not aired yet. */
internal val UPCOMING_HINT = Regex("(?i)premier|schedul|upcoming|waiting|starting")

/** How a listing marks something that is live right now. */
internal val LIVE_HINT = Regex("(?i)watching|live now|\\blive\\b|24/7|radio")
