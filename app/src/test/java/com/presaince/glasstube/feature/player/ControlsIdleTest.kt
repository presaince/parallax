/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controls' idle rule. The fault it exists for: the CC menu was anchored to the controls, the
 * idle timer hid the controls under it, and the language list closed while a finger was on it.
 */
class ControlsIdleTest {

    @Test
    fun `a playing picture with nothing open puts its controls away`() {
        assertTrue(shouldHideControls(visible = true, playing = true, overlay = PlayerOverlay.None))
    }

    @Test
    fun `a paused picture keeps them`() {
        assertFalse(shouldHideControls(visible = true, playing = false, overlay = PlayerOverlay.None))
    }

    @Test
    fun `an open popup keeps them`() {
        val popups = listOf(
            PlayerOverlay.Speed,
            PlayerOverlay.Quality,
            PlayerOverlay.Captions,
        )
        for (popup in popups) {
            assertFalse(popup.name, shouldHideControls(visible = true, playing = true, overlay = popup))
        }
    }

    @Test
    fun `controls that are already hidden are left hidden`() {
        // The effect runs again on every interaction; hiding twice must not flash anything.
        assertFalse(shouldHideControls(visible = false, playing = true, overlay = PlayerOverlay.None))
    }
}
