/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import com.presaince.glasstube.data.sponsorblock.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a long block is worth warming. The prefetch itself needs a player, but the decision that
 * spends the bytes is a pure rule: ahead, longer than a buffer, outside the buffer, inside the
 * lead window, and not the one already fetched.
 */
class SponsorPrefetchTest {

    private val sponsor = Segment(startMs = 120_000, endMs = 420_000, category = "sponsor")

    @Test
    fun `a long block ahead is warmed at its far side`() {
        assertEquals(
            420_000L,
            sponsorPrefetchTarget(
                positionMs = 100_000,
                bufferedMs = 160_000,
                segments = listOf(sponsor),
                prefetchedEndMs = null,
            ),
        )
    }

    @Test
    fun `a far side the player already holds needs nothing`() {
        assertNull(
            sponsorPrefetchTarget(
                positionMs = 100_000,
                bufferedMs = 500_000,
                segments = listOf(sponsor),
                prefetchedEndMs = null,
            ),
        )
    }

    @Test
    fun `the same destination is never fetched twice`() {
        assertNull(
            sponsorPrefetchTarget(
                positionMs = 100_000,
                bufferedMs = 160_000,
                segments = listOf(sponsor),
                prefetchedEndMs = 420_000,
            ),
        )
    }

    @Test
    fun `a block further than the lead window waits`() {
        val later = Segment(startMs = 250_000, endMs = 400_000, category = "sponsor")
        assertNull(
            sponsorPrefetchTarget(
                positionMs = 0,
                bufferedMs = 30_000,
                segments = listOf(later),
                prefetchedEndMs = null,
            ),
        )
    }

    @Test
    fun `a block shorter than the forward buffer is not worth a connection`() {
        val intro = Segment(startMs = 50_000, endMs = 70_000, category = "intro")
        assertNull(
            sponsorPrefetchTarget(
                positionMs = 40_000,
                bufferedMs = 30_000,
                segments = listOf(intro),
                prefetchedEndMs = null,
            ),
        )
    }

    @Test
    fun `a block behind the playhead is not coming back`() {
        assertNull(
            sponsorPrefetchTarget(
                positionMs = 200_000,
                bufferedMs = 210_000,
                segments = listOf(sponsor),
                prefetchedEndMs = null,
            ),
        )
    }

    @Test
    fun `the nearest of two long blocks is the one warmed`() {
        val far = Segment(startMs = 500_000, endMs = 900_000, category = "sponsor")
        assertEquals(
            420_000L,
            sponsorPrefetchTarget(
                positionMs = 100_000,
                bufferedMs = 160_000,
                segments = listOf(far, sponsor),
                prefetchedEndMs = null,
            ),
        )
    }
}
