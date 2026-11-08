/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.resync

import android.content.SharedPreferences
import com.presaince.parallax.data.secure.SecurePrefs

/**
 * Bring your own key, off by default. Nothing is sent anywhere until both the toggle is on and a
 * key is present, and the only thing that leaves the device is the cue slice for the comments
 * being aligned.
 */
data class ResyncSettings(
    val enabled: Boolean = false,
    val endpoint: String = DEFAULT_ENDPOINT,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
) {
    val ready: Boolean get() = enabled && apiKey.isNotBlank() && endpoint.startsWith("https://")

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

class ResyncStore(
    private val prefs: SharedPreferences,
    private val secure: SecurePrefs,
) {

    fun load(): ResyncSettings = ResyncSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        endpoint = prefs.getString(KEY_ENDPOINT, null)
            ?.takeIf { it.isNotBlank() }
            ?: ResyncSettings.DEFAULT_ENDPOINT,
        model = prefs.getString(KEY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: ResyncSettings.DEFAULT_MODEL,
        apiKey = secure.get(KEY_API_KEY).orEmpty(),
    )

    fun save(settings: ResyncSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_ENDPOINT, settings.endpoint.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .apply()
        if (settings.apiKey.isBlank()) {
            secure.remove(KEY_API_KEY)
        } else {
            secure.put(KEY_API_KEY, settings.apiKey.trim())
        }
    }

    private companion object {
        const val KEY_ENABLED = "resync_enabled"
        const val KEY_ENDPOINT = "resync_endpoint"
        const val KEY_MODEL = "resync_model"
        const val KEY_API_KEY = "resync_api_key"
    }
}
