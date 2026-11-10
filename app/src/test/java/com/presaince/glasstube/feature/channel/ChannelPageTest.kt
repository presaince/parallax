/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.channel

import com.presaince.glasstube.core.SortOption
import com.presaince.glasstube.core.SortState
import com.presaince.glasstube.data.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A channel page offers only the tabs its channel has. The extractor's list is the authority: an
 * empty Shorts tab on a channel with no Shorts reads as a broken screen, not as an empty one.
 */
class ChannelPageTest {

    @Test
    fun `only the channel's own tabs, plus the two local ones`() {
        assertEquals(
            listOf(ChannelTab.VIDEOS, ChannelTab.SHORTS, ChannelTab.WATCHED, ChannelTab.ABOUT),
            channelTabsFor(listOf("videos", "shorts")),
        )
        assertEquals(
            listOf(ChannelTab.VIDEOS, ChannelTab.SHORTS, ChannelTab.LIVE, ChannelTab.WATCHED, ChannelTab.ABOUT),
            channelTabsFor(listOf("videos", "shorts", "livestreams")),
        )
    }

    @Test
    fun `an unknown filter adds no chip`() {
        assertEquals(
            listOf(ChannelTab.WATCHED, ChannelTab.ABOUT),
            channelTabsFor(listOf("playlists", "about")),
        )
    }

    private fun video(id: String, views: Long?, at: Long?, duration: Long) = VideoSummary(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = id,
        channelName = "c",
        durationSec = duration,
        thumbnailUrl = null,
        viewCount = views,
        publishedAtMs = at,
    )

    private val videos = listOf(
        video("old", views = 900, at = 1_000, duration = 60),
        video("new", views = 10, at = 3_000, duration = 600),
        video("mid", views = 500, at = 2_000, duration = 30),
    )

    @Test
    fun `the videos tab sorts by date, views or length`() {
        assertEquals(
            listOf("new", "mid", "old"),
            ChannelUiState(items = videos, sort = SortState(SortOption.DATE, ascending = false))
                .sorted.map { it.id },
        )
        assertEquals(
            listOf("old", "mid", "new"),
            ChannelUiState(items = videos, sort = SortState(SortOption.VIEWS, ascending = false))
                .sorted.map { it.id },
        )
        assertEquals(
            listOf("mid", "old", "new"),
            ChannelUiState(items = videos, sort = SortState(SortOption.DURATION, ascending = true))
                .sorted.map { it.id },
        )
    }
}
