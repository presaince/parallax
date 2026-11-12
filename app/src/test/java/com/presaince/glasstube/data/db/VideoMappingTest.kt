/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.db

import com.presaince.glasstube.data.model.StreamState
import com.presaince.glasstube.data.model.toSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMappingTest {

    private fun entity(
        streamState: String = "NORMAL",
    ) = VideoEntity(
        id = "abc123",
        title = "A title",
        channelId = "https://youtube.com/@a",
        channelName = "A",
        durationSec = 600,
        thumbUrl = "https://i.ytimg.com/vi/abc123/hq.jpg",
        publishedAt = 1_700_000_000_000,
        fetchedAt = 1_700_100_000_000,
        viewCount = 1_000,
        likeCount = 77,
        commentCount = 12,
        streamState = streamState,
    )

    @Test
    fun `every column the app cares about survives the mapping`() {
        val video = entity().toVideo()

        assertEquals("abc123", video.id)
        assertEquals("A title", video.title)
        assertEquals("A", video.channelName)
        assertEquals(600L, video.durationSec)
        assertEquals("https://i.ytimg.com/vi/abc123/hq.jpg", video.thumbUrl)
        assertEquals(1_700_000_000_000, video.publishedAt)
        assertEquals(1_700_100_000_000, video.lastSeenAt)
        assertEquals(1_000L, video.viewCount)
        assertEquals(77L, video.likeCount)
        assertEquals(12L, video.commentCount)
    }

    @Test
    fun `the channel column is a url and the model says so`() {
        assertEquals("https://youtube.com/@a", entity().toVideo().channelUrl)
    }

    @Test
    fun `a live stream reads as live`() {
        assertEquals(StreamState.LIVE, entity(streamState = "LIVE").toVideo().streamState)
    }

    @Test
    fun `a value this build does not know is normal, not a crash`() {
        assertEquals(StreamState.NORMAL, entity(streamState = "PREMIERE").toVideo().streamState)
    }

    @Test
    fun `the card carries the live state the old copies dropped`() {
        // The three toSummary copies that preceded this one all left streamState at its default,
        // so a live stream showed no badge in the library, in a collection and in the walk.
        assertEquals(StreamState.LIVE, entity(streamState = "LIVE").toVideo().toSummary().streamState)
    }

    @Test
    fun `the card's url is built from the id`() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc123",
            entity().toVideo().toSummary().url,
        )
    }
}
