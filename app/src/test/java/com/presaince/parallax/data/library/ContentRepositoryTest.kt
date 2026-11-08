/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.library

import androidx.room.Room
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.db.ParallaxDb
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
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A refresh must not erase what was learned by opening a video. The listing carries no like or
 * comment counts, so the repository carries the stored ones forward across the REPLACE upsert.
 */
@RunWith(RobolectricTestRunner::class)
class ContentRepositoryTest {

    private lateinit var db: ParallaxDb
    private lateinit var repo: ContentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ParallaxDb::class.java,
        ).allowMainThreadQueries().build()
        repo = ContentRepository(
            source = FakeSource(),
            db = db,
            log = AppLog(File(requireNotNull(System.getProperty("java.io.tmpdir")))),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a refresh preserves learned like and comment counts`() = runTest {
        db.subscriptions().insertAll(
            listOf(SubscriptionEntity(url = "https://youtube.com/c/a", name = "A", addedAt = 0)),
        )
        db.videos().upsertAll(
            listOf(
                VideoEntity(
                    id = "vid1",
                    title = "Old title",
                    channelId = "https://youtube.com/c/a",
                    channelName = "A",
                    durationSec = 60,
                    thumbUrl = null,
                    publishedAt = 100,
                    fetchedAt = 0,
                ),
            ),
        )
        db.videos().setLikeCount("vid1", 77)
        db.videos().setCommentCount("vid1", 12)

        val result = repo.refreshFeed(now = 1_000L)

        assertEquals(1, result.channels)
        assertEquals(1, result.videos)
        val stored = db.videos().find("vid1")
        assertEquals("New title", stored?.title)
        assertEquals(77L, stored?.likeCount)
        assertEquals(12L, stored?.commentCount)
    }

    @Test
    fun `a fresh video has no learned counts`() = runTest {
        db.subscriptions().insertAll(
            listOf(SubscriptionEntity(url = "https://youtube.com/c/a", name = "A", addedAt = 0)),
        )

        repo.refreshFeed(now = 1_000L)

        val stored = db.videos().find("vid1")
        assertEquals(null, stored?.likeCount)
        assertEquals(null, stored?.commentCount)
    }

    private class FakeSource : VideoSource {
        override val id = "fake"

        override suspend fun search(query: String): AppResult<Page<VideoSummary>> =
            AppResult.Ok(Page(items = emptyList()))

        override suspend fun video(id: String): AppResult<VideoDetail> =
            AppResult.Err("not implemented")

        override suspend fun streams(id: String): AppResult<StreamSet> =
            AppResult.Err("not implemented")

        override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> =
            AppResult.Err("not implemented")

        override suspend fun related(id: String): AppResult<List<VideoSummary>> =
            AppResult.Ok(emptyList())

        override suspend fun channelUploads(
            channelUrl: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = AppResult.Ok(
            Page(
                items = listOf(
                    VideoSummary(
                        id = "vid1",
                        url = "https://www.youtube.com/watch?v=vid1",
                        title = "New title",
                        channelName = "A",
                        durationSec = 60,
                        thumbnailUrl = null,
                        channelUrl = "https://youtube.com/c/a",
                        publishedAtMs = 200,
                    ),
                ),
            ),
        )

        override suspend fun channelInfo(channelUrl: String): AppResult<Channel> =
            AppResult.Ok(Channel(url = channelUrl, name = "A", avatarUrl = null, subscriberCount = -1))

        override suspend fun subtitles(id: String): AppResult<SubtitleTrack> =
            AppResult.Err("not implemented")
    }
}
