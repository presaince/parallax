/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionPreviewTest {

    private val chapters = listOf(
        "0:00 Opening",
        "1:12 The claim",
        "2:40 The evidence",
        "4:05 The objection",
        "6:30 Closing",
    )

    @Test
    fun `four lines, counted from the top`() {
        assertEquals(chapters.take(4), descriptionPreview(chapters, 4))
    }

    @Test
    fun `leading blank lines are dropped`() {
        val lines = listOf("", "   ", "0:00 Opening", "1:12 The claim")
        assertEquals(listOf("0:00 Opening", "1:12 The claim"), descriptionPreview(lines, 4))
    }

    @Test
    fun `blank lines inside the window do not spend it`() {
        val lines = listOf("0:00 Opening", "", "1:12 The claim", "", "2:40 Evidence", "4:05 X", "6:30 Y")
        val preview = descriptionPreview(lines, 3)
        assertEquals(listOf("0:00 Opening", "", "1:12 The claim", "", "2:40 Evidence"), preview)
        assertEquals(3, preview.count { it.isNotBlank() })
    }

    @Test
    fun `a description with nothing hidden gets no chevron`() {
        assertFalse(descriptionHasMore(chapters.take(4), 4))
        assertTrue(descriptionHasMore(chapters, 4))
    }

    @Test
    fun `a short description shows in full`() {
        assertEquals(chapters.take(2), descriptionPreview(chapters.take(2), 4))
    }
}
