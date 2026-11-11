/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedProgressTest {

    @Test
    fun `the line says how far, how much of it, and which channel`() {
        assertEquals(
            "12 of 47 · 25% · Hard Reset",
            refreshLabel(FeedProgress(done = 12, total = 47, channel = "Hard Reset")),
        )
    }

    @Test
    fun `the percentage floors, so 47 of 48 is not 100`() {
        assertEquals(97, FeedProgress(done = 47, total = 48).percent)
        assertEquals(100, FeedProgress(done = 48, total = 48).percent)
        assertEquals(0, FeedProgress(done = 0, total = 48).percent)
    }

    @Test
    fun `a refresh with nothing counted yet says zero and has no name`() {
        assertEquals("0 of 9 · 0%", refreshLabel(FeedProgress(done = 0, total = 9)))
    }

    @Test
    fun `no subscriptions is not a division by zero`() {
        assertEquals(0, FeedProgress(done = 0, total = 0).percent)
        assertEquals("0 of 0 · 0%", refreshLabel(FeedProgress(done = 0, total = 0)))
    }

    @Test
    fun `a blank channel name does not leave a dangling dot`() {
        assertEquals(
            "3 of 3 · 100%",
            refreshLabel(FeedProgress(done = 3, total = 3, channel = "   ")),
        )
    }
}
