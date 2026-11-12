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
 * A length, not a position: 4 -> "4s", 184 -> "3m4s", 7384 -> "2h3m4s". The share sheet's clip
 * length is read as a quantity ("how long is this"), where the clock form "1:23:04" invites being
 * read as a place in the video, exactly like the two times either side of it.
 */
fun formatSpan(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h" + (if (m > 0) "${m}m" else "") + (if (s > 0) "${s}s" else "")
        m > 0 -> "${m}m" + (if (s > 0) "${s}s" else "")
        else -> "${s}s"
    }
}


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

/**
 * The undo chip's sentence: how far the seek moved, and nothing else. "Seek", not "Seeked": the
 * chip names the thing that happened, and the tense was doing no work the reader needed.
 */
fun seekUndoLabel(beforeMs: Long, afterMs: Long): String =
    "Seek ${seekDelta((afterMs - beforeMs) / 1000)}"

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
    // A scheduled stream is dated in the future, and reading that as "now" is how a premiere wore
    // a fresh timestamp while it had not begun (reported 2026-11-12). Say what is true instead.
    if (epochMs > now) return timeUntil(epochMs, now)
    val minutes = (now - epochMs) / 60_000
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

/** How long until a scheduled start: "in 1h", "in a moment". The other direction of [timeAgo]. */
fun timeUntil(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (epochMs - now).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "in\u00A0a moment"
        minutes < 60 -> "in\u00A0${minutes}m"
        minutes < 24 * 60 -> "in\u00A0${minutes / 60}h"
        minutes < 30 * 24 * 60 -> "in\u00A0${minutes / (24 * 60)}d"
        minutes < 365 * 24 * 60 -> "in\u00A0${minutes / (30 * 24 * 60)}mo"
        else -> "in\u00A0${minutes / (365 * 24 * 60)}y"
    }
}
