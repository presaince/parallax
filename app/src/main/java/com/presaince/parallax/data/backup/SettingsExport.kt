/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.content.Context
import com.presaince.parallax.ui.theme.readAccent
import com.presaince.parallax.ui.theme.readThemeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export: what a person would want to carry to a new install. No secrets - the Resync key
 * and the OAuth tokens stay on the device, because an export file is not a safe place for them.
 */
object SettingsExport {

    const val PREFS = "parallax"

    fun json(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val categories = prefs.getStringSet("sponsor_categories", null)

        return JSONObject()
            .put("app", "parallax")
            .put("version", 1)
            .put(
                "appearance",
                JSONObject()
                    .put("theme", readThemeMode(context).name)
                    .put("accent", readAccent(context).name),
            )
            .put(
                "playback",
                JSONObject()
                    .put("sponsorSkip", prefs.getBoolean("sponsor_skip", true))
                    .put("speed", prefs.getFloat("playback_speed", 1f))
                    .put("watchThreshold", prefs.getString("watch_threshold", "30s"))
                    .put(
                        "sponsorCategories",
                        JSONArray(categories?.sorted().orEmpty()),
                    ),
            )
            .put(
                "resync",
                JSONObject()
                    .put("enabled", prefs.getBoolean("resync_enabled", false))
                    .put("endpoint", prefs.getString("resync_endpoint", null))
                    .put("model", prefs.getString("resync_model", null)),
            )
            .put(
                "search",
                JSONObject().put(
                    "history",
                    JSONArray(
                        prefs.getString("search_history", null)
                            ?.split('\n')
                            ?.filter { it.isNotBlank() }
                            .orEmpty(),
                    ),
                ),
            )
            .toString(2)
    }
}
