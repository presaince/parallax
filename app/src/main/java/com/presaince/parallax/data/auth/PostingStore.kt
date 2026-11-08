/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.auth

import android.content.SharedPreferences
import com.presaince.parallax.data.secure.SecurePrefs

/**
 * Posting is hidden until enabled: with [enabled] false the app never constructs an OAuth client,
 * never opens a browser and never reads a token. That is the M6 gate's first half.
 *
 * A "desktop app" OAuth client is the only type whose loopback redirect works without the Google
 * Sign-In SDK, so the user supplies their own client id and secret.
 */
data class PostingSettings(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMs: Long = 0L,
) {
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    val signedIn: Boolean get() = !refreshToken.isNullOrBlank()
}

class PostingStore(
    private val prefs: SharedPreferences,
    private val secure: SecurePrefs,
) {

    fun load(): PostingSettings = PostingSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        clientId = secure.get(KEY_CLIENT_ID).orEmpty(),
        clientSecret = secure.get(KEY_CLIENT_SECRET).orEmpty(),
        accessToken = secure.get(KEY_ACCESS),
        refreshToken = secure.get(KEY_REFRESH),
        expiresAtMs = prefs.getLong(KEY_EXPIRES, 0L),
    )

    fun save(settings: PostingSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putLong(KEY_EXPIRES, settings.expiresAtMs)
            .apply()

        settings.clientId.takeIf { it.isNotBlank() }
            ?.let { secure.put(KEY_CLIENT_ID, it) }
        settings.clientSecret.takeIf { it.isNotBlank() }
            ?.let { secure.put(KEY_CLIENT_SECRET, it) }
        settings.accessToken?.let { secure.put(KEY_ACCESS, it) }
        settings.refreshToken?.let { secure.put(KEY_REFRESH, it) }
    }

    fun clearTokens() {
        secure.remove(KEY_ACCESS)
        secure.remove(KEY_REFRESH)
        prefs.edit().putLong(KEY_EXPIRES, 0L).apply()
    }

    private companion object {
        const val KEY_ENABLED = "posting_enabled"
        const val KEY_CLIENT_ID = "posting_client_id"
        const val KEY_CLIENT_SECRET = "posting_client_secret"
        const val KEY_ACCESS = "posting_access"
        const val KEY_REFRESH = "posting_refresh"
        const val KEY_EXPIRES = "posting_expires"
    }
}
