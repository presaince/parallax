/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player

/** A timestamped line from a description: "0:00 Intro", "1:23:45 The deep end". */
data class Chapter(val atSec: Long, val title: String)

/**
 * YouTube's convention is a line starting with a timestamp. Three or more lines, starting at zero,
 * are chapters; anything less is a person writing times in a paragraph.
 *
 * The timestamp may be wrapped in brackets or parentheses, sit behind a bullet or a dash, and a
 * two-part time may have three-digit minutes ("100:00" for a long stream).
 */
private val TIMESTAMP_LINE = Regex(
    """^\s*(?:[\[\(]\s*)?[•·*\-–—]?\s*(\d{1,3}):(\d{2})(?::(\d{2}))?\s*(?:[\]\)])?\s*[-–—:.)]?\s*(.+)$""",
)

fun timestampLine(line: String): Pair<Long, String>? {
    val match = TIMESTAMP_LINE.matchEntire(line) ?: return null
    val first = match.groupValues[1].toLongOrNull() ?: return null
    val second = match.groupValues[2].toLongOrNull() ?: return null
    val third = match.groupValues[3].toLongOrNull()
    val seconds = if (third != null) {
        first * 3600 + second * 60 + third
    } else {
        first * 60 + second
    }
    val title = match.groupValues[4].trim().trimStart('-', '–', '—', ':', ')', '.').trim()
    return seconds to title
}

fun parseChapters(description: String): List<Chapter> {
    val found = description.lineSequence()
        .mapNotNull { line -> timestampLine(line)?.let { Chapter(it.first, it.second) } }
        .toList()
    // Chapters need a start and a shape; one stray "10:30" in prose is not a chapter list.
    return if (found.size >= 3 && found.first().atSec == 0L) found else emptyList()
}
