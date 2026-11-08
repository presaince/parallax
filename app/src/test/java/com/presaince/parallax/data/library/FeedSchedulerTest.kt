/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.library

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSchedulerTest {

    @Test
    fun `jitter stays inside half an hour`() {
        val random = Random(7)
        repeat(500) {
            val delay = FeedScheduler.delayMs(random)
            assertTrue(
                "delay $delay out of band",
                delay in 12_600_000L..16_200_000L,
            )
        }
    }

    @Test
    fun `jitter is not constant`() {
        val random = Random(11)
        val values = (1..20).map { FeedScheduler.delayMs(random) }.toSet()
        assertTrue("only ${values.size} distinct delays", values.size > 1)
    }

    @Test
    fun `a fixed seed is reproducible`() {
        assertEquals(FeedScheduler.delayMs(Random(3)), FeedScheduler.delayMs(Random(3)))
    }
}
