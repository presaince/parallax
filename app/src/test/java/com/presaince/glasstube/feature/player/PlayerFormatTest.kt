/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import com.presaince.glasstube.data.sponsorblock.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's arithmetic, out of the 3,500-line screen. These were private functions inside a
 * composable file, which in practice meant they were checked by looking at them.
 */
class PlayerFormatTest {

    private fun segment(startMs: Long, endMs: Long) = Segment(startMs, endMs, "sponsor")

    @Test
    fun `a long segment shows the frame five seconds in`() {
        assertEquals(15_000L, segmentFrameMs(segment(10_000, 40_000)))
    }

    @Test
    fun `a short segment shows its middle`() {
        assertEquals(11_000L, segmentFrameMs(segment(10_000, 12_000)))
    }

    @Test
    fun `a segment of no length does not go backwards`() {
        assertEquals(10_000L, segmentFrameMs(segment(10_000, 9_000)))
    }

    @Test
    fun `the thumbnail is the nearest quartile`() {
        val video = "abc"
        // A frame at 10% of a 10-minute video is the opening still.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            segmentThumbUrl(video, segment(30_000, 90_000), 600_000),
        )
        // 25%: hq1.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq1.jpg",
            segmentThumbUrl(video, segment(100_000, 200_000), 600_000),
        )
        // 50%: hq2.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq2.jpg",
            segmentThumbUrl(video, segment(295_000, 400_000), 600_000),
        )
        // 75%: hq3.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq3.jpg",
            segmentThumbUrl(video, segment(445_000, 550_000), 600_000),
        )
    }

    @Test
    fun `an unknown duration falls back to the opening still`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            segmentThumbUrl("abc", segment(10_000, 40_000), 0),
        )
    }

    @Test
    fun `the scrub fraction is the playhead over the length`() {
        assertEquals(0.25f, scrubProgress(15_000, 60_000), 0.0001f)
    }

    @Test
    fun `a playhead past the end cannot overflow the bar`() {
        assertEquals(1f, scrubProgress(90_000, 60_000), 0.0001f)
    }

    @Test
    fun `an unknown length draws an empty bar`() {
        assertEquals(0f, scrubProgress(15_000, 0), 0.0001f)
    }

    @Test
    fun `volume is the step over the maximum`() {
        assertEquals(0.5f, volumeFraction(7, 14), 0.0001f)
        assertEquals(1f, volumeFraction(14, 14), 0.0001f)
        assertEquals(0f, volumeFraction(0, 14), 0.0001f)
    }

    @Test
    fun `a stream that reports no maximum does not divide by zero`() {
        assertEquals(0f, volumeFraction(0, 0), 0.0001f)
        assertEquals(1f, volumeFraction(5, 0), 0.0001f)
    }
}
