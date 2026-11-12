/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

/**
 * Which end of Up next's pool the next hop starts from. Both walk the same graph with the same
 * [RelatedWalk]; only the seed differs, which is why this is a setting and not a second algorithm.
 */
enum class RelatedDirection(
    val key: String,
    val label: String,
    /** The subtext under the setting: it always describes the mode in force. */
    val summary: String,
) {
    /** Every hop is a child of the video being watched, so the list gets wider. */
    WIDER("wider", "Wider", "Stays close to the video you are watching."),

    /** Every hop is a child of the last thing added, so the list walks away from it. */
    DEEPER("deeper", "Deeper", "Follows a chain away from it, one video at a time."),
    ;

    /** Two modes, so a tap on the setting is the other one. */
    val other: RelatedDirection get() = if (this == WIDER) DEEPER else WIDER

    companion object {
        val default = WIDER

        fun fromKey(stored: String?): RelatedDirection =
            entries.firstOrNull { it.key == stored } ?: default
    }
}

/** Where the pool stops. Three hops of a full related page is already more than anyone scrolls. */
const val RELATED_POOL_CAP = 60

/**
 * The nodes the next hop should open: one, from whichever end of the pool the setting names.
 * A node is skipped when it has been opened before, and when it is the video playing — expanding
 * the video you are watching returns the list you already have.
 *
 * Nothing unopened, or a pool at the cap, is an empty frontier: the caller turns that into "nothing
 * left to expand" rather than a tap that repaints the same list.
 */
fun frontierFor(
    pool: List<RelatedNode>,
    expanded: Set<String>,
    direction: RelatedDirection,
    playingId: String,
): List<RelatedNode> {
    if (pool.size >= RELATED_POOL_CAP) return emptyList()
    val free = pool.filter { it.video.id !in expanded && it.video.id != playingId }
    val seed = when (direction) {
        RelatedDirection.WIDER -> free.firstOrNull()
        RelatedDirection.DEEPER -> free.lastOrNull()
    } ?: return emptyList()
    return listOf(seed)
}

/**
 * The pool in the order it was grown: the video's own related first, then each hop's additions.
 * Order matters — it is what makes "wider" mean the shallow end and keeps the reader's original
 * suggestions at the top of the card.
 */
fun mergePool(
    pool: List<RelatedNode>,
    incoming: List<RelatedNode>,
    cap: Int = RELATED_POOL_CAP,
): List<RelatedNode> {
    val seen = pool.mapTo(mutableSetOf()) { it.video.id }
    val added = incoming.filter { seen.add(it.video.id) }
    return (pool + added).take(cap)
}
