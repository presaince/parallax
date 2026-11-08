/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

/**
 * YouTube serves a fixed set of thumbnail sizes per video id, so a URL can always be built even
 * when the extractor returned none. `hqdefault` is 480x360, which is enough for a list row and
 * small enough to be worth caching.
 */
object Thumbnails {

    /** The extractor's own URL when it has one, otherwise the predictable one. */
    fun forVideo(videoId: String, extracted: String?): String {
        val candidate = extracted?.takeIf { it.startsWith("https://") }
        return candidate ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
