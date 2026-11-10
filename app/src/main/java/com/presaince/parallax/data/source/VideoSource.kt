/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.model.Channel
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Page
import com.presaince.parallax.data.model.StreamSet
import com.presaince.parallax.data.model.SubtitleTrack
import com.presaince.parallax.data.model.VideoDetail
import com.presaince.parallax.data.model.VideoSummary

/**
 * The only boundary between the UI and YouTube. Nothing above this interface touches the
 * extractor, which is what keeps a fallback source and JVM tests possible.
 *
 * Members are added per milestone rather than declared ahead: a method that throws
 * NotImplementedError is a lie about what the app can do.
 */
interface VideoSource {
    val id: String

    suspend fun search(query: String): AppResult<Page<VideoSummary>>
    suspend fun video(id: String): AppResult<VideoDetail>
    suspend fun streams(id: String): AppResult<StreamSet>
    suspend fun comments(id: String, page: String? = null): AppResult<Page<Comment>>
    suspend fun related(id: String): AppResult<List<VideoSummary>>

    /**
     * The same list, with the cache skipped. Up next's reshuffle is the only caller: asking the
     * same id twice inside the cache window returns the same videos, which made the button look
     * like it did nothing.
     */
    suspend fun relatedFresh(id: String): AppResult<List<VideoSummary>> = related(id)
    suspend fun channelUploads(channelUrl: String, page: String? = null): AppResult<Page<VideoSummary>>
    suspend fun channelInfo(channelUrl: String): AppResult<Channel>
    suspend fun subtitles(id: String): AppResult<SubtitleTrack>

    /**
     * One of a channel's tabs by its filter name: "videos", "shorts", "livestreams". Sources that
     * cannot switch tabs serve their uploads, so the channel page still has something to show.
     */
    suspend fun channelTab(
        channelUrl: String,
        filter: String,
        page: String? = null,
    ): AppResult<Page<VideoSummary>> = channelUploads(channelUrl, page)

    /** Drops any cached streams for [id], so the next [streams] call fetches fresh signed URLs. */
    suspend fun invalidateStreams(id: String) { /* default no-op */ }
}

/** Both youtu.be/ID and youtube.com/watch?v=ID, plus the /shorts/ form. */
fun videoIdFromUrl(url: String): String? {
    val patterns = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}

/** A shared link's clip bounds in milliseconds, when there is a single moment at all. */
data class ShareClip(val startMs: Long?, val endMs: Long?)

/** Parses `t=`, `start=`/`end=` and the "1:20–2:05" line a shared clip carries. */
fun shareClipFrom(text: String): ShareClip {
    val query = text.substringAfter("?", "").substringBefore(" ")
    fun param(name: String): String? = Regex("""[?&]$name=([^&#\s]+)""").find(text)?.groupValues?.get(1)
    val seconds = { raw: String -> parseYouTubeTime(raw) }
    val start = param("start")?.let(seconds) ?: param("t")?.let(seconds)
    val end = param("end")?.let(seconds)
    if (start != null && end != null && end > start) return ShareClip(start, end)
    // The second line of a shared clip: "link&t=80s" then "1:20–2:05" on the next line.
    val range = Regex("""(\d+(?::\d{1,2}){0,2})\s*[–—-]\s*(\d+(?::\d{1,2}){0,2})""").find(text)
    if (range != null) {
        val a = parseColonRange(range.groupValues[1])
        val b = parseColonRange(range.groupValues[2])
        if (a != null && b != null && b > a) return ShareClip(a, b)
    }
    return ShareClip(start, null)
}

/** A YouTube time: plain seconds ("90"), "90s", "1m30s" or "45m" in milliseconds. */
fun parseYouTubeTime(raw: String): Long? {
    val t = raw.trim().lowercase()
    if (t.isEmpty()) return null
    val total = if (t.contains('h') || t.contains('m') || t.contains('s')) {
        val h = Regex("""(\d+)\s*h""").find(t)?.groupValues?.get(1)?.toLong() ?: 0L
        val m = Regex("""(\d+)\s*m""").find(t)?.groupValues?.get(1)?.toLong() ?: 0L
        val s = Regex("""(\d+)\s*s""").find(t)?.groupValues?.get(1)?.toLong() ?: 0L
        if (h == 0L && m == 0L && s == 0L && !Regex("""\d+""").containsMatchIn(t)) return null
        (h * 3600 + m * 60 + s) * 1000L
    } else {
        t.toLongOrNull()?.times(1000) ?: return null
    }
    return total
}

/** "1:20" or "1:02:30" to milliseconds. */
private fun parseColonRange(raw: String): Long? {
    val parts = raw.split(":").map { it.toLongOrNull() }
    if (parts.any { it == null } || parts.isEmpty()) return null
    var seconds = 0L
    for (p in parts) seconds = seconds * 60 + p!!
    return seconds * 1000L
}
