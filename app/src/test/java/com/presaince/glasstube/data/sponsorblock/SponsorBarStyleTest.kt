/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBarStyleTest {

    @Test
    fun `no stored key reads as the default`() {
        assertEquals(SponsorBarStyle.COVER, SponsorBarStyle.fromKey(null))
    }

    @Test
    fun `an unknown stored key reads as the default`() {
        assertEquals(SponsorBarStyle.COVER, SponsorBarStyle.fromKey("split"))
    }

    @Test
    fun `every style comes back from its own key`() {
        for (style in SponsorBarStyle.entries) {
            assertEquals(style, SponsorBarStyle.fromKey(style.key))
        }
    }
}
