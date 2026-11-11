/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

/**
 * How far a refresh has got. Published by `ContentRepository` while it works, so the feed can say
 * what it is doing instead of showing an animation with no number in it.
 */
data class FeedProgress(
    val done: Int,
    val total: Int,
    /** The channel the counter has just passed, for the reader who recognises the name. */
    val channel: String = "",
) {
    /** 0..100, floored: 47 of 48 is not "100%", it is one channel short. */
    val percent: Int
        get() = if (total <= 0) 0 else ((done.toLong() * 100) / total).toInt().coerceIn(0, 100)
}

/**
 * One line, in the order a reader reads it: how far, how much of the whole, and which channel. The
 * count is the fact, the percentage is the shape of the wait, the name is where the time went.
 */
fun refreshLabel(progress: FeedProgress): String {
    val count = "${progress.done} of ${progress.total}"
    val name = progress.channel.trim()
    return buildString {
        append(count)
        append(" · ")
        append(progress.percent)
        append('%')
        if (name.isNotEmpty()) {
            append(" · ")
            append(name)
        }
    }
}
