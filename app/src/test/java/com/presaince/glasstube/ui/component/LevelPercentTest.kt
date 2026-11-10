/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelPercentTest {

    @Test
    fun `a level reads as a whole percent`() {
        assertEquals("42%", levelPercent(0.421f))
        assertEquals("0%", levelPercent(0f))
        assertEquals("100%", levelPercent(1f))
    }

    @Test
    fun `nothing reads past a hundred`() {
        assertEquals("100%", levelPercent(1.7f))
    }

    @Test
    fun `nothing reads below zero`() {
        assertEquals("0%", levelPercent(-0.2f))
    }
}
