/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.content.Context
import com.presaince.parallax.data.source.readContentRegions
import org.json.JSONArray
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
 * The restore is additive and whitelisted. These pin the two things that would be dangerous to
 * get wrong: a file cannot invent a preference the table does not know, and it cannot turn the AI
 * key on when the key is not on this device.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsImportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
    }

    private fun prefs() =
        context.getSharedPreferences(SettingsImport.PREFS, Context.MODE_PRIVATE)

    @Test
    fun `a file restores exactly the choices it names`() {
        prefs().edit()
            .putString("theme_mode", "DARK")
            .putString("accent", "h120")
            .putBoolean("sponsor_skip", false)
            .putBoolean("card_title", false)
            .putInt("stream_cache_mb", 4096)
            .putStringSet("sponsor_categories", setOf("intro", "outro"))
            .putString("search_history", "lofi\nkotlin")
            .commit()
        val exported = SettingsExport.values(context)

        prefs().edit().clear().commit()
        val summary = SettingsImport.apply(context, exported, apiKeyPresent = false)

        assertEquals("DARK", prefs().getString("theme_mode", null))
        assertEquals("h120", prefs().getString("accent", null))
        assertFalse(prefs().getBoolean("sponsor_skip", true))
        assertFalse(prefs().getBoolean("card_title", true))
        assertEquals(4096, prefs().getInt("stream_cache_mb", 0))
        assertEquals(setOf("intro", "outro"), prefs().getStringSet("sponsor_categories", null))
        assertEquals("lofi\nkotlin", prefs().getString("search_history", null))
        assertTrue(summary.settings > 0)
    }

    @Test
    fun `a preference the table does not know is never written`() {
        val forged = JSONObject().put(
            "appearance",
            JSONObject()
                .put("theme", "LIGHT")
                .put("resync_api_key", "sk-forged")
                .put("oauth_access_token", "ya29.forged"),
        )

        SettingsImport.apply(context, forged, apiKeyPresent = false)

        assertEquals("LIGHT", prefs().getString("theme_mode", null))
        assertFalse(prefs().contains("resync_api_key"))
        assertFalse(prefs().contains("oauth_access_token"))
    }

    @Test
    fun `ai comes back on only when a key is already here`() {
        val json = JSONObject().put(
            "resync",
            JSONObject()
                .put("enabled", true)
                .put("endpoint", "https://api.example/v1")
                .put("model", "some-model"),
        )

        SettingsImport.apply(context, json, apiKeyPresent = false)
        assertFalse(prefs().getBoolean("resync_enabled", true))
        assertEquals("https://api.example/v1", prefs().getString("resync_endpoint", null))

        SettingsImport.apply(context, json, apiKeyPresent = true)
        assertTrue(prefs().getBoolean("resync_enabled", false))
    }

    @Test
    fun `an unknown region is dropped rather than asked for`() {
        val json = JSONObject().put(
            "content",
            JSONObject().put("regions", JSONArray(listOf("UA", "ZZ"))),
        )

        SettingsImport.apply(context, json, apiKeyPresent = false)

        assertEquals(listOf("UA"), readContentRegions(context))
    }

    @Test
    fun `a file with nothing usable changes nothing and says so`() {
        val summary = SettingsImport.apply(context, JSONObject(), apiKeyPresent = false)

        assertEquals(0, summary.settings)
        assertTrue(summary.message.contains("no settings"))
        assertTrue(prefs().all.isEmpty())
    }
}
