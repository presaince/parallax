/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersTest {

    @Test
    fun `the caption carries the time until the reader switches it off`() {
        val chapter = Chapter(atSec = 83, title = "The middle part")
        assertEquals("1:23 · The middle part", chapterCaption(chapter, true, "1:23"))
        assertEquals("The middle part", chapterCaption(chapter, false, "1:23"))
    }

    @Test
    fun `a list starting at zero is a chapter list`() {
        val description = """
            In this video:
            0:00 Intro
            1:23 The middle part
            1:02:03 The long ending
        """.trimIndent()

        val chapters = parseChapters(description)
        assertEquals(3, chapters.size)
        assertEquals(0L, chapters[0].atSec)
        assertEquals("Intro", chapters[0].title)
        assertEquals(83L, chapters[1].atSec)
        assertEquals(3723L, chapters[2].atSec)
        assertEquals("The long ending", chapters[2].title)
    }

    @Test
    fun `timestamps with punctuation separators still parse`() {
        assertEquals(83L to "Start", timestampLine("1:23 - Start"))
        assertEquals(83L to "Start", timestampLine("01:23 — Start"))
        assertEquals(83L to "Start", timestampLine("1:23 Start"))
    }

    @Test
    fun `brackets and bullets around a timestamp still parse`() {
        assertEquals(83L to "Start", timestampLine("[1:23] Start"))
        assertEquals(83L to "Start", timestampLine("(1:23) Start"))
        assertEquals(83L to "Start", timestampLine("• 1:23 Start"))
        assertEquals(83L to "Start", timestampLine("- 01:23 – Start"))
        assertEquals(3723L to "Long", timestampLine("[1:02:03] Long"))
    }

    @Test
    fun `minutes past ninety-nine are still a timestamp`() {
        assertEquals(6030L to "Still going", timestampLine("100:30 Still going"))
    }

    @Test
    fun `two lines or no zero are not chapters`() {
        assertTrue(parseChapters("0:00 Intro\n1:20 Middle").isEmpty())
        assertTrue(parseChapters("5:00 Later\n6:00 Later still\n7:00 End").isEmpty())
    }

    @Test
    fun `prose with a stray time is not a chapter`() {
        assertTrue(parseChapters("I watched at 10:30 and it was fine.").isEmpty())
    }

    @Test
    fun `active chapter is the last one the playhead has passed`() {
        val chapters = listOf(
            Chapter(0, "Intro"),
            Chapter(83, "Middle"),
            Chapter(3723, "Ending"),
        )
        assertEquals(0, activeChapterIndex(chapters, 0))
        assertEquals(0, activeChapterIndex(chapters, 82_000))
        assertEquals(1, activeChapterIndex(chapters, 83_000))
        assertEquals(2, activeChapterIndex(chapters, 3_723_000))
    }

    @Test
    fun `before the first chapter there is no active chapter`() {
        val chapters = listOf(Chapter(10, "Later"))
        assertEquals(-1, activeChapterIndex(chapters, 0))
    }
}
