/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.cache

import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Page
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
        val json = """{"next":null,"items":[{"author":"x"},{"id":"ok","text":"t"}]}"""
        assertEquals(listOf("ok"), CommentCodec.decode(json)?.items?.map { it.id })
    }
}
