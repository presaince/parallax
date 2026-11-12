/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import com.presaince.glasstube.data.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedPoolTest {

    @Test
    fun `wider opens the shallowest video not yet opened`() {
        val pool = pool("a", "b", "c")
        val frontier = frontierFor(pool, expanded = setOf("a"), RelatedDirection.WIDER, "playing")
        assertEquals("b", frontier.single().video.id)
    }

    @Test
    fun `deeper opens the newest video not yet opened`() {
        val pool = pool("a", "b", "c")
        val frontier = frontierFor(pool, expanded = setOf("c"), RelatedDirection.DEEPER, "playing")
        assertEquals("b", frontier.single().video.id)
    }

    @Test
    fun `the playing video is never opened`() {
        val pool = pool("playing", "a")
        val frontier = frontierFor(pool, emptySet(), RelatedDirection.WIDER, playingId = "playing")
        assertEquals("a", frontier.single().video.id)
    }

    @Test
    fun `a pool with nothing left to open has no frontier`() {
        val pool = pool("a", "b")
        assertTrue(frontierFor(pool, setOf("a", "b"), RelatedDirection.DEEPER, "playing").isEmpty())
    }

    @Test
    fun `a pool at the cap has no frontier`() {
        val pool = (1..RELATED_POOL_CAP).map { node("v$it") }
        assertTrue(frontierFor(pool, emptySet(), RelatedDirection.WIDER, "playing").isEmpty())
    }

    @Test
    fun `merging keeps the pool's order and appends what is new`() {
        val merged = mergePool(pool("a", "b"), listOf(node("c"), node("a"), node("d")))
        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.video.id })
    }

    @Test
    fun `merging stops at the cap`() {
        val merged = mergePool(pool("a", "b"), listOf(node("c"), node("d")), cap = 3)
        assertEquals(listOf("a", "b", "c"), merged.map { it.video.id })
    }

    @Test
    fun `every direction comes back from its own key`() {
        for (direction in RelatedDirection.entries) {
            assertEquals(direction, RelatedDirection.fromKey(direction.key))
        }
        assertEquals(RelatedDirection.WIDER, RelatedDirection.fromKey(null))
        assertEquals(RelatedDirection.WIDER, RelatedDirection.fromKey("sideways"))
    }

    @Test
    fun `a tap on the setting asks for the other mode`() {
        assertEquals(RelatedDirection.DEEPER, RelatedDirection.WIDER.other)
        assertEquals(RelatedDirection.WIDER, RelatedDirection.DEEPER.other)
    }

    private fun pool(vararg ids: String) = ids.map { node(it) }

    private fun node(id: String) = RelatedNode(
        video = VideoSummary(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            title = id,
            channelName = "channel",
            durationSec = 60,
            thumbnailUrl = null,
        ),
        path = listOf("You", id),
    )
}
