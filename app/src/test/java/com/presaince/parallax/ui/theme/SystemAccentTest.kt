/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAccentTest {

    @Test
    fun `system accent borrows the hue of the phone colour`() {
        // A Material blue, the colour a phone palette most often hands over.
        val accent = systemAccentOf(Color(0xFF4C6EF5))

        assertEquals(228f, accent.darkHue, 0.5f)
        assertEquals(accent.darkHue, accent.lightHue, 0f)
        assertEquals("System", accent.label)
    }

    @Test
    fun `a grey phone colour keeps the brand hue`() {
        val accent = systemAccentOf(Color(0xFF808080))

        assertEquals(Accents.cyan.darkHue, accent.darkHue, 0.01f)
    }
}
