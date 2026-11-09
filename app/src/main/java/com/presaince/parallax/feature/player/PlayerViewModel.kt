/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.resync.AiChaptersEngine
import com.presaince.parallax.data.source.VideoSource
import com.presaince.parallax.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val title: String,
    val channelName: String,
    val channelUrl: String? = null,
    val likes: Long? = null,
    val views: Long? = null,
    val description: String = "",
    val aiChapters: List<Chapter> = emptyList(),
    val previewFrames: List<com.presaince.parallax.data.model.PreviewFrames> = emptyList(),
    val loading: Boolean = true,
    /** True once the detail (description, chapters source) has landed, after the video itself. */
    val detailReady: Boolean = false,
    val error: String? = null,
)

/**
 * The player loads in one order: the video to its first frame, then chapters, then
 * comments, then "Up next". Each stage starts only once the previous one has settled,
 * so the picture never waits on the words beneath it.
 */
enum class PlayerLoadStage { Video, Chapters, Comments, UpNext }

/** The highest stage that is ready, or the stage that is currently loading. */
fun playerStage(videoReady: Boolean, detailReady: Boolean, commentsReady: Boolean): PlayerLoadStage =
    when {
        !videoReady -> PlayerLoadStage.Video
        !detailReady -> PlayerLoadStage.Chapters
        !commentsReady -> PlayerLoadStage.Comments
        else -> PlayerLoadStage.UpNext
    }

class PlayerViewModel(
    private val source: VideoSource,
    private val playback: PlaybackController,
    private val db: ParallaxDb,
    private val summary: VideoSummary,
    private val log: AppLog,
    private val aiChaptersEngine: AiChaptersEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlayerUiState(
            title = summary.title,
            channelName = summary.channelName,
            channelUrl = summary.channelUrl,
            likes = summary.likeCount,
            views = summary.viewCount,
        ),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Guards a second load when the screen is re-entered while the first one is still running. */
    private var inFlight = false

    fun load() {
        if (inFlight) return
        inFlight = true
        viewModelScope.launch {
            try {
                loadOnce()
            } finally {
                inFlight = false
            }
        }
    }

    /**
     * Loads the same video again at a given position, for when the quality changes mid-watch.
     */
    fun reloadAt(positionMs: Long) {
        if (inFlight) return
        inFlight = true
        viewModelScope.launch {
            try {
                loadOnce(positionMs)
            } finally {
                inFlight = false
            }
        }
    }

    /**
     * The view model outlives the screen: it is keyed by the video, not by the route. Reopening a
     * video that was stopped therefore reuses a "loaded" state over a stopped player — a black
     * frame with no error. If the controller is not playing this video, load again.
     */
    fun ensurePlaying(playingVideoId: String?) {
        if (playingVideoId != summary.id) load()
    }

    /** Guards AI chapters so one open makes at most one request. */
    private var aiChaptersRequested = false

    /**
     * Fills the chapter gap with the model when the description has none. The engine checks its
     * cache first, and a failure is logged rather than shown: no chapters is not an error.
     */
    fun loadAiChapters() {
        if (aiChaptersRequested) return
        aiChaptersRequested = true
        viewModelScope.launch {
            when (val result = aiChaptersEngine.chapters(summary.id)) {
                is AppResult.Ok -> _state.update {
                    it.copy(aiChapters = result.value.map { chapter -> Chapter(chapter.atSec, chapter.title) })
                }
                is AppResult.Err -> log.info("chapters", "AI chapters for ${summary.id}: ${result.message}")
            }
        }
    }

    private suspend fun loadOnce(resumeOverrideMs: Long? = null) = coroutineScope {
        _state.update { it.copy(loading = true, error = null) }

        // Streams and the resume lookup run together, and the description rides along
        // without holding playback up. They all share one StreamInfo fetch.
        val detail = async { source.video(summary.id) }
        val resume = async(Dispatchers.IO) {
            resumeOverrideMs
                ?: runCatching { db.progress().find(summary.id)?.positionMs }.getOrNull()
        }

        val streamsStartedAt = System.currentTimeMillis()
        val streamsResult = source.streams(summary.id)
        log.info("source", "streams for ${summary.id} ready in ${System.currentTimeMillis() - streamsStartedAt}ms: ${if (streamsResult is AppResult.Ok) "ok" else "err"}")
        when (val result = streamsResult) {
            is AppResult.Ok -> {
                playback.play(
                    videoId = summary.id,
                    title = summary.title,
                    channel = summary.channelName,
                    thumbUrl = summary.thumbnailUrl,
                    streams = result.value,
                    resumeAtMs = resume.await(),
                )
                _state.update { it.copy(loading = false) }
            }
            is AppResult.Err -> {
                log.error("source", "streams failed for ${summary.id}: ${result.message}")
                _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }

        (detail.await() as? AppResult.Ok)?.let { loaded ->
            _state.update {
                it.copy(
                    detailReady = true,
                    title = loaded.value.title.ifBlank { it.title },
                    channelName = loaded.value.channelName.ifBlank { it.channelName },
                    channelUrl = loaded.value.channelUrl ?: it.channelUrl,
                    likes = loaded.value.likeCount ?: it.likes,
                    description = loaded.value.description,
                    previewFrames = loaded.value.previewFrames,
                )
            }
            loaded.value.likeCount?.let { likes ->
                // The listings never carry a like count, so this is the only place it is learned,
                // and without it the likes setting on a card has nothing to show. Fire and forget:
                // it never holds the load up.
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { db.videos().setLikeCount(summary.id, likes) }
                }
            }
        }
    }

    fun retry() = load()
}
