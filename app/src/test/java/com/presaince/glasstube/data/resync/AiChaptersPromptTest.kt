/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.data.model.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChaptersPromptTest {

    @Test
    fun `reply parsing reads chapters and orders them`() {
        val reply = """{"chapters":[{"seconds":180,"title":"Deep end"},{"seconds":0,"title":"Intro"}]}"""
        val chapters = AiChaptersPrompt.parseResponse(reply)
        assertEquals(2, chapters.size)
        assertEquals(0L, chapters.first().atSec)
        assertEquals("Intro", chapters.first().title)
        assertEquals(180L, chapters.last().atSec)
    }

    @Test
    fun `reply parsing survives prose and fences`() {
        val fenced = """
            Here you go:
            ```json
            {"chapters":[{"seconds":0,"title":"Start"}]}
            ```
        """.trimIndent()
        assertEquals(1, AiChaptersPrompt.parseResponse(fenced).size)
        assertTrue(AiChaptersPrompt.parseResponse("I cannot do that").isEmpty())
        assertTrue(AiChaptersPrompt.parseResponse("""{"chapters": [""").isEmpty())
    }

    @Test
    fun `reply parsing drops blank titles and duplicates`() {
        val reply = """{"chapters":[
            {"seconds":0,"title":"One"},
            {"seconds":0,"title":"Duplicate"},
            {"seconds":-5,"title":"Negative"},
            {"seconds":30,"title":"  "}
        ]}"""
        val chapters = AiChaptersPrompt.parseResponse(reply)
        assertEquals(1, chapters.size)
        assertEquals("One", chapters.single().title)
    }

    @Test
    fun `short transcripts pass through untouched`() {
        val cues = List(50) { Cue(it.toDouble(), it + 1.0, "line $it") }
        assertEquals(cues.size, AiChaptersPrompt.sample(cues).size)
    }

    @Test
    fun `long transcripts are sampled across the whole video`() {
        // 3000 cues * ~30 chars each is ~90k of formatted text, over the 60k budget.
        val cues = List(3000) { Cue(it.toDouble(), it + 1.0, "some transcript line number $it") }
        val sampled = AiChaptersPrompt.sample(cues)
        assertTrue(sampled.size < cues.size)
        assertEquals(cues.first(), sampled.first())
        // The end of the video must survive sampling, or the model never sees the ending.
        val lastSec = sampled.last().startSec
        assertTrue("last sampled cue at $lastSec, video is ${cues.last().startSec}", lastSec > 0.95 * cues.last().startSec)
        val formatted = ResyncPrompt.formatCues(sampled)
        assertTrue(formatted.length <= ResyncPrompt.MAX_CUE_CHARS * 2)
    }
}
