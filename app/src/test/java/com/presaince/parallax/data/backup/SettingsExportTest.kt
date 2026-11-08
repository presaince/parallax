/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * An export file leaves the device, so the one rule that cannot regress is that no key or token is
 * in it. The rest pins what a person expects to carry to a new install.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsExportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SettingsExport.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun prefs() =
        context.getSharedPreferences(SettingsExport.PREFS, Context.MODE_PRIVATE).edit()

    @Test
    fun `no secret value ever reaches the file`() {
        prefs()
            .putString("resync_key", "sk-parallax-secret")
            .putString("resync_api_key", "sk-parallax-secret-2")
            .putString("oauth_access_token", "ya29.access-token")
            .putString("oauth_refresh_token", "1//refresh-token")
            .putString("client_secret", "gocspx-client-secret")
            .commit()

        val json = SettingsExport.json(context)

        assertFalse(json.contains("sk-parallax-secret"))
        assertFalse(json.contains("ya29.access-token"))
        assertFalse(json.contains("1//refresh-token"))
        assertFalse(json.contains("gocspx-client-secret"))
        // The key names must not travel either: a reader should not learn what to ask for.
        assertFalse(json.contains("resync_key"))
        assertFalse(json.contains("oauth_access_token"))
    }

    @Test
    fun `settings that are safe to carry are exported`() {
        prefs()
            .putString("theme_mode", "LIGHT")
            // The stored key is the hue slot, not the spoken label ("Violet" is h280).
            .putString("accent", "h280")
            .putBoolean("sponsor_skip", false)
            .putFloat("playback_speed", 1.5f)
            .putStringSet("sponsor_categories", setOf("music_offtopic", "intro"))
            .putString("resync_endpoint", "https://api.example/v1")
            .putString("resync_model", "some-model")
            .putString("search_history", "lofi\nkotlin\n\n")
            .commit()

        val json = JSONObject(SettingsExport.json(context))

        assertEquals("parallax", json.getString("app"))
        assertEquals(1, json.getInt("version"))

        val appearance = json.getJSONObject("appearance")
        assertEquals("LIGHT", appearance.getString("theme"))
        assertEquals("h280", appearance.getString("accent"))

        val playback = json.getJSONObject("playback")
        assertFalse(playback.getBoolean("sponsorSkip"))
        assertEquals(1.5f, playback.getDouble("speed").toFloat(), 0.0001f)
        // Sorted, so two exports of the same settings are byte-identical.
        val categories = playback.getJSONArray("sponsorCategories")
        assertEquals(2, categories.length())
        assertEquals("intro", categories.getString(0))
        assertEquals("music_offtopic", categories.getString(1))

        val resync = json.getJSONObject("resync")
        assertFalse(resync.getBoolean("enabled"))
        assertEquals("https://api.example/v1", resync.getString("endpoint"))
        assertEquals("some-model", resync.getString("model"))

        // Blank lines in the stored history are not search terms.
        val history = json.getJSONObject("search").getJSONArray("history")
        assertEquals(2, history.length())
        assertEquals("lofi", history.getString(0))
        assertEquals("kotlin", history.getString(1))
    }

    @Test
    fun `an empty install still produces a readable file`() {
        val json = JSONObject(SettingsExport.json(context))

        assertTrue(json.has("appearance"))
        assertTrue(json.has("playback"))
        assertEquals(0, json.getJSONObject("search").getJSONArray("history").length())
    }
}
