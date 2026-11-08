/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.resync

import com.presaince.parallax.data.model.Cue

/**
 * Cue parsing for the two formats YouTube actually hands out: WebVTT (dot thousandths) and SRT
 * (comma thousandths). TTML is accepted because auto-generated tracks sometimes arrive that way.
 *
 * Anything unknown returns an empty list rather than throwing: a track the app cannot read is the
 * same as no track, and the caller shows "no captions" either way.
 */
object SubtitleParser {

    private val TIMING = Regex("""(\d{1,2}:\d{2}:\d{2}[.,]\d{3}|\d{1,2}:\d{2}[.,]\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[.,]\d{3}|\d{1,2}:\d{2}[.,]\d{3})""")
    private val TAG = Regex("""<[^>]*>""")
    private val TTML_P = Regex(
        """<p[^>]*\bbegin="([^"]+)"[^>]*\bend="([^"]+)"[^>]*>(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun parseVttOrSrt(text: String): List<Cue> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = mutableListOf<Cue>()

        var i = 0
        while (i < lines.size) {
            val timing = TIMING.find(lines[i])
            if (timing == null) {
                i++
                continue
            }

            val start = seconds(timing.groupValues[1])
            val end = seconds(timing.groupValues[2])
            if (start == null || end == null) {
                i++
                continue
            }

            val body = StringBuilder()
            i++
            while (i < lines.size && lines[i].isNotBlank()) {
                if (body.isNotEmpty()) body.append(' ')
                body.append(clean(lines[i]))
                i++
            }
            val textLine = body.toString().trim()
            if (textLine.isNotEmpty()) cues += Cue(start, end, textLine)
        }
        return cues
    }

    fun parseTtml(text: String): List<Cue> = TTML_P.findAll(text).mapNotNull { match ->
        val start = clockSeconds(match.groupValues[1]) ?: return@mapNotNull null
        val end = clockSeconds(match.groupValues[2]) ?: return@mapNotNull null
        val body = clean(match.groupValues[3])
        if (body.isBlank()) null else Cue(start, end, body)
    }.toList()

    /** "00:01:02.500" or "1:02.5" -> seconds. Null when it is not a clock value. */
    fun seconds(raw: String): Double? {
        val parts = raw.replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        return when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        }
    }

    /** TTML allows "1.5s" and "00:00:01.500". */
    private fun clockSeconds(raw: String): Double? {
        val value = raw.trim()
        if (value.endsWith("s")) return value.dropLast(1).toDoubleOrNull()
        return seconds(value)
    }

    private fun clean(line: String): String = TAG.replace(line, "").trim()
}
