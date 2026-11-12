/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import com.presaince.glasstube.data.sponsorblock.Segment

/**
 * The arithmetic the player screen needs, out of the screen. Each of these was a private function
 * inside a three-and-a-half-thousand-line file, which is the same as untested: a composable cannot
 * call itself in a test, and nothing here needs one.
 */

/** The frame 5 seconds into the segment, clamped to stay inside it. */
internal fun segmentFrameMs(segment: Segment): Long {
    val length = (segment.endMs - segment.startMs).coerceAtLeast(0L)
    return if (length > 5_000L) segment.startMs + 5_000L else segment.startMs + length / 2
}

/**
 * The nearest auto-captured frame to [segmentFrameMs]. YouTube exposes only four URL frames
 * (hqdefault + hq1/2/3 at ~25/50/75%), so the nearest quartile is the closest a URL gets to
 * the +5s picture. Coil loads it only when the row scrolls on screen.
 */
internal fun segmentThumbUrl(videoId: String, segment: Segment, durationMs: Long): String {
    if (durationMs <= 0L) return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val fraction = segmentFrameMs(segment).toFloat() / durationMs.toFloat()
    val name = when {
        fraction < 0.125f -> "hqdefault"
        fraction < 0.375f -> "hq1"
        fraction < 0.625f -> "hq2"
        fraction < 0.875f -> "hq3"
        else -> "hqdefault"
    }
    return "https://i.ytimg.com/vi/$videoId/$name.jpg"
}

/**
 * The playhead as a fraction of the duration, clamped so a stale position past the end cannot
 * overflow the bar and an unknown duration draws an empty one.
 */
internal fun scrubProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * How far up the volume rocker is, as a fraction. Split from the [android.media.AudioManager]
 * read so the clamp has a test; a stream that reports zero maximum would otherwise divide by it.
 */
internal fun volumeFraction(current: Int, max: Int): Float =
    (current.toFloat() / max.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
