/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.resync

import com.presaince.parallax.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun `parses webvtt with settings and tags`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000 align:start position:0%
            <c>hello</c> there

            01:02:03.500 --> 01:02:05.000
            second
            line
        """.trimIndent()

        val cues = SubtitleParser.parseVttOrSrt(vtt)
        assertEquals(2, cues.size)
        assertEquals(1.0, cues[0].startSec, 0.001)
        assertEquals("hello there", cues[0].text)
        assertEquals(3723.5, cues[1].startSec, 0.001)
        assertEquals("second line", cues[1].text)
    }

    @Test
    fun `parses srt with commas`() {
        val srt = """
            1
            00:00:02,250 --> 00:00:03,500
            a cue
        """.trimIndent()

        val cues = SubtitleParser.parseVttOrSrt(srt)
        assertEquals(1, cues.size)
        assertEquals(2.25, cues[0].startSec, 0.001)
    }

    @Test
    fun `parses ttml`() {
        val ttml = """
            <tt><body><div>
            <p begin="00:00:01.500" end="00:00:03.000">first <span>bit</span></p>
            <p begin="2.5s" end="4.0s">second</p>
            </div></body></tt>
        """.trimIndent()

        val cues = SubtitleParser.parseTtml(ttml)
        assertEquals(2, cues.size)
        assertEquals(1.5, cues[0].startSec, 0.001)
        assertEquals("first bit", cues[0].text)
        assertEquals(2.5, cues[1].startSec, 0.001)
    }

    @Test
    fun `garbage yields nothing`() {
        assertTrue(SubtitleParser.parseVttOrSrt("not a caption file").isEmpty())
        assertTrue(SubtitleParser.parseTtml("<html>no cues</html>").isEmpty())
        assertNull(SubtitleParser.seconds("banana"))
    }
}
