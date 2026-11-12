/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Descriptions and comments arrive as HTML from YouTube. The renderer keeps line breaks, drops the
 * markup, and must survive the double encoding that used to put "&amp;apos;" on screen.
 */
@RunWith(RobolectricTestRunner::class)
class HtmlTest {

    @Test
    fun `blank input is empty`() {
        assertEquals("", cleanHtml(""))
        assertEquals("", cleanHtml("   \n  "))
    }

    @Test
    fun `plain text keeps its own line breaks`() {
        assertEquals("line one\nline two", cleanHtml("line one\nline two"))
    }

    @Test
    fun `double encoded entities are decoded without any tags`() {
        // "I&amp;apos;m" is what the extractor hands over. One un-escape leaves "&apos;", which is
        // the bug this test exists for: the text has no tags, so it used to skip the parser.
        assertEquals("I'm here", cleanHtml("I&amp;apos;m here"))
    }

    @Test
    fun `a bare ampersand survives`() {
        assertEquals("Tom & Jerry", cleanHtml("Tom & Jerry"))
    }

    @Test
    fun `tags are stripped and br becomes a newline`() {
        assertEquals("a\nb", cleanHtml("a<br>b"))
        assertEquals("bold", cleanHtml("<b>bold</b>"))
    }

    @Test
    fun `runs of spaces collapse and blank lines are capped`() {
        assertEquals("a b", cleanHtml("a    b"))
        assertEquals("a\n\nb", cleanHtml("a\n\n\n\nb"))
    }

    @Test
    fun `zero width spaces are removed`() {
        assertEquals("ab", cleanHtml("a\u200Bb"))
    }

    @Test
    fun `an image keeps its alt text`() {
        // 121704: the description's emoji was an <img>, and the platform parser drops images.
        assertEquals(
            "Follow me on \uD83D\uDCF7 Instagram",
            cleanHtml("Follow me on <img src=\"https://x/i.png\" alt=\"\uD83D\uDCF7\"> Instagram"),
        )
    }

    @Test
    fun `an image with no alt becomes nothing`() {
        assertEquals("a b", cleanHtml("a <img src=\"https://x/i.png\"> b"))
    }
}
