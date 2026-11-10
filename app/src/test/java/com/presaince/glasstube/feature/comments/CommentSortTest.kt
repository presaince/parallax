/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.comments

import com.presaince.glasstube.core.SortOption
import com.presaince.glasstube.core.SortState
import com.presaince.glasstube.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader can read a page newest first or most liked first, and the direction flips. A pinned
 * comment is not part of that order: it stays on top of both.
 */
class CommentSortTest {

    private fun comment(
        id: String,
        likes: Int = 0,
        at: Long? = null,
        pinned: Boolean = false,
    ) = Comment(
        id = id,
        author = "a",
        text = "t",
        likeCount = likes,
        publishedText = "1 day ago",
        publishedAtMs = at,
        timestampSec = null,
        pinned = pinned,
        replyCount = 0,
    )

    private val page = listOf(
        comment("old", likes = 3, at = 1_000),
        comment("new", likes = 1, at = 3_000),
        comment("mid", likes = 9, at = 2_000),
    )

    @Test
    fun `newest first is the default reading`() {
        val order = sortedComments(page, SortState(SortOption.DATE, ascending = false))
        assertEquals(listOf("new", "mid", "old"), order.map { it.id })
    }

    @Test
    fun `the same choice flipped reads oldest first`() {
        val order = sortedComments(page, SortState(SortOption.DATE, ascending = true))
        assertEquals(listOf("old", "mid", "new"), order.map { it.id })
    }

    @Test
    fun `most liked first, and its inverse`() {
        assertEquals(
            listOf("mid", "old", "new"),
            sortedComments(page, SortState(SortOption.LIKES, ascending = false)).map { it.id },
        )
        assertEquals(
            listOf("new", "old", "mid"),
            sortedComments(page, SortState(SortOption.LIKES, ascending = true)).map { it.id },
        )
    }

    @Test
    fun `a pinned comment stays on top whatever the sort`() {
        val withPin = page + comment("pin", likes = 0, at = 500, pinned = true)
        for (state in listOf(
            SortState(SortOption.DATE, ascending = false),
            SortState(SortOption.LIKES, ascending = true),
        )) {
            assertEquals("pin", sortedComments(withPin, state).first().id)
        }
    }

    @Test
    fun `a comment with no date sorts as the oldest rather than as now`() {
        val unknown = page + comment("noda", likes = 0, at = null)
        val order = sortedComments(unknown, SortState(SortOption.DATE, ascending = false))
        assertEquals("noda", order.last().id)
    }
}
