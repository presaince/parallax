/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResyncPromptTest {

    private fun comment(id: String, text: String) = Comment(
        id = id,
        author = "a",
        text = text,
        likeCount = 0,
        publishedText = "now",
        timestampSec = null,
        pinned = false,
        replyCount = 0,
    )

    @Test
    fun `finds the three timestamp shapes`() {
        assertEquals(listOf(3723L), ResyncPrompt.findTimestamps("the bit at 1:02:03 is good"))
        assertEquals(listOf(3723L), ResyncPrompt.findTimestamps("1.02.03 really"))
        assertEquals(listOf(3730L), ResyncPrompt.findTimestamps("62:10 onwards"))
        assertEquals(listOf(42L), ResyncPrompt.findTimestamps("around 0:42"))
    }

    @Test
    fun `ignores non timestamps`() {
        assertTrue(ResyncPrompt.findTimestamps("version 1.2.3 shipped").isEmpty())
        assertTrue(ResyncPrompt.findTimestamps("40% faster, 3 times").isEmpty())
        assertTrue(ResyncPrompt.findTimestamps("1:75 is not a clock").isEmpty())
    }

    @Test
    fun `keeps the first timestamp of a comment`() {
        val result = ResyncPrompt.timestamped(
            listOf(
                comment("a", "3:00 and later 5:00"),
                comment("b", "no time here"),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(180L, result.single().originalSec)
    }

    @Test
    fun `window is asymmetric and clamps at zero`() {
        val cues = (0..400).map { Cue(it.toDouble(), it + 1.0, "cue $it") }
        val comments = listOf(
            TimestampedComment("a", "a", "at 1:00", 60),
        )
        val sliced = ResyncPrompt.slice(cues, comments)
        // 60 - 300 clamps to 0; the tail is 15 seconds past the timestamp.
        assertEquals(0.0, sliced.first().startSec, 0.001)
        assertEquals(75.0, sliced.last().startSec, 0.001)
    }

    @Test
    fun `one call for a normal video`() {
        val cues = (0..600).map { Cue(it.toDouble(), it + 1.0, "line $it") }
        val comments = (1..5).map {
            TimestampedComment("c$it", "a", "at ${it * 60}", it * 60L)
        }
        assertEquals(1, ResyncPrompt.batches(comments, cues).size)
    }

    @Test
    fun `many comments batch by ten`() {
        val cues = (0..900).map { Cue(it.toDouble(), it + 1.0, "line $it") }
        val comments = (1..35).map {
            TimestampedComment("c$it", "a", "at ${it * 20}", it * 20L)
        }
        val batches = ResyncPrompt.batches(comments, cues)
        assertEquals(4, batches.size)
        assertEquals(35, batches.sumOf { it.comments.size })
        assertTrue(batches.all { it.comments.size <= 10 })
    }

    @Test
    fun `reply parsing drops later answers and unknown ids`() {
        val reply = """
            ```json
            {"moments":[
              {"commentId":"a","seconds":240,"confidence":0.9},
              {"commentId":"a","seconds":200,"confidence":0.5},
              {"commentId":"ghost","seconds":10,"confidence":1.0},
              {"commentId":"b","seconds":9999,"confidence":0.9}
            ]}
            ```
        """.trimIndent()

        val moments = ResyncPrompt.parseResponse(reply, mapOf("a" to 300, "b" to 100))
        assertEquals(1, moments.size)
        assertEquals("a", moments.first().commentId)
        assertEquals(240L, moments.first().seconds)
    }

    @Test
    fun `reply parsing survives prose and broken json`() {
        assertTrue(ResyncPrompt.parseResponse("I cannot do that", emptyMap()).isEmpty())
        assertTrue(ResyncPrompt.parseResponse("""{"moments": [""", mapOf("a" to 1)).isEmpty())
    }

    @Test
    fun `confidence is clamped`() {
        val reply = """{"moments":[{"commentId":"a","seconds":10,"confidence":4.2}]}"""
        assertEquals(1f, ResyncPrompt.parseResponse(reply, mapOf("a" to 100)).single().confidence)
    }
}
