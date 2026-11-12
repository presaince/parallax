/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.SourceError
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.resync.AiChaptersEngine
import com.presaince.glasstube.data.source.VideoSource
import com.presaince.glasstube.playback.PlaybackController
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
    val previewFrames: List<com.presaince.glasstube.data.model.PreviewFrames> = emptyList(),
    val loading: Boolean = true,
    /** True once the detail (description, chapters source) has landed, after the video itself. */
    val detailReady: Boolean = false,
    val error: String? = null,
    /** Why the stream failed, when the source could classify it. Drives the copy and the retry. */
    val errorKind: SourceError? = null,
    /** Every track the video offers, empty when it has none. The CC control needs this list. */
    val captionTracks: List<com.presaince.glasstube.data.model.CaptionTrackInfo> = emptyList(),
    /** True once the track list has been asked for, so the control does not appear late. */
    val captionsLoaded: Boolean = false,
    /** The track being read, or null when the video has none. */
    val captionTrack: com.presaince.glasstube.data.model.CaptionTrackInfo? = null,
    /** The chosen track's cues, downloaded only while captions are on. */
    val captionCues: List<com.presaince.glasstube.data.model.Cue> = emptyList(),
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
    private val db: GlassTubeDb,
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

    /**
     * The track list, then the cues if captions are already on. Asked for once the first frame is
     * up, so the request never competes with the picture. An empty list is an answer, not a
     * failure: most videos have no captions and the CC control then has nothing to offer.
     */
    private var captionsRequested = false

    fun loadCaptions() {
        if (captionsRequested) return
        captionsRequested = true
        viewModelScope.launch {
            val tracks = when (val result = source.captionTracks(summary.id)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> {
                    log.info("captions", "track list for ${summary.id}: ${result.message}")
                    emptyList()
                }
            }
            val track = pickCaptionTrack(tracks, playback.state.value.captionLanguage)
            _state.update {
                it.copy(captionTracks = tracks, captionsLoaded = true, captionTrack = track)
            }
            if (playback.state.value.captionsOn) loadCues(track)
        }
    }

    /**
     * The CC menu's only entry point: a track means store its language and switch captions on,
     * null is Off. Both halves are written together because the menu has no "on" row of its own —
     * a tap on a track *is* the ask, and a track tap that left them off would be a tap that did
     * nothing.
     */
    fun chooseCaptionTrack(track: com.presaince.glasstube.data.model.CaptionTrackInfo?) {
        val pick = captionPick(track)
        val chosen = track?.takeIf { pick.on }
        val alreadyLoaded = chosen != null &&
            _state.value.captionTrack?.languageTag == chosen.languageTag &&
            _state.value.captionCues.isNotEmpty()
        playback.setCaptionLanguage(pick.languageTag)
        playback.setCaptionsEnabled(pick.on)
        // The old language's lines must not sit under a new language's fetch.
        _state.update {
            it.copy(
                captionTrack = chosen,
                captionCues = if (chosen == null || it.captionTrack?.languageTag != chosen.languageTag) {
                    emptyList()
                } else {
                    it.captionCues
                },
            )
        }
        if (chosen != null && !alreadyLoaded) loadCues(chosen)
    }

    /**
     * Captions were switched on somewhere else — the Playback setting, or a backup restore — while
     * this video is open. Nothing in the CC menu ran, so the cues have to be asked for here.
     */
    fun fetchCuesIfOn() {
        if (!playback.state.value.captionsOn) return
        val track = _state.value.captionTrack
        if (track == null) {
            // The list has not landed yet; loadCaptions fetches for itself when it does.
            if (!captionsRequested) loadCaptions()
            return
        }
        if (_state.value.captionCues.isEmpty()) loadCues(track)
    }

    /**
     * Downloads one track. A failure clears the cues rather than showing the previous language's
     * lines: the picture would then carry words that are not being said. One request per language
     * at a time, so the menu's own fetch and the screen's enable hook cannot both ask.
     */
    private var cuesLoadingFor: String? = null

    private fun loadCues(track: com.presaince.glasstube.data.model.CaptionTrackInfo?) {
        val chosen = track ?: return
        if (cuesLoadingFor == chosen.languageTag) return
        cuesLoadingFor = chosen.languageTag
        viewModelScope.launch {
            try {
                when (val result = source.captions(summary.id, chosen.languageTag)) {
                    is AppResult.Ok -> _state.update {
                        // A slow answer for a language the reader has already moved past is
                        // dropped rather than written over the current one.
                        if (it.captionTrack?.languageTag != chosen.languageTag) {
                            it
                        } else {
                            it.copy(captionCues = result.value.cues)
                        }
                    }
                    is AppResult.Err -> {
                        log.info("captions", "cues for ${summary.id} ${chosen.languageTag}: ${result.message}")
                        _state.update {
                            if (it.captionTrack?.languageTag != chosen.languageTag) {
                                it
                            } else {
                                it.copy(captionCues = emptyList())
                            }
                        }
                    }
                }
            } finally {
                if (cuesLoadingFor == chosen.languageTag) cuesLoadingFor = null
            }
        }
    }

    private suspend fun loadOnce(resumeOverrideMs: Long? = null) = coroutineScope {
        _state.update { it.copy(loading = true, error = null, errorKind = null) }

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
                    it.copy(
                        loading = false,
                        // A classified failure gets the plain sentence; the raw extractor text
                        // stays in the log where it is useful.
                        error = result.sourceError?.userMessage ?: result.message,
                        errorKind = result.sourceError,
                    )
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
