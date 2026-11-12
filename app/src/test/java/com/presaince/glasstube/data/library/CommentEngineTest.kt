/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.library

import androidx.room.Room
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.model.Channel
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.model.SubtitleTrack
import com.presaince.glasstube.data.model.VideoDetail
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The comments cache: a page inside its hour is served from disk, and only a forced read goes back
 * to the extractor. Its own test class because it is its own engine now.
 */
@RunWith(RobolectricTestRunner::class)
class CommentEngineTest {

    private lateinit var db: GlassTubeDb

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            GlassTubeDb::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a fresh comments page is served from cache until a refresh forces it`() = runTest {
        val source = CountingCommentsSource()
        val comments = CommentEngine(source = source, db = db)

        comments.comments("vid1", null)
        comments.comments("vid1", null)
        assertEquals("the second read is the cached page", 1, source.commentCalls)

        comments.comments("vid1", null, refresh = true)
        assertEquals("a forced read goes to the extractor", 2, source.commentCalls)
    }

    @Test
    fun `the first page stores the video's comment total`() = runTest {
        val comments = CommentEngine(source = CountingCommentsSource(total = 42), db = db)

        comments.comments("vid1", null)

        assertEquals(42L, db.videos().find("vid1")?.commentCount)
    }

    private class CountingCommentsSource(private val total: Long = 1) : VideoSource {
        var commentCalls = 0

        override val id = "fake"

        override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> {
            commentCalls++
            return AppResult.Ok(
                Page(
                    items = listOf(
                        Comment(
                            id = "c$commentCalls",
                            author = "someone",
                            text = "seen it once",
                            likeCount = 0,
                            publishedText = "1 day ago",
                            timestampSec = null,
                            pinned = false,
                            replyCount = 0,
                            repliesPage = null,
                        ),
                    ),
                    total = total,
                ),
            )
        }

        override suspend fun search(query: String): AppResult<Page<VideoSummary>> =
            AppResult.Ok(Page(items = emptyList()))

        override suspend fun video(id: String): AppResult<VideoDetail> =
            AppResult.Err("not implemented")

        override suspend fun streams(id: String): AppResult<StreamSet> =
            AppResult.Err("not implemented")

        override suspend fun related(id: String): AppResult<List<VideoSummary>> =
            AppResult.Ok(emptyList())

        override suspend fun channelUploads(
            channelUrl: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = AppResult.Ok(Page(items = emptyList()))

        override suspend fun channelInfo(channelUrl: String): AppResult<Channel> =
            AppResult.Err("not implemented")

        override suspend fun channelTab(
            channelUrl: String,
            filter: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = AppResult.Ok(Page(items = emptyList()))

        override suspend fun subtitles(id: String): AppResult<SubtitleTrack> =
            AppResult.Err("not implemented")
    }
}
