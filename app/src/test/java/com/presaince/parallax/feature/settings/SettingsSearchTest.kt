/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun titles(query: String) = searchSettings(query).map { it.title }

    @Test
    fun `finds a row by its own words`() {
        assertTrue("Theme" in titles("theme"))
        assertTrue("Accent" in titles("accent"))
    }

    @Test
    fun `finds a row by the words a person types`() {
        assertTrue("Skip SponsorBlock" in titles("ads"))
        assertTrue("Import backup" in titles("takeout"))
        assertTrue("Model endpoint (AI)" in titles("api key"))
    }

    @Test
    fun `one letter out still finds the row`() {
        assertTrue("Skip SponsorBlock" in titles("sponsr"))
        assertTrue("Theme" in titles("apearance"))
        assertTrue("Speed presets" in titles("speeds"))
    }

    @Test
    fun `a swapped pair costs one edit, not two`() {
        assertTrue("AI mode" in titles("commnets"))
        assertTrue("Logs" in titles("lgos"))
    }

    @Test
    fun `every word has to match`() {
        assertTrue(titles("sponsor speed").isEmpty())
    }

    @Test
    fun `nonsense finds nothing and an empty query is not a search`() {
        assertTrue(titles("zzzzzz").isEmpty())
        assertTrue(titles("").isEmpty())
        assertTrue(titles("   ").isEmpty())
    }
}
