/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level gesture's one rule: a finger acts on a band, and the ends of that band are a magnet.
 * The numbers below are the Pixel 7's: a 608px picture, 44dp of chrome at 2.625 density.
 */
class LevelBandTest {

    private val height = 608f
    private val top = 44f * 2.625f // 115.5
    private val bottomInset = 12f * 2.625f // 31.5
    private val magnet = 12f * 2.625f // 31.5
    private val band = levelBandOf(height, top, bottomInset, magnet)

    @Test
    fun `the band starts below the chrome and ends above the bottom air`() {
        assertEquals(115.5f, band.topPx, 0.01f)
        assertEquals(576.5f, band.bottomPx, 0.01f)
        assertFalse(band.contains(20f))
        assertTrue(band.contains(300f))
        assertFalse(band.contains(600f))
    }

    @Test
    fun `a finger over the chrome moves nothing`() {
        assertEquals(0f, levelTravelFactor(60f, band), 0f)
        assertEquals(1f, levelTravelFactor(300f, band), 0f)
        assertEquals(0f, levelTravelFactor(590f, band), 0f)
    }

    @Test
    fun `entering the top magnet means all the way up`() {
        // Started in the middle of the band, dragged into the last 31.5px under the chrome.
        assertEquals(1, levelEndReach(band.topPx + 10f, 300f, band))
        assertEquals(1, levelEndReach(band.topPx, 300f, band))
    }

    @Test
    fun `entering the bottom magnet means all the way down`() {
        assertEquals(-1, levelEndReach(band.bottomPx - 10f, 300f, band))
        assertEquals(-1, levelEndReach(band.bottomPx, 300f, band))
    }

    @Test
    fun `the middle of the band is not the end`() {
        assertEquals(0, levelEndReach(300f, 300f, band))
        assertEquals(0, levelEndReach(band.topPx + 40f, 300f, band))
        assertEquals(0, levelEndReach(band.bottomPx - 40f, 300f, band))
    }

    @Test
    fun `a drag that starts at the top of the screen cannot jump to the end`() {
        // 205539's case in reverse: a finger that starts at 95% of the height, above the band,
        // must not read as "the magnet" on its first pixel of movement. It can only lower.
        assertEquals(0, levelEndReach(30f, 20f, band))
        assertEquals(0, levelEndReach(band.topPx + 5f, 20f, band))
        // The other end is still free: the same drag may travel down and finish at zero.
        assertEquals(-1, levelEndReach(band.bottomPx - 5f, 20f, band))
    }

    @Test
    fun `a drag that starts at the bottom can still reach the top`() {
        assertEquals(1, levelEndReach(band.topPx + 5f, band.bottomPx - 2f, band))
    }

    @Test
    fun `a picture too short for the band keeps the magnets apart`() {
        // 60dp of picture between a 44dp stop and 12dp of air: smaller than two magnets.
        val tiny = levelBandOf(heightPx = 60f, topPx = 40f, bottomInsetPx = 10f, magnetPx = 30f)
        assertEquals(5f, tiny.magnetPx, 0.01f)
        assertTrue(tiny.topPx < tiny.bottomPx)
    }
}
