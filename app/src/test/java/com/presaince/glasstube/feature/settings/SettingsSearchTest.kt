/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun titles(query: String) = searchSettings(query).map { it.title }

    private fun sectionOf(query: String): SettingsSectionId? =
        searchSettings(query).firstOrNull()?.section

    @Test
    fun `finds a row by its own words`() {
        assertTrue("Theme" in titles("theme"))
        assertTrue("Accent" in titles("accent"))
    }

    @Test
    fun `finds a row by the words a person types`() {
        assertTrue("Skip SponsorBlock" in titles("ads"))
        assertTrue("Import" in titles("takeout"))
        assertTrue("Model endpoint (AI)" in titles("api key"))
    }

    @Test
    fun `one letter out still finds the row`() {
        assertTrue("Skip SponsorBlock" in titles("sponsr"))
        assertTrue("Theme" in titles("apearance"))
        assertTrue("Speeds in the slider" in titles("speeds"))
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

    // The pages were regrouped: look on Appearance, feed on Feed, behaviour on Playback. These
    // fail if a row is moved without its search entry, which is how eleven rows once went missing.
    @Test
    fun `a look setting resolves to Appearance`() {
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("chapters"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("video page order"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("section labels"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("progress bar fullscreen"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("chapter timestamps"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("tint from video"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("animations"))
        assertEquals(SettingsSectionId.APPEARANCE, sectionOf("while a video loads"))
    }

    @Test
    fun `a feed setting resolves to Feed`() {
        assertEquals(SettingsSectionId.FEED, sectionOf("live"))
        assertEquals(SettingsSectionId.FEED, sectionOf("watched"))
        assertEquals(SettingsSectionId.FEED, sectionOf("count a watch"))
        assertEquals(SettingsSectionId.FEED, sectionOf("related at the end"))
        assertEquals(SettingsSectionId.FEED, sectionOf("bored skip subscribed"))
    }

    @Test
    fun `a behaviour setting resolves to Playback`() {
        assertEquals(SettingsSectionId.PLAYBACK, sectionOf("cache"))
        assertEquals(SettingsSectionId.PLAYBACK, sectionOf("quality"))
        assertEquals(SettingsSectionId.PLAYBACK, sectionOf("swipe to seek"))
        assertEquals(SettingsSectionId.PLAYBACK, sectionOf("gestures"))
        assertEquals(SettingsSectionId.PLAYBACK, sectionOf("picture in picture"))
    }

    @Test
    fun `run setup starts the wizard instead of opening About`() {
        val entry = searchSettings("wizard").firstOrNull { it.title == "Run setup again" }
        assertTrue(entry != null)
        assertTrue(entry!!.runs)
    }
}
