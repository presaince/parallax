/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.resync

import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Cue
import org.json.JSONObject

data class TimestampedComment(
    val commentId: String,
    val author: String,
    val text: String,
    /** What the commenter wrote, in seconds. The answer may move earlier, never later. */
    val originalSec: Long,
)

data class AlignedMoment(
    val commentId: String,
    val seconds: Long,
    val confidence: Float,
)

data class ResyncBatch(
    val comments: List<TimestampedComment>,
    val cues: List<Cue>,
)

/**
 * Everything in Timestamp Resync that is pure: finding the timestamp in a comment, slicing cues,
 * writing the prompt and reading the reply. No network, no storage, so all of it is unit-tested.
 *
 * The window is deliberately asymmetric. A commenter writes about something they have already
 * heard, so the concept starts somewhere in the five minutes before their timestamp and at most a
 * few seconds after it.
 */
object ResyncPrompt {

    const val BEFORE_SEC = 300L
    const val AFTER_SEC = 15L
    const val MAX_COMMENTS_PER_CALL = 30
    const val BATCH_SIZE = 10
    const val MAX_CUE_CHARS = 60_000

    // "1:02:03", "1.02.03" and "62:10" all appear in comments. Seconds are always written with
    // two digits, which is what keeps "version 1.2.3" out.
    private val TIMESTAMP = Regex(
        """(?<![\d:.])(\d{1,3}:\d{2}(?::\d{2})?|\d{1,3}\.\d{2}(?:\.\d{2})?)(?![\d:.])""",
    )

    fun findTimestamps(text: String): List<Long> = TIMESTAMP.findAll(text)
        .mapNotNull { toSeconds(it.groupValues[1]) }
        .toList()

    fun toSeconds(raw: String): Long? {
        val parts = raw.replace('.', ':').split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        val seconds = when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        }
        // 1:75 or 0:99 are typos, not timestamps.
        if (numbers.size >= 2 && numbers.last() >= 60) return null
        return seconds.takeIf { it > 0 }
    }

    fun timestamped(comments: List<Comment>): List<TimestampedComment> = comments
        .mapNotNull { comment ->
            val seconds = findTimestamps(comment.text).firstOrNull() ?: return@mapNotNull null
            TimestampedComment(
                commentId = comment.id,
                author = comment.author,
                text = comment.text,
                originalSec = seconds,
            )
        }
        .sortedBy { it.originalSec }

    /** Cues in [max(0, ts - 300), ts + 5] for each comment, merged so overlaps appear once. */
    fun slice(cues: List<Cue>, comments: List<TimestampedComment>): List<Cue> {
        if (comments.isEmpty()) return emptyList()
        val start = comments.minOf { (it.originalSec - BEFORE_SEC).coerceAtLeast(0) }
        val end = comments.maxOf { it.originalSec + AFTER_SEC }
        return cues.filter { it.endSec >= start && it.startSec <= end }
    }

    fun formatCues(cues: List<Cue>): String = cues.joinToString(separator = "\n") { cue ->
        "[${clock(cue.startSec)}] ${cue.text.replace('\n', ' ')}"
    }

    fun formatComments(comments: List<TimestampedComment>): String =
        comments.joinToString(separator = "\n") { comment ->
            "id=${comment.commentId} at=${comment.originalSec} text=${comment.text.replace('\n', ' ')}"
        }

    fun systemPrompt(): String = buildString {
        appendLine("You align YouTube comments to subtitle cues.")
        appendLine("Reply with a single JSON object and nothing else:")
        appendLine("""{"moments":[{"commentId":"...","seconds":123,"confidence":0.9}]}""")
        appendLine("Rules:")
        appendLine("- seconds is an integer and must point at the moment the comment describes.")
        appendLine("- Never invent a moment. Omit a comment you cannot place from the cues.")
        appendLine("- Never return a second later than the comment's timestamp.")
        appendLine("- confidence is 0 to 1. Use below 0.5 when you are guessing.")
        append("Only use commentId values given in the input.")
    }

    fun userPrompt(comments: List<TimestampedComment>, cues: List<Cue>): String = buildString {
        appendLine("CUES")
        appendLine(formatCues(cues))
        appendLine()
        appendLine("COMMENTS")
        append(formatComments(comments))
    }

    /**
     * Batches so one video usually costs one call. A batch that would blow the character budget is
     * split in half until it fits, which terminates at a single comment.
     */
    fun batches(comments: List<TimestampedComment>, cues: List<Cue>): List<ResyncBatch> {
        if (comments.isEmpty()) return emptyList()
        if (comments.size <= MAX_COMMENTS_PER_CALL) {
            val batch = ResyncBatch(comments, slice(cues, comments))
            if (formatCues(batch.cues).length <= MAX_CUE_CHARS) return listOf(batch)
        }
        return comments.chunked(BATCH_SIZE).flatMap { chunk -> split(chunk, cues) }
    }

    private fun split(
        comments: List<TimestampedComment>,
        cues: List<Cue>,
    ): List<ResyncBatch> {
        if (comments.isEmpty()) return emptyList()
        val sliced = slice(cues, comments)
        if (comments.size == 1 || formatCues(sliced).length <= MAX_CUE_CHARS) {
            return listOf(ResyncBatch(comments, sliced))
        }
        val half = comments.size / 2
        return split(comments.take(half), cues) + split(comments.drop(half), cues)
    }

    /**
     * Reads the model's reply defensively: fenced JSON is unwrapped, unknown ids are dropped,
     * seconds later than the original are dropped, and confidence is clamped.
     */
    fun parseResponse(
        reply: String,
        originals: Map<String, Long>,
    ): List<AlignedMoment> {
        val json = reply.substringAfter('{', "").let { if (it.isEmpty()) return emptyList() else "{$it" }
        val trimmed = json.substring(0, json.lastIndexOf('}') + 1)
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("moments") ?: return emptyList()

        val moments = buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val id = entry.optString("commentId").takeIf { it.isNotBlank() } ?: continue
                val original = originals[id] ?: continue
                val seconds = entry.optLong("seconds", Long.MIN_VALUE)
                if (seconds == Long.MIN_VALUE || seconds < 0 || seconds > original) continue
                val confidence = entry.optDouble("confidence", 0.0)
                    .takeIf { it.isFinite() }
                    ?.toFloat()
                    ?.coerceIn(0f, 1f)
                    ?: 0f
                add(AlignedMoment(commentId = id, seconds = seconds, confidence = confidence))
            }
        }.distinctBy { it.commentId }

        return moments
    }

    private fun clock(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
    }

    private fun Long.pad(): String = if (this < 10) "0$this" else toString()
}
