/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.auth

import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.attempt
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Everything posting needs once the user has turned it on: token refresh, and one API call that
 * creates a top-level comment. Posting only — the app never reads or deletes anything with the
 * user's account.
 */
class PostingManager(
    private val http: OkHttpClient,
    private val store: PostingStore,
    private val auth: YouTubeAuth,
) {

    fun settings(): PostingSettings = store.load()

    fun save(enabled: Boolean, clientId: String, clientSecret: String) {
        val current = store.load()
        store.save(
            current.copy(
                enabled = enabled,
                clientId = clientId.trim(),
                clientSecret = clientSecret.trim(),
            ),
        )
    }

    /** Persists a freshly exchanged token pair. */
    fun storeTokens(tokens: Tokens) {
        val current = store.load()
        store.save(
            current.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: current.refreshToken,
                expiresAtMs = System.currentTimeMillis() + tokens.expiresInSec * 1000,
            ),
        )
    }

    fun signOut() = store.clearTokens()

    suspend fun post(videoId: String, text: String): AppResult<Unit> =
        insert(videoId = videoId, parentId = null, text = text)

    /** A reply is the same call with a parent id, on the comments resource instead of threads. */
    suspend fun reply(videoId: String, parentId: String, text: String): AppResult<Unit> =
        insert(videoId = videoId, parentId = parentId, text = text)

    private suspend fun insert(
        videoId: String,
        parentId: String?,
        text: String,
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        val settings = store.load()
        if (!settings.enabled) return@withContext AppResult.Err("Posting is off")
        if (text.isBlank()) return@withContext AppResult.Err("Nothing to post")

        when (val token = accessToken(settings)) {
            is AppResult.Err -> token
            is AppResult.Ok -> attempt {
                val snippet = JSONObject()
                    .put("videoId", videoId)
                    .put("textOriginal", text)
                if (parentId != null) snippet.put("parentId", parentId)

                val endpoint = if (parentId == null) {
                    "$API/commentThreads?part=snippet"
                } else {
                    "$API/comments?part=snippet"
                }
                val body = if (parentId == null) {
                    JSONObject().put(
                        "snippet",
                        JSONObject()
                            .put("videoId", videoId)
                            .put("topLevelComment", JSONObject().put("snippet", snippet)),
                    )
                } else {
                    JSONObject().put("snippet", snippet)
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer ${token.value}")
                    .post(body.toRequestBody(JSON))
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string().orEmpty()
                        throw IOException("post failed: HTTP ${response.code} ${error.take(200)}")
                    }
                }
            }
        }
    }

    private suspend fun accessToken(settings: PostingSettings): AppResult<String> {
        val access = settings.accessToken
        if (!access.isNullOrBlank() && settings.expiresAtMs - 60_000 > System.currentTimeMillis()) {
            return AppResult.Ok(access)
        }
        val refresh = settings.refreshToken
            ?: return AppResult.Err("Not signed in to Google")

        return when (
            val result = auth.refresh(settings.clientId, settings.clientSecret, refresh)
        ) {
            is AppResult.Ok -> {
                storeTokens(result.value)
                AppResult.Ok(result.value.accessToken)
            }
            is AppResult.Err -> result
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/youtube/v3"
        val JSON = "application/json".toMediaType()
    }
}
