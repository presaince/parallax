/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.security.MessageDigest

data class Segment(val startMs: Long, val endMs: Long, val category: String) {
    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/**
 * SponsorBlock's public API. The video id never leaves the device: the query is the first two
 * bytes of its SHA-256, and every video starting with that prefix comes back.
 */
class SponsorBlock(private val client: OkHttpClient) {

    private val cache = LinkedHashMap<String, List<Segment>>()

    suspend fun segments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        cache[videoId]?.let { return@withContext it }

        val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
            .addPathSegment(prefix(videoId))
            .addQueryParameter("categories", CATEGORIES)
            .build()

        val body = runCatching {
            client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string().orEmpty()
            }
        }.getOrElse { return@withContext emptyList() }

        val parsed = parse(videoId, body)
        cache[videoId] = parsed
        if (cache.size > 100) cache.remove(cache.keys.first())
        parsed
    }

    private fun parse(videoId: String, body: String): List<Segment> = runCatching {
        val array = JSONArray(body)
        buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                if (entry.optString("videoID") != videoId) continue
                val segments = entry.optJSONArray("segments") ?: continue
                for (j in 0 until segments.length()) {
                    val segment = segments.optJSONObject(j) ?: continue
                    val range = segment.optJSONArray("segment") ?: continue
                    add(
                        Segment(
                            startMs = (range.optDouble(0, 0.0) * 1000).toLong(),
                            endMs = (range.optDouble(1, 0.0) * 1000).toLong(),
                            category = segment.optString("category"),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun prefix(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
        return digest.take(2).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        // Ask for every category we can display, then filter on the device: the user may change
        // their mind without a new request.
        const val CATEGORIES =
            """["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic","filler"]"""
    }
}
