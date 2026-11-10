/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.resync

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.ResyncEntity
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Cue
import com.presaince.glasstube.data.source.VideoSource
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResyncOutcome(
    val moments: List<AlignedMoment>,
    val trackLanguage: String,
    val calls: Int,
)

/**
 * Ties the pieces together: cues from the extractor, prompts from [ResyncPrompt], one HTTP call per
 * batch, results in Room. Anything already aligned against the same cue slice is not sent again.
 */
class ResyncEngine(
    private val source: VideoSource,
    private val db: GlassTubeDb,
    private val client: LlmClient,
    private val store: ResyncStore,
) {

    suspend fun align(videoId: String, comments: List<Comment>): AppResult<ResyncOutcome> =
        withContext(Dispatchers.IO) {
            val settings = store.load()
            if (!settings.enabled) {
                return@withContext AppResult.Err("Timestamp Resync is off — turn it on in Resync settings")
            }
            if (settings.apiKey.isBlank()) {
                return@withContext AppResult.Err("Add your own API key in Resync settings")
            }

            val timestamped = ResyncPrompt.timestamped(comments)
            if (timestamped.isEmpty()) {
                return@withContext AppResult.Err("None of the loaded comments carry a timestamp")
            }

            val track = when (val result = source.subtitles(videoId)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> return@withContext AppResult.Err(
                    "No readable captions: ${result.message}",
                )
            }

            val batches = ResyncPrompt.batches(timestamped, track.cues)
            val cached = db.resync().forVideo(videoId)
            var calls = 0

            for (batch in batches) {
                val hash = contextHash(batch.cues)
                val alreadyDone = batch.comments.all { comment ->
                    cached.any { it.commentId == comment.commentId && it.contextHash == hash }
                }
                if (alreadyDone) continue

                val originals = batch.comments.associate { it.commentId to it.originalSec }
                when (
                    val reply = client.complete(
                        settings = settings,
                        system = ResyncPrompt.systemPrompt(),
                        user = ResyncPrompt.userPrompt(batch.comments, batch.cues),
                    )
                ) {
                    is AppResult.Ok -> {
                        calls++
                        val moments = ResyncPrompt.parseResponse(reply.value, originals)
                        if (moments.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            db.resync().saveAll(
                                moments.map { moment ->
                                    ResyncEntity(
                                        videoId = videoId,
                                        commentId = moment.commentId,
                                        originalSec = originals.getValue(moment.commentId),
                                        alignedSec = moment.seconds,
                                        model = settings.model,
                                        createdAt = now,
                                        contextHash = hash,
                                    )
                                },
                            )
                        }
                    }
                    is AppResult.Err -> return@withContext AppResult.Err(
                        "Model call failed: ${reply.message}",
                    )
                }
            }

            AppResult.Ok(
                ResyncOutcome(
                    moments = storedMoments(videoId),
                    trackLanguage = track.language.ifBlank { "unknown" },
                    calls = calls,
                ),
            )
        }

    /** Cached alignment for one video, ready to show without touching the network. */
    suspend fun stored(videoId: String): List<AlignedMoment> = withContext(Dispatchers.IO) {
        storedMoments(videoId)
    }

    private suspend fun storedMoments(videoId: String): List<AlignedMoment> =
        db.resync().forVideo(videoId).map {
            AlignedMoment(commentId = it.commentId, seconds = it.alignedSec, confidence = 1f)
        }

    private fun contextHash(cues: List<Cue>): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(ResyncPrompt.formatCues(cues).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
