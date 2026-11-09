/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.discover

import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.db.HistoryEntity
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.model.Channel
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Page
import com.presaince.parallax.data.model.StreamSet
import com.presaince.parallax.data.model.SubtitleTrack
import com.presaince.parallax.data.model.VideoDetail
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
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

    private fun summary(id: String, title: String = id, durationSec: Long = 600) = VideoSummary(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = "channel",
        durationSec = durationSec,
        thumbnailUrl = null,
        publishedAtMs = System.currentTimeMillis(),
        viewCount = 1_000,
    )

    private fun videoEntity(id: String, title: String = id) = VideoEntity(
        id = id,
        title = title,
        channelId = "https://youtube.com/c/a",
        channelName = "channel",
        durationSec = 600,
        thumbUrl = null,
        publishedAt = System.currentTimeMillis(),
        fetchedAt = 0,
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

    private class FakeStore(
        private val videos: List<VideoEntity>,
        private val historyIds: List<String> = emptyList(),
        private val progress: List<String> = emptyList(),
    ) : DiscoverStore {
        override suspend fun recentHistory(limit: Int): List<HistoryEntity> =
            historyIds.map { HistoryEntity(videoId = it, title = it, channelName = "c", openedAt = 1) }

        override suspend fun progressIds(): List<String> = progress

        override suspend fun subscriptions(): List<SubscriptionEntity> = emptyList()

        override suspend fun findVideo(id: String): VideoEntity? = videos.firstOrNull { it.id == id }

        override suspend fun recentVideos(limit: Int): List<VideoEntity> = videos.take(limit)
    }

    private class FakeSource(
        private val related: Map<String, List<VideoSummary>>,
    ) : VideoSource {
        override val id = "fake"

        override suspend fun search(query: String): AppResult<Page<VideoSummary>> =
            AppResult.Ok(Page(items = emptyList()))

        override suspend fun video(id: String): AppResult<VideoDetail> = AppResult.Err("no")

        override suspend fun streams(id: String): AppResult<StreamSet> = AppResult.Err("no")

        override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> =
            AppResult.Err("no")

        override suspend fun related(id: String): AppResult<List<VideoSummary>> =
            AppResult.Ok(related[id].orEmpty())

        override suspend fun channelUploads(
            channelUrl: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = AppResult.Err("no")

        override suspend fun channelInfo(channelUrl: String): AppResult<Channel> = AppResult.Err("no")

        override suspend fun subtitles(id: String): AppResult<SubtitleTrack> = AppResult.Err("no")
    }
}
