/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A fresh install opens in Glass, following the phone's light or dark. The wizard promises that
 * on its first page, so the read has to agree before the page is drawn.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeDefaultsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `a fresh install is glass on the system base`() {
        assertEquals(ThemeMode.GLASS, readThemeMode(context))
        assertEquals(ThemeMode.SYSTEM, readGlassBase(context))
    }

    @Test
    fun `a stored choice is not overwritten`() {
        writeThemeMode(context, ThemeMode.LIGHT)
        writeGlassBase(context, ThemeMode.DARK)
        assertEquals(ThemeMode.LIGHT, readThemeMode(context))
        assertEquals(ThemeMode.DARK, readGlassBase(context))
    }
}
