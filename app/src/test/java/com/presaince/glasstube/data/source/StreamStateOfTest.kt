/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.data.model.StreamState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType

class StreamStateOfTest {

    @Test
    fun `a scheduled stream typed as live is upcoming, not live`() {
        // Reported 2026-11-12: a premiere wore a LIVE pill and a "now" date, then said
        // "this live stream has not started yet" when opened.
        assertEquals(
            StreamState.UPCOMING,
            streamStateOf(StreamType.LIVE_STREAM, 0, "Scheduled for 12 Nov 2026", "A premiere"),
        )
    }

    @Test
    fun `a premiere is upcoming`() {
        assertEquals(
            StreamState.UPCOMING,
            streamStateOf(StreamType.LIVE_STREAM, 0, "Premieres in 3 hours", "Premiere"),
        )
    }

    @Test
    fun `a stream that is on air is live`() {
        assertEquals(
            StreamState.LIVE,
            streamStateOf(StreamType.LIVE_STREAM, 0, "Started streaming 5 minutes ago", "Live now"),
        )
    }

    @Test
    fun `an unlabelled live stream is live when the listing says so`() {
        assertEquals(
            StreamState.LIVE,
            streamStateOf(StreamType.NONE, 0, "12,431 watching", "A stream"),
        )
    }

    @Test
    fun `an ordinary video is normal`() {
        assertEquals(
            StreamState.NORMAL,
            streamStateOf(StreamType.VIDEO_STREAM, 600, "3 hours ago", "A video"),
        )
    }

    @Test
    fun `an audio live stream is live`() {
        assertEquals(
            StreamState.LIVE,
            streamStateOf(StreamType.AUDIO_LIVE_STREAM, 0, "Started streaming", "Radio"),
        )
    }
}
