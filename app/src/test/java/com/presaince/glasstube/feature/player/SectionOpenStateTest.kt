/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A section the reader folded stays folded on the next video. The page's open state is one
 * preference per section, so the key must be per-section and a missing key must answer the
 * default the page shipped with.
 */
@RunWith(RobolectricTestRunner::class)
class SectionOpenStateTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences(SectionOpenState.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `every section has its own key`() {
        val keys = PlayerSection.entries.map { SectionOpenState.key(it) }
        assertEquals(PlayerSection.entries.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith(SectionOpenState.KEY_PREFIX) })
    }

    @Test
    fun `before anything is folded the page answers its own defaults`() {
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Description))
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Sponsor))
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Notes))
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Comments))
        assertTrue(SectionOpenState.read(prefs, PlayerSection.Chapters))
        assertTrue(SectionOpenState.read(prefs, PlayerSection.UpNext))
    }

    @Test
    fun `a folded section is still folded on the next video`() {
        SectionOpenState.write(prefs, PlayerSection.Chapters, false)
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Chapters))
    }

    @Test
    fun `one section's answer does not become another's`() {
        SectionOpenState.write(prefs, PlayerSection.Description, true)
        assertTrue(SectionOpenState.read(prefs, PlayerSection.Description))
        assertFalse(SectionOpenState.read(prefs, PlayerSection.Comments))
        assertTrue(SectionOpenState.read(prefs, PlayerSection.Chapters))
    }
}
