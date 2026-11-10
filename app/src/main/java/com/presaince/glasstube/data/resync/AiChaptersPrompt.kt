/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.data.model.Cue
import org.json.JSONObject

/** One chapter the model read out of a transcript: where it starts, what it is called. */
data class AiChapter(val atSec: Long, val title: String)

/**
 * Everything in AI chapters that is pure: sampling the transcript, asking, and reading the reply.
 * No network, no storage, so all of it is unit-tested. Only used when the video has no chapters in
 * its description, and only when the model settings are on.
 */
object AiChaptersPrompt {

    /**
     * Cues are sampled evenly across the whole video, so the budget never keeps only the opening of
     * a long one. One HTTP call either way (ResyncPrompt's budget).
     */
    fun sample(cues: List<Cue>): List<Cue> {
        val formatted = ResyncPrompt.formatCues(cues)
        if (formatted.length <= ResyncPrompt.MAX_CUE_CHARS) return cues
        val stride = (formatted.length / ResyncPrompt.MAX_CUE_CHARS) + 1
        val picked = ArrayList<Cue>(cues.size / stride + 1)
        var i = 0
        while (i < cues.size) {
            picked += cues[i]
            i += stride
        }
        return picked
    }

    fun systemPrompt(): String = buildString {
        appendLine("You split a video transcript into chapters.")
        appendLine("Reply with a single JSON object and nothing else:")
        appendLine("""{"chapters":[{"seconds":0,"title":"Intro"}]}""")
        appendLine("Rules:")
        appendLine("- seconds is an integer; the first chapter starts at 0.")
        appendLine("- Titles are short, in the language of the transcript.")
        appendLine("- At most 25 chapters; mark a new one only where the topic genuinely changes.")
        appendLine("- Only mark moments that are in the transcript. Never invent one.")
        append("Order by seconds.")
    }

    fun userPrompt(cues: List<Cue>): String = buildString {
        appendLine("TRANSCRIPT")
        append(ResyncPrompt.formatCues(cues))
    }

    /**
     * Reads the model's reply defensively: fenced JSON is unwrapped, chapters with no title or a
     * negative time are dropped, and the result is ordered and de-duplicated.
     */
    fun parseResponse(reply: String): List<AiChapter> {
        val json = reply.substringAfter('{', "").let { if (it.isEmpty()) return emptyList() else "{$it" }
        val trimmed = json.substring(0, json.lastIndexOf('}') + 1)
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("chapters") ?: return emptyList()

        val chapters = buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val seconds = entry.optLong("seconds", Long.MIN_VALUE)
                if (seconds == Long.MIN_VALUE || seconds < 0) continue
                val title = entry.optString("title").trim()
                if (title.isEmpty()) continue
                add(AiChapter(seconds, title))
            }
        }
        return chapters.sortedBy { it.atSec }.distinctBy { it.atSec }
    }
}
