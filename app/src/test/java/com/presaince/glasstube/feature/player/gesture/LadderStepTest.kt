/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The centre drag's step rule, shared by the player and the gesture lesson. A lesson that stepped
 * on a slow short drag would teach a gesture the player ignores.
 */
class LadderStepTest {

    @Test
    fun `a slow long drag down is a step down`() {
        assertEquals(LadderStep.Down, ladderStep(down = 0.30f, up = 0f, elapsedMs = 900L))
    }

    @Test
    fun `a slow long drag up is a step up`() {
        assertEquals(LadderStep.Up, ladderStep(down = 0f, up = 0.30f, elapsedMs = 900L))
    }

    @Test
    fun `a slow short drag does nothing`() {
        assertEquals(LadderStep.None, ladderStep(down = 0.10f, up = 0f, elapsedMs = 900L))
        assertEquals(LadderStep.None, ladderStep(down = 0f, up = 0.10f, elapsedMs = 900L))
    }

    @Test
    fun `a short fast flick counts without the distance`() {
        assertEquals(LadderStep.Down, ladderStep(down = 0.05f, up = 0f, elapsedMs = 120L))
        assertEquals(LadderStep.Up, ladderStep(down = 0f, up = 0.05f, elapsedMs = 120L))
    }

    @Test
    fun `a flick too short to measure does nothing`() {
        assertEquals(LadderStep.None, ladderStep(down = 0.01f, up = 0f, elapsedMs = 120L))
    }

    @Test
    fun `a drag that travelled and came back steps the way it ended`() {
        assertEquals(LadderStep.Up, ladderStep(down = 0f, up = 0.40f, elapsedMs = 1200L))
    }
}
