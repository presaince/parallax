/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.comments

import com.presaince.glasstube.core.SortOption
import com.presaince.glasstube.core.SortState
import com.presaince.glasstube.core.sortList
import com.presaince.glasstube.data.model.Comment

/** The two ways a comment page can be read. Nothing else has an ordering the extractor gives us. */
val COMMENT_SORTS = listOf(SortOption.DATE, SortOption.LIKES)

/**
 * Comments in the reader's chosen order. A pinned comment stays on top whatever the sort says: it
 * was pinned to be read first, and sorting is about the rest of the page.
 *
 * A comment whose date never arrived (an old cached page, or an extractor that gave none) sorts as
 * the oldest, so a real timestamp always outranks a guess.
 */
fun sortedComments(items: List<Comment>, state: SortState): List<Comment> {
    val pinned = items.filter { it.pinned }
    val rest = items.filterNot { it.pinned }
    val ordered = sortList(
        rest,
        state,
        date = { it.publishedAtMs },
        likes = { it.likeCount.toLong() },
    )
    return pinned + ordered
}
