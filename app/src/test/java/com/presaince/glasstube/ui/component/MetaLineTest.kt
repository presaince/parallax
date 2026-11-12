/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import com.presaince.glasstube.data.model.StreamState
import com.presaince.glasstube.data.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaLineTest {

    private val now = 1_800_000_000_000L

    private fun summary(
        state: StreamState = StreamState.NORMAL,
        publishedAtMs: Long? = now - 3 * 3_600_000,
    ) = VideoSummary(
        id = "abc123",
        url = "https://www.youtube.com/watch?v=abc123",
        title = "A title",
        channelName = "A",
        durationSec = 600,
        thumbnailUrl = null,
        publishedAtMs = publishedAtMs,
        streamState = state,
    )

    @Test
    fun `a scheduled stream counts down instead of saying now`() {
        val line = metaFor(
            summary(state = StreamState.UPCOMING, publishedAtMs = now + 3_600_000),
            watches = null,
            style = CardStyle(),
            now = now,
        )
        assertEquals("live\u00A0in\u00A01h", line.text)
    }

    @Test
    fun `the countdown is red and lit`() {
        val line = metaFor(
            summary(state = StreamState.UPCOMING, publishedAtMs = now + 3_600_000),
            watches = null,
            style = CardStyle(),
            now = now,
        )
        val span = line.spanStyles.first()
        assertEquals(LIVE_RED, span.item.color)
        assertTrue("the glow is a shadow of the same red", span.item.shadow?.color == LIVE_RED)
    }

    @Test
    fun `an ordinary video still says how long ago`() {
        val line = metaFor(summary(), watches = null, style = CardStyle(), now = now)
        assertEquals("3h\u00A0ago", line.text)
    }

    @Test
    fun `facts follow the date with their separators`() {
        val line = metaFor(
            summary().copy(viewCount = 1_000, likeCount = 12),
            watches = null,
            style = CardStyle(),
            now = now,
        )
        assertEquals("3h\u00A0ago\u00A0·\u00A01.0K\u00A0views\u00A0·\u00A012\u00A0likes", line.text)
    }
}
