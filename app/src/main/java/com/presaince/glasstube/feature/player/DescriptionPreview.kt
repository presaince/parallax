/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

/**
 * How a description folds: the first [max] lines of real text, counted from the top. Blank lines
 * inside the window are kept, because a chapter list with its paragraphs squeezed out is harder to
 * scan than the same list with them. Leading blanks are dropped so a description that starts with
 * air does not open with air.
 */
fun descriptionPreview(all: List<String>, max: Int): List<String> {
    if (max <= 0) return emptyList()
    val out = mutableListOf<String>()
    var content = 0
    for (line in all.dropWhile { it.isBlank() }) {
        if (content >= max) break
        out += line
        if (line.isNotBlank()) content++
    }
    return out
}

/** True when the collapsed view is hiding real text, so the chevron has a reason to exist. */
fun descriptionHasMore(all: List<String>, max: Int): Boolean =
    all.dropWhile { it.isBlank() }.count { it.isNotBlank() } > max
