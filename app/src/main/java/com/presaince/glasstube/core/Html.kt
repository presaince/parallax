/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.text.Html

/** One image tag, with its attributes, so the alt text can be rescued from it. */
private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ALT_ATTR = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

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
    // YouTube's richer descriptions carry some marks as images ("<img … alt="📷">"), and the
    // platform parser drops an image with no ImageGetter. The alt text is the mark, so it
    // becomes the text (`121704`).
    val withAlt = IMG_TAG.replace(decoded) { tag ->
        ALT_ATTR.find(tag.value)?.groupValues?.get(1).orEmpty()
    }
    val hasTags = withAlt.contains('<') && withAlt.contains('>')
    // Entities can arrive without a single tag ("I&amp;apos;m here"), and those still need the
    // platform table — otherwise "&apos;" reaches the screen verbatim.
    val hasEntities = withAlt.contains('&')
    val text = if (hasTags || hasEntities) {
        runCatching { Html.fromHtml(withAlt, Html.FROM_HTML_MODE_LEGACY).toString() }
            .getOrElse { withAlt }
    } else {
        // Plain text with nothing to decode: keep the author's line breaks untouched.
        withAlt
    }
    return text
        .replace("\u200B", "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
