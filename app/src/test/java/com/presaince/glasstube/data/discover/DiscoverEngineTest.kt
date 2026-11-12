/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.SubscriptionEntity
import com.presaince.glasstube.data.model.Video
import com.presaince.glasstube.data.model.Channel
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.model.SubtitleTrack
import com.presaince.glasstube.data.model.VideoDetail
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.ChannelFeed
import com.presaince.glasstube.data.source.VideoSource
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The empty pick set had three causes and one message. These pin the seen filter, the seed error,
 * and that the empty message now names the cause.
 */
class DiscoverEngineTest {

    private fun recipe(
        seed: String = "topChannels",
        minDurationSec: Long? = null,
    ) = Recipe(
        name = "Test",
        seed = Seed(kind = "related", seed = seed, count = 3),
        filters = Filters(minDurationSec = minDurationSec),
        score = Score(views = 1.0, recency = 1.0),
        walk = Walk(hops = 1, branch = 5, wildcard = 0.0),
    )

    private fun searchRecipe(query: String = "podcast") = Recipe(
        name = "Search",
        seed = Seed(kind = "search", count = 10, query = query),
        filters = Filters(),
        score = Score(views = 1.0, recency = 1.0),
        walk = Walk(),
    )

    private fun uploadsRecipe() = Recipe(
        name = "Fresh",
        seed = Seed(kind = "uploads", count = 5),
        filters = Filters(maxAgeDays = 7),
        score = Score(views = 1.0, recency = 1.0),
        walk = Walk(),
    )

    private fun summary(
        id: String,
        title: String = id,
        durationSec: Long = 600,
        channelUrl: String? = null,
    ) = VideoSummary(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = "channel",
        durationSec = durationSec,
        thumbnailUrl = null,
        channelUrl = channelUrl,
        publishedAtMs = System.currentTimeMillis(),
        viewCount = 1_000,
    )

    private fun videoEntity(id: String, title: String = id) = Video(
        id = id,
        title = title,
        channelUrl = "https://youtube.com/c/a",
        channelName = "channel",
        durationSec = 600,
        thumbUrl = null,
        publishedAt = System.currentTimeMillis(),
        lastSeenAt = 0,
    )

    @Test
    fun `no seeds is an error, not an empty page`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(related = mapOf("seed" to listOf(summary("a")))),
            store = FakeStore(videos = emptyList()),
            random = Random(1),
        )

        val result = engine.picks(recipe(), limit = 5)

        assertTrue(result is AppResult.Err)
        assertTrue((result as AppResult.Err).message.contains("Nothing to walk from"))
    }

    @Test
    fun `seen videos are never picked`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(related = mapOf("seed" to listOf(summary("seen"), summary("fresh")))),
            store = FakeStore(
                videos = listOf(videoEntity("seed")),
                historyIds = listOf("seen"),
            ),
            random = Random(1),
        )

        val result = engine.picks(recipe(), limit = 5)

        assertTrue(result is AppResult.Ok)
        val picks = (result as AppResult.Ok).value
        assertEquals(listOf("fresh"), picks.map { it.video.id })
    }

    @Test
    fun `when every pick is seen the error says so`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(related = mapOf("seed" to listOf(summary("seen")))),
            store = FakeStore(
                videos = listOf(videoEntity("seed")),
                historyIds = listOf("seen"),
            ),
            random = Random(1),
        )

        val result = engine.picks(recipe(), limit = 5)

        assertTrue(result is AppResult.Err)
        val message = (result as AppResult.Err).message
        assertTrue(message.contains("already seen"))
    }

    @Test
    fun `when filters reject everything the error says so`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(related = mapOf("seed" to listOf(summary("short", durationSec = 30)))),
            store = FakeStore(videos = listOf(videoEntity("seed"))),
            random = Random(1),
        )

        val result = engine.picks(recipe(minDurationSec = 900), limit = 5)

        assertTrue(result is AppResult.Err)
        assertTrue((result as AppResult.Err).message.contains("filters"))
    }

    @Test
    fun `when no related videos come back the error says so`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(related = mapOf("seed" to emptyList())),
            store = FakeStore(videos = listOf(videoEntity("seed"))),
            random = Random(1),
        )

        val result = engine.picks(recipe(), limit = 5)

        assertTrue(result is AppResult.Err)
        assertTrue((result as AppResult.Err).message.contains("No related videos"))
    }

    @Test
    fun `a search recipe makes one search and no related calls`() = runTest {
        val source = FakeSource(
            related = mapOf("a" to listOf(summary("never"))),
            searchItems = listOf(summary("a"), summary("b")),
        )
        val engine = DiscoverEngine(
            source = source,
            store = FakeStore(videos = emptyList()),
            random = Random(1),
        )

        val result = engine.picks(searchRecipe("podcast"), limit = 5)

        assertTrue(result is AppResult.Ok)
        assertEquals(listOf("a", "b"), (result as AppResult.Ok).value.map { it.video.id })
        assertEquals(1, source.searches)
        assertEquals(0, source.relatedCalls)
        assertEquals("Search: \"podcast\"", result.value.first().path.single())
    }

    @Test
    fun `a failed search falls back to the library`() = runTest {
        val engine = DiscoverEngine(
            source = FakeSource(searchFails = true),
            store = FakeStore(
                videos = listOf(
                    videoEntity("podcast-talk", title = "A podcast about maps"),
                    videoEntity("unrelated", title = "A cat"),
                ),
            ),
            random = Random(1),
        )

        val result = engine.picks(searchRecipe("podcast"), limit = 5)

        assertTrue(result is AppResult.Ok)
        val picks = (result as AppResult.Ok).value
        assertEquals(listOf("podcast-talk"), picks.map { it.video.id })
        assertEquals("From your library", picks.first().path.single())
    }

    @Test
    fun `an uploads recipe reads the feed even when subscribed channels are skipped`() = runTest {
        val channel = "https://www.youtube.com/channel/UC1234567890123456789012"
        val engine = DiscoverEngine(
            source = FakeSource(),
            store = FakeStore(
                videos = emptyList(),
                subscriptions = listOf(SubscriptionEntity(url = channel, name = "C", addedAt = 1)),
            ),
            random = Random(1),
            hideSubscribed = { true },
            channelFeed = FakeFeed(mapOf(channel to listOf(summary("new", channelUrl = channel)))),
        )

        val result = engine.picks(uploadsRecipe(), limit = 5)

        assertTrue(result is AppResult.Ok)
        assertEquals(listOf("new"), (result as AppResult.Ok).value.map { it.video.id })
    }

    @Test
    fun `an uploads recipe falls back to the extractor for a handle`() = runTest {
        val channel = "https://www.youtube.com/@someone"
        val engine = DiscoverEngine(
            source = FakeSource(
                uploads = mapOf(channel to listOf(summary("uploaded", channelUrl = channel))),
            ),
            store = FakeStore(
                videos = emptyList(),
                subscriptions = listOf(SubscriptionEntity(url = channel, name = "S", addedAt = 1)),
            ),
            random = Random(1),
            channelFeed = FakeFeed(emptyMap()),
        )

        val result = engine.picks(uploadsRecipe(), limit = 5)

        assertTrue(result is AppResult.Ok)
        assertEquals(listOf("uploaded"), (result as AppResult.Ok).value.map { it.video.id })
    }

    @Test
    fun `an offline uploads recipe uses the stored feed`() = runTest {
        val channel = "https://www.youtube.com/@someone"
        val engine = DiscoverEngine(
            source = FakeSource(),
            store = FakeStore(
                videos = listOf(videoEntity("stored")),
                subscriptions = listOf(SubscriptionEntity(url = channel, name = "S", addedAt = 1)),
            ),
            random = Random(1),
            channelFeed = FakeFeed(emptyMap()),
        )

        val result = engine.picks(uploadsRecipe(), limit = 5)

        assertTrue(result is AppResult.Ok)
        assertEquals(listOf("stored"), (result as AppResult.Ok).value.map { it.video.id })
    }

    private class FakeStore(
        private val videos: List<Video>,
        private val historyIds: List<String> = emptyList(),
        private val progress: List<String> = emptyList(),
        private val subscriptions: List<SubscriptionEntity> = emptyList(),
    ) : DiscoverStore {
        override suspend fun recentHistory(limit: Int): List<HistoryEntity> =
            historyIds.map { HistoryEntity(videoId = it, title = it, channelName = "c", openedAt = 1) }

        override suspend fun progressIds(): List<String> = progress

        override suspend fun subscriptions(): List<SubscriptionEntity> = subscriptions

        override suspend fun findVideo(id: String): Video? = videos.firstOrNull { it.id == id }

        override suspend fun recentVideos(limit: Int): List<Video> = videos.take(limit)
    }

    private class FakeSource(
        private val related: Map<String, List<VideoSummary>> = emptyMap(),
        private val searchItems: List<VideoSummary> = emptyList(),
        private val searchFails: Boolean = false,
        private val uploads: Map<String, List<VideoSummary>> = emptyMap(),
    ) : VideoSource {
        override val id = "fake"
        var searches = 0
        var relatedCalls = 0

        override suspend fun search(query: String): AppResult<Page<VideoSummary>> {
            searches++
            return if (searchFails) {
                AppResult.Err("offline")
            } else {
                AppResult.Ok(Page(items = searchItems))
            }
        }

        override suspend fun video(id: String): AppResult<VideoDetail> = AppResult.Err("no")

        override suspend fun streams(id: String): AppResult<StreamSet> = AppResult.Err("no")

        override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> =
            AppResult.Err("no")

        override suspend fun related(id: String): AppResult<List<VideoSummary>> {
            relatedCalls++
            return AppResult.Ok(related[id].orEmpty())
        }

        override suspend fun channelUploads(
            channelUrl: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = uploads[channelUrl]
            ?.let { AppResult.Ok(Page(items = it)) }
            ?: AppResult.Err("no")

        override suspend fun channelInfo(channelUrl: String): AppResult<Channel> = AppResult.Err("no")

        override suspend fun subtitles(id: String): AppResult<SubtitleTrack> = AppResult.Err("no")
    }

    private class FakeFeed(
        private val byUrl: Map<String, List<VideoSummary>>,
    ) : ChannelFeed {
        override suspend fun latest(channelUrl: String, limit: Int): AppResult<List<VideoSummary>> =
            byUrl[channelUrl]?.let { AppResult.Ok(it.take(limit)) } ?: AppResult.Err("no feed")
    }
}
