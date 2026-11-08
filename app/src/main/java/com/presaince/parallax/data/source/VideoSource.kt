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
    suspend fun channelUploads(channelUrl: String, page: String? = null): AppResult<Page<VideoSummary>>
    suspend fun channelInfo(channelUrl: String): AppResult<Channel>
    suspend fun subtitles(id: String): AppResult<SubtitleTrack>
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
