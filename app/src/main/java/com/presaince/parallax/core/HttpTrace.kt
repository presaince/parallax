/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

/**
 * Whether one HTTP exchange is worth a log line.
 *
 * A bug report carries the last 300 entries. When every request logged twice, those 300 entries
 * covered about ten minutes of a busy session and were mostly `POST …/next -> 200`: the stall, the
 * failed video and the dead channel that the report was actually about were pushed out of the
 * window. A successful request in good time is the expected case, not news, so it is silent.
 *
 * The three exceptions are the ones a bug is usually about: a status that is not a success, a
 * success with nothing in it, and one that took so long the player had already stalled.
 */
object HttpTrace {

    /** Above this, a call is news even when it worked: the player names a stall at 15 s and retries at 20 s. */
    const val SLOW_MS = 8_000L

    /** Null means "log nothing". */
    fun note(code: Int, bytes: Int, elapsedMs: Long): String? = when {
        code !in 200..299 -> "HTTP $code after ${elapsedMs}ms"
        bytes == 0 -> "${code} with an empty body"
        elapsedMs >= SLOW_MS -> "slow ${elapsedMs}ms for ${bytes}B"
        else -> null
    }
}
