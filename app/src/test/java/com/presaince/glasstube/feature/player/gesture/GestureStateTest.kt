/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStateTest {

    private val width = 1080f
    private val edge = 66f
    private val slop = 12f

    @Test
    fun `edges belong to the system`() {
        assertEquals(Zone.Edge, zoneFor(10f, width, edge))
        assertEquals(Zone.Edge, zoneFor(1070f, width, edge))
    }

    @Test
    fun `thirds decide brightness and volume`() {
        assertEquals(Zone.Left, zoneFor(200f, width, edge))
        assertEquals(Zone.Centre, zoneFor(540f, width, edge))
        assertEquals(Zone.Right, zoneFor(900f, width, edge))
    }

    @Test
    fun `no axis until slop is passed`() {
        assertEquals(Axis.None, resolveAxis(Zone.Left, 5f, 5f, slop))
    }

    @Test
    fun `horizontal wins when it dominates`() {
        assertEquals(Axis.Horizontal, resolveAxis(Zone.Left, 40f, 10f, slop))
        assertEquals(Axis.Horizontal, resolveAxis(Zone.Centre, -40f, 10f, slop))
    }

    @Test
    fun `vertical in every zone, because the middle is the minimise drag`() {
        assertEquals(Axis.Vertical, resolveAxis(Zone.Left, 4f, 40f, slop))
        assertEquals(Axis.Vertical, resolveAxis(Zone.Right, -4f, 40f, slop))
        assertEquals(Axis.Vertical, resolveAxis(Zone.Centre, 4f, 40f, slop))
        assertEquals(Axis.Vertical, resolveAxis(Zone.Edge, 4f, 40f, slop))
    }

    @Test
    fun `the top corners are dead zones for the picture tap`() {
        val zones = SurfaceTapZones.defaults()
        assertTrue(zones.any { it.contains(0.05f, 0.05f) })
        assertTrue(zones.any { it.contains(0.95f, 0.10f) })
        assertFalse(zones.any { it.contains(0.5f, 0.5f) })
        assertFalse(zones.any { it.contains(0.5f, 0.05f) })
    }

    @Test
    fun `double tap direction follows the half`() {
        assertEquals(-1, seekDirection(300f, width))
        assertEquals(1, seekDirection(800f, width))
    }

    @Test
    fun `a fast flick either way belongs to the ladder, not to a level`() {
        // The bug: a fast swipe up from the left third turned the volume up on its way to
        // fullscreen. Speed is pixels per millisecond, signed by direction.
        assertTrue(flickBelongsToLadder(Zone.Left, totalDy = -80f, pxPerMs = -2f, threshold = 1f))
        assertTrue(flickBelongsToLadder(Zone.Right, totalDy = -80f, pxPerMs = -2f, threshold = 1f))
        assertTrue(flickBelongsToLadder(Zone.Left, totalDy = 80f, pxPerMs = 2f, threshold = 1f))
    }

    @Test
    fun `a slow drag still owns the level under the finger`() {
        assertFalse(flickBelongsToLadder(Zone.Left, totalDy = -80f, pxPerMs = -0.3f, threshold = 1f))
        assertFalse(flickBelongsToLadder(Zone.Left, totalDy = 80f, pxPerMs = 0.2f, threshold = 1f))
    }

    @Test
    fun `the centre is already the ladder`() {
        assertFalse(flickBelongsToLadder(Zone.Centre, totalDy = 80f, pxPerMs = 4f, threshold = 1f))
    }
}
