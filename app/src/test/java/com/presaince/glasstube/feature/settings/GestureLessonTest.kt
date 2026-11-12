/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import com.presaince.glasstube.feature.player.gesture.LadderStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lesson's three-stop ladder and the rule that the fourth step needs the round trip. */
class GestureLessonTest {

    @Test
    fun `down walks to the bar and stops there`() {
        assertEquals(GestureLesson.MINI, GestureLesson.stepped(GestureLesson.NORMAL, LadderStep.Down))
        assertEquals(GestureLesson.BAR, GestureLesson.stepped(GestureLesson.MINI, LadderStep.Down))
        assertEquals(GestureLesson.BAR, GestureLesson.stepped(GestureLesson.BAR, LadderStep.Down))
    }

    @Test
    fun `up walks back to normal and stops there`() {
        assertEquals(GestureLesson.MINI, GestureLesson.stepped(GestureLesson.BAR, LadderStep.Up))
        assertEquals(GestureLesson.NORMAL, GestureLesson.stepped(GestureLesson.MINI, LadderStep.Up))
        assertEquals(GestureLesson.NORMAL, GestureLesson.stepped(GestureLesson.NORMAL, LadderStep.Up))
    }

    @Test
    fun `no step leaves the card where it is`() {
        assertEquals(GestureLesson.MINI, GestureLesson.stepped(GestureLesson.MINI, LadderStep.None))
        assertEquals(GestureLesson.BAR, GestureLesson.stepped(GestureLesson.BAR, LadderStep.None))
    }

    @Test
    fun `the fourth step needs the bar and the way back`() {
        assertFalse(GestureLesson.isComplete(3, GestureLesson.NORMAL, reachedBar = false))
        assertFalse(GestureLesson.isComplete(3, GestureLesson.MINI, reachedBar = true))
        assertFalse(GestureLesson.isComplete(2, GestureLesson.NORMAL, reachedBar = true))
        assertTrue(GestureLesson.isComplete(3, GestureLesson.NORMAL, reachedBar = true))
    }
}
