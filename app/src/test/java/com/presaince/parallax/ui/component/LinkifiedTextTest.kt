/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A comment body is text someone else wrote: the links in it have to be links, and the rest of the
 * text has to stay tappable so the row still opens.
 */
class LinkifiedTextTest {

    @Test
    fun `both http and bare www read as links`() {
        val text = "watch https://youtu.be/x and www.example.com please"
        val ranges = linkRanges(text).map { text.substring(it) }
        assertEquals(listOf("https://youtu.be/x", "www.example.com"), ranges)
    }

    @Test
    fun `plain words carry no link`() {
        assertEquals(emptyList<IntRange>(), linkRanges("nothing to open here"))
    }

    @Test
    fun `a bare www address gains the scheme it cannot be opened without`() {
        assertEquals("https://www.example.com/a", absoluteUrl("www.example.com/a"))
        assertEquals("https://example.com/a", absoluteUrl("https://example.com/a"))
    }
}
