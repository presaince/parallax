/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.text.Html

/**
 * YouTube sends descriptions and comment text as HTML with entities: `&amp;apos;`, `&#128515;`,
 * `<a href="…">`, `<br>`. Rendering that raw puts tag soup on screen. This turns it into the string
 * a reader expects, keeping link and emphasis text and dropping the markup.
 *
 * `Html.fromHtml` is the platform parser and handles the entity table better than a hand-written
 * regex; it is used as a converter, not as a renderer.
 */
fun cleanHtml(raw: String): String {
    if (raw.isBlank()) return ""
    // YouTube double-encodes some entities ("I&amp;apos;m"), so one pass leaves "&apos;" behind.
    // Un-escape the ampersand once, by hand, rather than parsing twice: a second Html.fromHtml
    // pass turns the newlines that <br> and <p> produced back into spaces, which is why the
    // description used to arrive as one paragraph.
    val decoded = raw.replace("&amp;", "&")
    val hasTags = decoded.contains('<') && decoded.contains('>')
    // Entities can arrive without a single tag ("I&amp;apos;m here"), and those still need the
    // platform table — otherwise "&apos;" reaches the screen verbatim.
    val hasEntities = decoded.contains('&')
    val text = if (hasTags || hasEntities) {
        runCatching { Html.fromHtml(decoded, Html.FROM_HTML_MODE_LEGACY).toString() }
            .getOrElse { decoded }
    } else {
        // Plain text with nothing to decode: keep the author's line breaks untouched.
        decoded
    }
    return text
        .replace("\u200B", "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
