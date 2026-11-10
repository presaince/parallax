/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressBarVisibilityTest {

    @Test
    fun `the bar is hidden until a tap shows the controls`() {
        assertFalse(
            progressBarVisible(
                controlsVisible = false,
                barDragging = false,
                seekPillVisible = false,
                undoVisible = false,
            ),
        )
    }

    @Test
    fun `the bar is up while the controls are`() {
        assertTrue(
            progressBarVisible(
                controlsVisible = true,
                barDragging = false,
                seekPillVisible = false,
                undoVisible = false,
            ),
        )
    }

    @Test
    fun `a bar drag keeps the bar up after the controls time out`() {
        assertTrue(
            progressBarVisible(
                controlsVisible = false,
                barDragging = true,
                seekPillVisible = false,
                undoVisible = false,
            ),
        )
    }

    @Test
    fun `the bar steps aside for the seek pill`() {
        assertFalse(
            progressBarVisible(
                controlsVisible = true,
                barDragging = false,
                seekPillVisible = true,
                undoVisible = false,
            ),
        )
    }

    @Test
    fun `the bar steps aside for the undo offer`() {
        assertFalse(
            progressBarVisible(
                controlsVisible = true,
                barDragging = false,
                seekPillVisible = false,
                undoVisible = true,
            ),
        )
    }
}
