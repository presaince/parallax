/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

/**
 * The entity table, decoded without the platform parser.
 *
 * `cleanHtml` can lean on `android.text.Html`, but a caption cue is one line of text and it is
 * parsed in the source layer, where a JVM test should not need Android. YouTube writes an
 * apostrophe as `&#39;` in a caption track, and the reader was seeing the number instead of the
 * mark (`115549`).
 */
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "ndash" to "\u2013",
    "mdash" to "\u2014",
    "hellip" to "\u2026",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "middot" to "\u00B7",
)

private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});")

/**
 * Decodes the entities YouTube writes, and leaves everything else exactly as it arrived: a bare
 * `&`, an unknown name (`&foo;`) and a lone `#` are all author text, not markup. Pure, so the
 * caption parser can be tested on the JVM.
 */
fun decodeEntities(raw: String): String {
    if ('&' !in raw) return raw
    return ENTITY.replace(raw) { match ->
        val body = match.groupValues[1]
        val replacement = when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.let(::characterOf)
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::characterOf)
            else -> NAMED_ENTITIES[body.lowercase()]
        }
        replacement ?: match.value
    }
}

/** One code point as text, or null when the number is not one (0, a surrogate half, too large). */
private fun characterOf(code: Int): String? {
    if (!Character.isValidCodePoint(code)) return null
    if (code in 0xD800..0xDFFF) return null
    return String(Character.toChars(code))
}
