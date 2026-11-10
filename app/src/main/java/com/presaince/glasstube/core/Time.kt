/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

/** 249 -> "4:09", 3907 -> "1:05:07". Negative or unknown lengths render as "--:--". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.pad2()}:${s.pad2()}" else "$m:${s.pad2()}"
}

private fun Long.pad2(): String = if (this < 10) "0$this" else toString()

/**
 * The size of a seek, then where it landed: "+12s to 4:09". The seconds are the part a person
 * remembers ("I jumped half a minute"), the position is the part they check. The minus is the
 * typographic one, so the sign cannot be mistaken for a hyphen between two times.
 */
fun seekDelta(seconds: Long): String = when {
    seconds > 0 -> "+${seconds}s"
    seconds < 0 -> "\u2212${-seconds}s"
    else -> "0s"
}

/** The undo chip's sentence: how far the seek moved, and nothing else. */
fun seekUndoLabel(beforeMs: Long, afterMs: Long): String =
    "Seeked ${seekDelta((afterMs - beforeMs) / 1000)}"

/** Singular and plural in one place. "1 replies" is not an output the app may produce. */
fun plural(count: Long, singular: String, plural: String = defaultPlural(singular)): String =
    if (count == 1L) "$count $singular" else "$count $plural"

/**
 * The three English rules the app needs, and no grammar engine: sibilant endings take -es,
 * consonant + y takes -ies, everything else takes -s. Callers with an irregular word pass the
 * plural explicitly; the default is for the regular nouns on cards and counts.
 */
private fun defaultPlural(singular: String): String {
    val lower = singular.lowercase()
    return when {
        lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") ||
            lower.endsWith("ch") || lower.endsWith("sh") -> singular + "es"
        lower.endsWith("y") && lower.length > 1 &&
            lower[lower.length - 2] !in "aeiou" -> singular.dropLast(1) + "ies"
        else -> singular + "s"
    }
}

/** "3h ago", "12d ago". Takes [now] so it can be tested without a clock. */
fun timeAgo(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - epochMs).coerceAtLeast(0)) / 60_000
    // No-break space: "5h" must never end a line with "ago" on the next one. Every card, every
    // header and the player's facts line put this string in a wrapping context.
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m\u00A0ago"
        minutes < 24 * 60 -> "${minutes / 60}h\u00A0ago"
        minutes < 30 * 24 * 60 -> "${minutes / (24 * 60)}d\u00A0ago"
        minutes < 365 * 24 * 60 -> "${minutes / (30 * 24 * 60)}mo\u00A0ago"
        else -> "${minutes / (365 * 24 * 60)}y\u00A0ago"
    }
}
