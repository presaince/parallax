/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The multi-region setting is an ordered list with a hard cap. Order matters because the first is
 * the main region, and the cap matters because every extra entry is a search pass.
 */
@RunWith(RobolectricTestRunner::class)
class ContentRegionsTest {

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
    fun `keeps the tap order and caps at three`() {
        writeContentRegions(context, listOf("GB", "US", "DE", "FR"))
        assertEquals(listOf("GB", "US", "DE"), readContentRegions(context))
        assertEquals("GB", readContentRegion(context))
    }

    @Test
    fun `drops codes the app does not offer`() {
        writeContentRegions(context, listOf("GB", "ZZ", "US"))
        assertEquals(listOf("GB", "US"), readContentRegions(context))
    }

    @Test
    fun `the single-region write clears the extras`() {
        writeContentRegions(context, listOf("GB", "US"))
        writeContentRegion(context, "PL")
        assertEquals(listOf("PL"), readContentRegions(context))
    }

    @Test
    fun `the default is System, not a country`() {
        assertEquals(listOf(SYSTEM_REGION), readContentRegions(context))
    }

    @Test
    fun `an empty write means System`() {
        writeContentRegions(context, listOf("GB"))
        writeContentRegions(context, emptyList())
        assertEquals(listOf(SYSTEM_REGION), readContentRegions(context))
    }

    @Test
    fun `one tap adds, removes and stops at the cap`() {
        assertEquals(listOf("GB"), toggleRegion(emptyList(), "GB"))
        assertEquals(listOf("GB", "US"), toggleRegion(listOf("GB"), "US"))
        assertEquals(listOf("GB"), toggleRegion(listOf("GB", "US"), "US"))
        assertEquals(listOf("GB", "US", "DE"), toggleRegion(listOf("GB", "US", "DE"), "FR"))
    }

    @Test
    fun `the last region falls back to System`() {
        assertEquals(listOf(SYSTEM_REGION), toggleRegion(listOf("GB"), "GB"))
    }

    @Test
    fun `a country replaces System and System replaces countries`() {
        assertEquals(listOf("GB"), toggleRegion(listOf(SYSTEM_REGION), "GB"))
        assertEquals(listOf(SYSTEM_REGION), toggleRegion(listOf("GB", "US"), SYSTEM_REGION))
        assertEquals(listOf(SYSTEM_REGION), toggleRegion(listOf(SYSTEM_REGION), SYSTEM_REGION))
    }

    @Test
    fun `System resolves to the phone's country`() {
        writeContentRegions(context, listOf(SYSTEM_REGION))
        val country = java.util.Locale.getDefault().country.uppercase()
        val expected = if (country.length == 2) listOf(country) else listOf("")

        assertEquals(expected, resolvedRegions(context))
    }
}
