/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource

/**
 * One video on the walk, and the titles that led to it: "you → A → this". The path is kept because
 * explaining a pick is the one thing a plain related list cannot do.
 */
data class RelatedNode(
    val video: VideoSummary,
    val path: List<String>,
)

/**
 * One hop of the related graph, and the only place in the app that walks it. Bored's one-page
 * answer, Bored's deep walk and the player's Up next all call [hop], so a fix to the traversal
 * reaches all three and there is no second algorithm to drift.
 *
 * A hop is not a walk: it takes a frontier, asks each node for its related videos, and returns the
 * children the walk has not seen. Deciding which children to keep, score or show is the caller's
 * business — Bored scores them by recipe, Up next appends them to its own pool.
 */
class RelatedWalk(
    private val source: VideoSource,
    private val perNode: Int = PER_NODE,
) {

    /**
     * One `related()` call per frontier node. A branch that fails contributes nothing: one dead
     * video should not end the walk. Children already in [visited], or already added by an earlier
     * node in this same hop, are skipped, so the result never repeats an id.
     */
    suspend fun hop(frontier: List<RelatedNode>, visited: Set<String>): List<RelatedNode> {
        val seen = visited.toMutableSet()
        val children = mutableListOf<RelatedNode>()
        for (node in frontier) {
            val related = when (val result = source.related(node.video.id)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> emptyList()
            }
            related.take(perNode).forEach { video ->
                if (seen.add(video.id)) {
                    children += RelatedNode(video, node.path + video.title)
                }
            }
        }
        return children
    }

    companion object {
        /** A full related page is more than a walk needs; twelve keeps one hop's cost bounded. */
        const val PER_NODE = 12
    }
}
