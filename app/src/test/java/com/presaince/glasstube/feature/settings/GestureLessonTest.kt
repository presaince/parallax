/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lesson's three-stop ladder and the rule that the fourth step needs the round trip. */
class GestureLessonTest {

    private val threshold = 20f

    @Test
    fun `down walks to the bar and stops there`() {
        assertEquals(1, GestureLesson.nextLevel(GestureLesson.NORMAL, 30f, threshold))
        assertEquals(2, GestureLesson.nextLevel(GestureLesson.MINI, 30f, threshold))
        assertEquals(2, GestureLesson.nextLevel(GestureLesson.BAR, 30f, threshold))
    }

    @Test
    fun `up walks back to normal and stops there`() {
        assertEquals(1, GestureLesson.nextLevel(GestureLesson.BAR, -30f, threshold))
        assertEquals(0, GestureLesson.nextLevel(GestureLesson.MINI, -30f, threshold))
        assertEquals(0, GestureLesson.nextLevel(GestureLesson.NORMAL, -30f, threshold))
    }

    @Test
    fun `a short drag does not move a step`() {
        assertEquals(1, GestureLesson.nextLevel(GestureLesson.MINI, 10f, threshold))
        assertEquals(1, GestureLesson.nextLevel(GestureLesson.MINI, -10f, threshold))
    }

    @Test
    fun `the fourth step needs the bar and the way back`() {
        assertFalse(GestureLesson.isComplete(3, GestureLesson.NORMAL, reachedBar = false))
        assertFalse(GestureLesson.isComplete(3, GestureLesson.MINI, reachedBar = true))
        assertFalse(GestureLesson.isComplete(2, GestureLesson.NORMAL, reachedBar = true))
        assertTrue(GestureLesson.isComplete(3, GestureLesson.NORMAL, reachedBar = true))
    }
}
