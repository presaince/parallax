/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The storyboard cell a time falls in: which page image, and which rectangle of that image's
 * pixels. The crop is drawn from these numbers, so a wrong column shows a neighbouring moment and
 * a page past the end has to say "nothing" rather than stretch another page.
 */
class PreviewFramesTest {

    private fun frames(x: Int, y: Int, pages: Int = 2) = PreviewFrames(
        urls = (1..pages).map { "https://i.ytimg.com/sb/$it.jpg" },
        framesPerPageX = x,
        framesPerPageY = y,
        frameWidth = 160,
        frameHeight = 90,
        durationPerFrameMs = 1_000,
        totalCount = x * y * pages,
    )

    @Test
    fun `the first frame is the top left of the first page`() {
        assertEquals(StoryboardCell(0, 0, 0, 160, 90), frames(4, 5).cellAt(0))
    }

    @Test
    fun `a frame walks across the row before it moves down`() {
        val storyboard = frames(4, 5)
        assertEquals(StoryboardCell(0, 3 * 160, 0, 160, 90), storyboard.cellAt(3_000))
        assertEquals(StoryboardCell(0, 0, 90, 160, 90), storyboard.cellAt(4_000))
        assertEquals(StoryboardCell(0, 160, 2 * 90, 160, 90), storyboard.cellAt(9_000))
    }

    @Test
    fun `a frame on the second page reports that page`() {
        val storyboard = frames(4, 5)
        assertEquals(StoryboardCell(1, 0, 0, 160, 90), storyboard.cellAt(20_000))
        assertEquals(StoryboardCell(1, 3 * 160, 4 * 90, 160, 90), storyboard.cellAt(39_000))
    }

    @Test
    fun `a frame past the end is the last cell, not one past it`() {
        val storyboard = frames(4, 5)
        assertEquals(storyboard.cellAt(39_000), storyboard.cellAt(400_000))
    }

    @Test
    fun `a frame whose page is not in the list has no cell`() {
        // One page of urls, but the count claims four pages' worth of frames. Nothing honest to
        // draw, so the caller falls back rather than cropping a cell that is not there.
        val storyboard = frames(4, 5, pages = 1).copy(totalCount = 4 * 5 * 4)
        assertNull(storyboard.cellAt(60_000))
    }

    @Test
    fun `a storyboard with no pages has no cell`() {
        assertNull(frames(4, 5, pages = 0).cellAt(1_000))
    }

    @Test
    fun `a broken grid has no cell`() {
        assertNull(frames(0, 5).cellAt(1_000))
        assertNull(frames(4, 5).copy(durationPerFrameMs = 0).cellAt(1_000))
    }
}
