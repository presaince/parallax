/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.model.Channel
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.model.SubtitleTrack
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.model.VideoDetail
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedWalkTest {

    @Test
    fun `a hop returns the children with the path to them`() = runTest {
        val walk = RelatedWalk(source = FakeSource(mapOf("seed" to listOf(video("a")))))
        val hop = walk.hop(listOf(RelatedNode(video("seed"), listOf("You"))), visited = emptySet())
        assertEquals(listOf("a"), hop.map { it.video.id })
        assertEquals(listOf("You", "A"), hop.single().path)
    }

    @Test
    fun `a video already visited is not returned again`() = runTest {
        val walk = RelatedWalk(source = FakeSource(mapOf("seed" to listOf(video("a"), video("b")))))
        val hop = walk.hop(listOf(RelatedNode(video("seed"), listOf("You"))), visited = setOf("a"))
        assertEquals(listOf("b"), hop.map { it.video.id })
    }

    @Test
    fun `one video appearing under two branches is returned once`() = runTest {
        val walk = RelatedWalk(
            source = FakeSource(
                mapOf(
                    "one" to listOf(video("same"), video("x")),
                    "two" to listOf(video("same"), video("y")),
                ),
            ),
        )
        val hop = walk.hop(
            listOf(RelatedNode(video("one"), emptyList()), RelatedNode(video("two"), emptyList())),
            visited = emptySet(),
        )
        assertEquals(listOf("same", "x", "y"), hop.map { it.video.id })
    }

    @Test
    fun `a branch that fails does not end the hop`() = runTest {
        val walk = RelatedWalk(
            source = FakeSource(mapOf("good" to listOf(video("a"))), failing = setOf("dead")),
        )
        val hop = walk.hop(
            listOf(RelatedNode(video("dead"), emptyList()), RelatedNode(video("good"), emptyList())),
            visited = emptySet(),
        )
        assertEquals(listOf("a"), hop.map { it.video.id })
    }

    @Test
    fun `a node gives at most its share of children`() = runTest {
        val walk = RelatedWalk(
            source = FakeSource(mapOf("seed" to listOf(video("a"), video("b"), video("c")))),
            perNode = 2,
        )
        val hop = walk.hop(listOf(RelatedNode(video("seed"), emptyList())), visited = emptySet())
        assertTrue(hop.size == 2)
    }

    private fun video(id: String) = VideoSummary(
        id = id,
        url = "https://www.youtube.com/watch?v=$id",
        title = id.uppercase(),
        channelName = "channel",
        durationSec = 60,
        thumbnailUrl = null,
    )

    private class FakeSource(
        private val related: Map<String, List<VideoSummary>> = emptyMap(),
        private val failing: Set<String> = emptySet(),
    ) : VideoSource {
        override val id = "fake"

        override suspend fun related(id: String): AppResult<List<VideoSummary>> =
            if (id in failing) AppResult.Err("dead") else AppResult.Ok(related[id].orEmpty())

        override suspend fun search(query: String): AppResult<Page<VideoSummary>> =
            AppResult.Err("no")

        override suspend fun video(id: String): AppResult<VideoDetail> = AppResult.Err("no")
        override suspend fun streams(id: String): AppResult<StreamSet> = AppResult.Err("no")
        override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> =
            AppResult.Err("no")

        override suspend fun channelUploads(
            channelUrl: String,
            page: String?,
        ): AppResult<Page<VideoSummary>> = AppResult.Err("no")

        override suspend fun channelInfo(channelUrl: String): AppResult<Channel> = AppResult.Err("no")
        override suspend fun subtitles(id: String): AppResult<SubtitleTrack> = AppResult.Err("no")
    }
}
