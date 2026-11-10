/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.feed

import com.presaince.glasstube.data.db.ProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure half of the feed screen: progress fractions and the honest updated label. The
 * ViewModel's database wiring is covered by the repository and DAO tests; this pins what the
 * screen shows.
 */
class FeedUiStateTest {

    private fun progress(videoId: String, positionMs: Long, durationMs: Long) = ProgressEntity(
        videoId = videoId,
        title = videoId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 0,
    )

    @Test
    fun `watched fraction is the playhead over the length`() {
        val state = FeedUiState(progress = mapOf("a" to progress("a", 30_000, 60_000)))
        assertEquals(0.5f, state.watchedFraction("a")!!, 0.0001f)
    }

    @Test
    fun `fraction is null without progress or without a length`() {
        assertNull(FeedUiState().watchedFraction("missing"))
        val state = FeedUiState(progress = mapOf("a" to progress("a", 10, 0)))
        assertNull(state.watchedFraction("a"))
    }

    @Test
    fun `fraction is clamped to the card`() {
        val state = FeedUiState(progress = mapOf("a" to progress("a", 90_000, 60_000)))
        assertEquals(1f, state.watchedFraction("a")!!, 0.0001f)
    }

    @Test
    fun `the label is loading only while refreshing an empty feed`() {
        assertEquals("Loading…", FeedUiState(refreshing = true).updatedLabel)
        assertEquals("Not fetched yet", FeedUiState().updatedLabel)
        val now = System.currentTimeMillis()
        val state = FeedUiState(lastFetchedAt = now - 3_600_000, videos = emptyList())
        assertEquals("Updated 1h\u00A0ago", state.updatedLabel)
    }
}
