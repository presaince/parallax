/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

import org.junit.Test

class QualityTest {

    @Test
    fun `Auto starts at first load height`() {
        assert(firstLoadHeight(0) == 720)
    }

    @Test
    fun `chosen height wins over first load cap`() {
        assert(firstLoadHeight(480) == 480)
        assert(firstLoadHeight(1080) == 1080)
    }
}
