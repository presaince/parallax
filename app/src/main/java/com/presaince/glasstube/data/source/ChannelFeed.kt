/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.model.VideoSummary
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The freshest videos from one channel, without the extractor. YouTube publishes a free per-channel
 * feed (latest 15, no key, no quota), which is what a "new uploads" page actually needs. The feed
 * carries no duration; a caller that filters on length treats it as unknown, which every filter
 * already allows.
 */
interface ChannelFeed {
    suspend fun latest(channelUrl: String, limit: Int = 15): AppResult<List<VideoSummary>>
}

class RssChannelFeed(
    private val http: OkHttpClient,
    private val log: AppLog? = null,
) : ChannelFeed {

    private val cache = ConcurrentHashMap<String, Pair<Long, List<VideoSummary>>>()

    override suspend fun latest(channelUrl: String, limit: Int): AppResult<List<VideoSummary>> =
        withContext(Dispatchers.IO) {
            cache[channelUrl]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.let {
                return@withContext AppResult.Ok(it.second.take(limit))
            }
            val channelId = channelIdFromUrl(channelUrl)
                ?: return@withContext AppResult.Err("Not a channel id URL: $channelUrl")
            try {
                val request = Request.Builder()
                    .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                    .header("User-Agent", USER_AGENT)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AppResult.Err("channel feed: HTTP ${response.code}")
                    }
                    val items = parseChannelFeed(response.body?.string().orEmpty(), limit)
                    cache[channelUrl] = System.currentTimeMillis() to items
                    AppResult.Ok(items)
                }
            } catch (e: IOException) {
                log?.warn("rss", "channel feed failed: ${e.message?.take(120)}")
                AppResult.Err(e.message ?: "channel feed failed", e)
            }
        }

    private companion object {
        const val TTL_MS = 30L * 60 * 1000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/** `/channel/UC…` is the only form the free feed accepts; handles need the extractor first. */
fun channelIdFromUrl(url: String): String? =
    Regex("""/channel/(UC[A-Za-z0-9_-]{22})""").find(url)?.groupValues?.get(1)

/**
 * One channel's feed as summaries. Pure so the shape is pinned without a socket. Entries are
 * self-contained XML; a regex is enough here because the document is generated, not hand-written,
 * and the failure mode is a missing item rather than one that cannot be fetched.
 */
fun parseChannelFeed(xml: String, limit: Int = 15): List<VideoSummary> {
    if (xml.isBlank()) return emptyList()
    return ENTRIES.findAll(xml)
        .mapNotNull { match ->
            val block = match.groupValues[1]
            val id = tag(block, "yt:videoId") ?: return@mapNotNull null
            val title = unescape(tag(block, "title").orEmpty()).trim()
            if (title.isEmpty()) return@mapNotNull null
            val channelName = unescape(
                Regex("""<author>\s*<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1).orEmpty(),
            ).trim()
            VideoSummary(
                id = id,
                url = "https://www.youtube.com/watch?v=$id",
                title = title,
                channelName = channelName,
                // The feed does not say how long a video is; 0 is the code's "unknown".
                durationSec = 0,
                thumbnailUrl = Regex("""<media:thumbnail[^>]*\burl="([^"]+)"""")
                    .find(block)?.groupValues?.get(1),
                channelUrl = tag(block, "yt:channelId")?.let {
                    "https://www.youtube.com/channel/$it"
                },
                publishedAtMs = tag(block, "published")?.let { published ->
                    runCatching { OffsetDateTime.parse(published).toInstant().toEpochMilli() }
                        .getOrNull()
                },
                viewCount = Regex("""<media:statistics[^>]*\bviews="(\d+)"""")
                    .find(block)?.groupValues?.get(1)?.toLongOrNull(),
            )
        }
        .distinctBy { it.id }
        .take(limit)
        .toList()
}

private val ENTRIES =
    Regex("""<entry(?:\s[^>]*)?>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)

private fun tag(block: String, name: String): String? =
    Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
        .find(block)?.groupValues?.get(1)?.trim()

private val ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
)

/** Text nodes carry entities only; tags inside a title have already been stripped by YouTube. */
internal fun unescape(raw: String): String =
    Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""").replace(raw) { match ->
        val body = match.groupValues[1]
        val codePoint = when {
            body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)
            body.startsWith("#") -> body.drop(1).toIntOrNull()
            else -> null
        }
        when {
            codePoint != null && Character.isValidCodePoint(codePoint) ->
                String(Character.toChars(codePoint))
            else -> ENTITIES[body] ?: match.value
        }
    }
