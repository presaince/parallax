/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.cache

import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentCodecTest {

    private val page = Page(
        items = listOf(
            Comment(
                id = "Ugx1",
                author = "someone",
                text = "the bit at 4:09 <b>is</b> wrong",
                likeCount = 12,
                publishedText = "2 days ago",
                publishedAtMs = 1_760_000_000_000,
                timestampSec = 249,
                pinned = true,
                replyCount = 3,
            ),
            Comment(
                id = "Ugx2",
                author = "other",
                text = "no timestamp here",
                likeCount = 0,
                publishedText = "1 hour ago",
                timestampSec = null,
                pinned = false,
                replyCount = 0,
            ),
        ),
        next = "https://www.youtube.com/watch?v=abc&ctoken=xyz",
    )

    @Test
    fun `round trips a page`() {
        val decoded = CommentCodec.decode(CommentCodec.encode(page))
        assertEquals(page, decoded)
    }

    @Test
    fun `round trips a last page`() {
        val decoded = CommentCodec.decode(CommentCodec.encode(page.copy(next = null)))
        assertNull(decoded?.next)
        assertEquals(2, decoded?.items?.size)
    }

    @Test
    fun `rejects foreign json`() {
        assertNull(CommentCodec.decode("not json"))
        assertNull(CommentCodec.decode("""{"unrelated":true}"""))
    }

    @Test
    fun `skips entries without an id`() {
        val json = """{"v":2,"next":null,"items":[{"author":"x"},{"id":"ok","text":"t"}]}"""
        assertEquals(listOf("ok"), CommentCodec.decode(json)?.items?.map { it.id })
    }

    @Test
    fun `a page cached before comments carried a date still reads`() {
        // The field is new; a page written by this version must not fail to parse over it.
        val json = """{"v":2,"next":null,"items":[{"id":"ok","text":"t","published":"1 day ago"}]}"""
        assertNull(CommentCodec.decode(json)?.items?.first()?.publishedAtMs)
    }

    @Test
    fun `a page from an older build is ignored, not misread`() {
        // This is what made the date sort look broken: old pages carried no date at all.
        val old = """{"next":null,"items":[{"id":"ok","text":"t","published":"1 day ago"}]}"""
        assertNull(CommentCodec.decode(old))
    }

    @Test
    fun `a commenter's channel survives the cache`() {
        val page = Page(
            items = listOf(
                Comment(
                    id = "Ugx3",
                    author = "someone",
                    authorUrl = "https://www.youtube.com/channel/UC123",
                    text = "t",
                    likeCount = 0,
                    publishedText = "now",
                    timestampSec = null,
                    pinned = false,
                    replyCount = 0,
                ),
            ),
        )
        assertEquals(
            "https://www.youtube.com/channel/UC123",
            CommentCodec.decode(CommentCodec.encode(page))?.items?.first()?.authorUrl,
        )
    }
}
