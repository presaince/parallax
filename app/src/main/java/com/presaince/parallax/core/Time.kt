/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

/** 249 -> "4:09", 3907 -> "1:05:07". Negative or unknown lengths render as "--:--". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.pad2()}:${s.pad2()}" else "$m:${s.pad2()}"
}

private fun Long.pad2(): String = if (this < 10) "0$this" else toString()

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
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 30 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        minutes < 365 * 24 * 60 -> "${minutes / (30 * 24 * 60)}mo ago"
        else -> "${minutes / (365 * 24 * 60)}y ago"
    }
}
