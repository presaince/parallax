/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.feed

import com.presaince.glasstube.data.db.HiddenRow
import com.presaince.glasstube.data.db.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenTest {

    private fun video(id: String, channel: String? = "https://youtube.com/@a", state: String = "NORMAL") =
        VideoEntity(
            id = id,
            title = "Video $id",
            channelId = channel,
            channelName = "A",
            durationSec = 300,
            thumbUrl = null,
            publishedAt = 1L,
            fetchedAt = 1L,
            streamState = state,
        )

    @Test
    fun `nothing hidden leaves the feed alone`() {
        val feed = listOf(video("one"), video("two"))
        assertEquals(feed, visibleVideos(feed, emptyList()))
    }

    @Test
    fun `a hidden video leaves the feed and only that one`() {
        val feed = listOf(video("one"), video("two"))
        val hidden = listOf(hiddenVideo(feed[0], "hidden by you"))
        assertEquals(listOf("two"), visibleVideos(feed, hidden).map { it.id })
    }

    @Test
    fun `a hidden channel's live stream leaves, its videos stay`() {
        val live = video("live1", "https://youtube.com/@perm", state = "LIVE")
        val upload = video("upload1", "https://youtube.com/@perm")
        val other = video("live2", "https://youtube.com/@other", state = "LIVE")
        val hidden = listOf(hiddenLiveChannel("https://youtube.com/@perm", "Perm", "live streams hidden by you"))
        assertEquals(
            listOf("upload1", "live2"),
            visibleVideos(listOf(live, upload, other), hidden).map { it.id },
        )
    }

    @Test
    fun `a hidden video that is also live is not rescued by the channel rule`() {
        val live = video("live1", "https://youtube.com/@perm", state = "LIVE")
        val hidden = listOf(
            hiddenVideo(live, "hidden by you"),
            hiddenLiveChannel("https://youtube.com/@nobody", "Nobody", "live streams hidden by you"),
        )
        assertEquals(emptyList<String>(), visibleVideos(listOf(live), hidden).map { it.id })
    }

    @Test
    fun `the two kinds of row are keyed apart`() {
        val live = video("live1", "https://youtube.com/@perm", state = "LIVE")
        val rows: List<HiddenRow> = listOf(
            hiddenVideo(live, "hidden by you"),
            hiddenLiveChannel("https://youtube.com/@perm", "Perm", "live streams hidden by you"),
        )
        // Distinct keys, so unhiding one leaves the other alone.
        assertEquals(2, rows.map { it.key }.toSet().size)
        assertEquals("live:https://youtube.com/@perm", liveChannelKey("https://youtube.com/@perm"))
    }

    @Test
    fun `a hidden row keeps the words it needs to be listed later`() {
        val live = video("live1", "https://youtube.com/@perm", state = "LIVE")
        val row = hiddenVideo(live, "private or removed")
        assertEquals("Video live1", row.title)
        assertEquals("private or removed", row.reason)
        assertEquals(false, row.liveOnly)
        assertEquals(live.id, row.videoId)
    }
}
