/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.AiChapterEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.source.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI chapters for videos whose description has none. Cache first, then one model call; nothing is
 * fetched at all while the model features are off.
 */
class AiChaptersEngine(
    private val source: VideoSource,
    private val db: GlassTubeDb,
    private val client: LlmClient,
    private val store: ResyncStore,
) {

    suspend fun chapters(videoId: String): AppResult<List<AiChapter>> =
        withContext(Dispatchers.IO) {
            val settings = store.load()
            if (!settings.ready) {
                return@withContext AppResult.Err(
                    "AI model features are off — turn them on in Resync settings",
                )
            }

            val cached = db.aiChapters().forVideo(videoId)
            if (cached.isNotEmpty()) {
                return@withContext AppResult.Ok(
                    cached.map { AiChapter(it.atSec, it.title) },
                )
            }

            val track = when (val result = source.subtitles(videoId)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> return@withContext AppResult.Err(
                    "No readable captions: ${result.message}",
                )
            }

            when (
                val reply = client.complete(
                    settings = settings,
                    system = AiChaptersPrompt.systemPrompt(),
                    user = AiChaptersPrompt.userPrompt(AiChaptersPrompt.sample(track.cues)),
                )
            ) {
                is AppResult.Err -> AppResult.Err("Model call failed: ${reply.message}")
                is AppResult.Ok -> {
                    val chapters = AiChaptersPrompt.parseResponse(reply.value)
                    if (chapters.isEmpty()) {
                        AppResult.Err("The model returned no usable chapters")
                    } else {
                        val now = System.currentTimeMillis()
                        db.aiChapters().saveAll(
                            chapters.map { chapter ->
                                AiChapterEntity(
                                    videoId = videoId,
                                    atSec = chapter.atSec,
                                    title = chapter.title,
                                    model = settings.model,
                                    createdAt = now,
                                )
                            },
                        )
                        AppResult.Ok(chapters)
                    }
                }
            }
        }
}
