/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.attempt
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * One HTTP call to an OpenAI-compatible chat completions endpoint. The user picks the endpoint and
 * the model, so this stays a thin client rather than an SDK: no provider lock-in, no hidden retry.
 */
class LlmClient(private val http: OkHttpClient) {

    suspend fun complete(
        settings: ResyncSettings,
        system: String,
        user: String,
    ): AppResult<String> = withContext(Dispatchers.IO) {
        attempt {
            val body = JSONObject()
                .put("model", settings.model)
                .put("temperature", 0)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)),
                )
                .toString()

            val request = Request.Builder()
                .url(settings.endpoint)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", CONTENT_TYPE)
                .post(body.toRequestBody(MEDIA_JSON))
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("model call failed: HTTP ${response.code} ${text.take(160)}")
                }
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }
    }

    private companion object {
        const val CONTENT_TYPE = "application/json"
        val MEDIA_JSON = "application/json".toMediaType()
    }
}
