/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.data.db.HistoryEntity
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.ProgressEntity
import com.presaince.parallax.data.model.StreamSet
import com.presaince.parallax.data.sponsorblock.Segment
import com.presaince.parallax.data.sponsorblock.SponsorBlock
import com.presaince.parallax.data.sponsorblock.SponsorCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sponsorSkip: Boolean = true,
    val sponsorCategories: Set<String> = SponsorCategories.default,
    val segments: List<Segment> = emptyList(),
    val lastSkip: Segment? = null,
    /** Set while something is loaded, so the shell can show a mini-player. */
    val videoId: String? = null,
    val videoTitle: String = "",
    /** The last mid-playback failure, so the screen can try once more without the user asking. */
    val playerError: String? = null,
    /** True once the current video has reached READY: the first-frame gate for lower-priority loads. */
    val firstFrameReady: Boolean = false,
)

/** A sponsor seek during buffering discards what ExoPlayer buffered and restarts it. */
internal fun shouldSkipSegment(playbackState: Int): Boolean = playbackState == Player.STATE_READY

/**
 * The one owner of the player. The service keeps it alive in the foreground; screens only read
 * [state] and call commands. Position-driven work (SponsorBlock markers and skip) lives here rather
 * than in a screen, so it keeps working with the activity gone.
 */
class PlaybackController(
    private val appContext: Context,
    private val sponsorBlock: SponsorBlock,
    private val prefs: SharedPreferences,
    private val db: ParallaxDb,
    private val log: AppLog,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        // Smaller start buffer: the defaults ask for 2.5s before the first frame, which is most
        // of the 6s Queueing wait when the pick is high-bitrate and the feed is fetching.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .build(),
        )
        .build()

    /** One factory for every play, instead of a fresh data source per video. */
    private val mediaFactory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext))

    private val _state = MutableStateFlow(
        PlaybackState(
            sponsorSkip = prefs.getBoolean(KEY_SPONSOR_SKIP, true),
            sponsorCategories = prefs.getStringSet(KEY_SPONSOR_CATEGORIES, null)
                ?.toSet()
                ?: SponsorCategories.default,
        ),
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Raised by a tap on the media notification; the nav opens the playing video and clears it. */
    private val _openPlayer = MutableStateFlow(false)
    val openPlayer: StateFlow<Boolean> = _openPlayer.asStateFlow()

    fun requestOpenPlayer() {
        _openPlayer.value = true
    }

    fun consumeOpenPlayer() {
        _openPlayer.value = false
    }

    private var segments: List<Segment> = emptyList()
    private var rawSegments: List<Segment> = emptyList()
    private var segmentsJob: Job? = null
    private var normalSpeed: Float = prefs.getFloat(KEY_SPEED, 1f)
    private var lastSkipEndMs = -1L
    private var currentVideoId: String? = null
    private var currentTitle: String = ""
    private var currentChannel: String = ""
    private var currentThumb: String? = null
    private var pendingResumeMs: Long? = null
    private var pendingSegmentsFetch = false
    private var ticksSinceSave = 0
    /** When [play] was called, so buffering time can be logged alongside fetch time. */
    private var playStartedAtMs = 0L
    /** True once READY for the current play has been logged. */
    private var readyLogged = false
    /** True between the deliberate stop/clear and the prepare at the start of [play]. */
    private var preparing = false

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                // Pausing is the moment a viewer leaves: save the position then, not five seconds
                // later, or backing out mid-video loses where you were.
                if (!isPlaying) saveProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        durationMs = player.duration.coerceAtLeast(0L),
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                    )
                }
                if (playbackState == Player.STATE_READY && currentVideoId != null && !readyLogged) {
                    readyLogged = true
                    _state.update { it.copy(firstFrameReady = true) }
                    log.info("player", "ready in ${System.currentTimeMillis() - playStartedAtMs}ms for $currentVideoId")
                }
                // IDLE with a video loaded means something took the media away. It is the only
                // clue when a stream stops on its own, so the log names it.
                if (playbackState == Player.STATE_IDLE && currentVideoId != null && !preparing) {
                    log.warn("player", "idle while loaded: $currentVideoId at ${player.currentPosition}ms")
                }
                val resume = pendingResumeMs
                if (playbackState == Player.STATE_READY && resume != null) {
                    pendingResumeMs = null
                    player.seekTo(resume.coerceIn(0, player.duration.coerceAtLeast(0)))
                    log.info("player", "resumed at ${resume}ms")
                }
                // Sponsor data is a separate request; starting it only now leaves the
                // connections and the bandwidth to the first frame.
                if (playbackState == Player.STATE_READY && pendingSegmentsFetch) {
                    pendingSegmentsFetch = false
                    currentVideoId?.let { fetchSegments(it) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // The HTTP status is the diagnosis: 403/429 means the signed URL was rejected,
                // not the decoder. The cause chain carries it, the top message does not.
                val detail = buildString {
                    append("${error.errorCodeName}: ${error.message}; video=$currentVideoId")
                    append(" cause=${error.cause?.javaClass?.simpleName}:${error.cause?.message}")
                    val cause = error.cause
                    if (cause != null) {
                        val responseCode = runCatching {
                            cause.javaClass.getMethod("getResponseCode").invoke(cause)
                        }.getOrNull()
                        if (responseCode != null) append(" http=$responseCode")
                    }
                }
                log.error("player", detail)
                _state.update { it.copy(playerError = error.errorCodeName) }
            }
        })

        scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    fun play(
        videoId: String,
        title: String,
        channel: String,
        thumbUrl: String?,
        streams: StreamSet,
        resumeAtMs: Long? = null,
    ) {
        startService()
        playStartedAtMs = System.currentTimeMillis()
        readyLogged = false
        val host = streams.muxedUrl?.let { hostOf(it) }
            ?: listOfNotNull(streams.videoUrl, streams.audioUrl).joinToString(",") { hostOf(it) }
        log.info("player", "play $videoId (${if (streams.muxedUrl != null) "muxed" else "split"} host=$host)")

        currentVideoId = videoId
        currentTitle = title
        currentChannel = channel
        currentThumb = thumbUrl
        ticksSinceSave = 0

        scope.launch {
            runCatching {
                db.history().add(
                    HistoryEntity(
                        videoId = videoId,
                        title = title,
                        channelName = channel,
                        watchedAt = System.currentTimeMillis(),
                        thumbUrl = thumbUrl,
                    ),
                )
            }
        }

        // stop() and clearMediaItems() both report IDLE. That is us doing it, not a stream that
        // died, so the warning below is suppressed until the new media is prepared.
        preparing = true
        player.stop()
        player.clearMediaItems()

        // Metadata is not decoration: the media notification and the lock screen are built from it.
        // The artwork URI is fetched by media3's bitmap loader, so the shade shows the thumbnail.
        val artwork = Uri.parse(
            thumbUrl?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        )
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(channel)
            .setArtworkUri(artwork)
            .build()

        val muxed = streams.muxedUrl
        if (muxed != null) {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(muxed)
                    .setMediaId(videoId)
                    .setMediaMetadata(metadata)
                    .build(),
            )
        } else {
            val videoItem = MediaItem.Builder()
                .setUri(requireNotNull(streams.videoUrl))
                .setMediaId(videoId)
                .setMediaMetadata(metadata)
                .build()
            val audioItem = MediaItem.fromUri(requireNotNull(streams.audioUrl))
            player.setMediaSource(
                MergingMediaSource(
                    mediaFactory.createMediaSource(videoItem),
                    mediaFactory.createMediaSource(audioItem),
                ),
            )
        }

        lastSkipEndMs = -1L
        segments = emptyList()
        rawSegments = emptyList()
        // Deferred until READY so the SponsorBlock request does not compete with the first
        // frame for the shared pool and bandwidth.
        pendingSegmentsFetch = true

        // Play when ready, before prepare, so the first frame starts as soon as it can.
        player.playWhenReady = true
        player.setPlaybackSpeed(normalSpeed)
        player.prepare()
        preparing = false
        // A seek before the timeline exists clamps to zero, which is exactly how a resume turns
        // into "start from the beginning". Hold it until the player is ready.
        pendingResumeMs = resumeAtMs?.takeIf { it > 5_000 }
        _state.update {
            it.copy(
                positionMs = 0,
                durationMs = 0,
                speed = normalSpeed,
                videoId = videoId,
                videoTitle = title,
                playerError = null,
                firstFrameReady = false,
                segments = emptyList(),
                lastSkip = null,
            )
        }
    }

    /** Close from the mini-player: the next play starts from scratch. */
    fun stop() {
        log.info("player", "stop $currentVideoId at ${player.currentPosition}ms")
        // Clear the id first: the IDLE that stop() reports is this call, not a stream failure.
        currentVideoId = null
        preparing = true
        player.stop()
        player.clearMediaItems()
        preparing = false
        segments = emptyList()
        segmentsJob?.cancel()
        _state.update {
            it.copy(
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                videoId = null,
                videoTitle = "",
                playerError = null,
                firstFrameReady = false,
                segments = emptyList(),
                lastSkip = null,
            )
        }
    }

    fun togglePlayPause() {
        val playerError = player.playerError
        if (playerError != null) {
            // A paused stream can expire its URL. Re-preparing with the same URL is the cheap
            // try; if it fails again the log says so and the picture stays where it was.
            log.warn(
                "player",
                "play pressed while in error ${playerError.errorCodeName}; re-preparing $currentVideoId",
            )
            player.prepare()
            player.playWhenReady = true
            return
        }
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    /**
     * Dictation must not record the video. `playWhenReady` is checked as well as `isPlaying`:
     * tapping the microphone the instant a video starts would otherwise miss the pause, and the
     * recogniser would happily transcribe the soundtrack.
     */
    fun pauseForDictation(): Boolean {
        val wasActive = player.playWhenReady || player.isPlaying
        player.pause()
        return wasActive
    }

    fun resumeAfterDictation(resume: Boolean) {
        if (resume) player.play()
    }

    /**
     * The ±10 buttons. They read the player's own position, not the last UI sample, so two taps in
     * quick succession move twenty seconds instead of racing the tick that refreshes the screen.
     */
    fun skipBy(deltaMs: Long) = seekBy(deltaMs)

    /** Playback speed the user chose, in 0.1 steps, remembered across videos. */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        normalSpeed = clamped
        prefs.edit().putFloat(KEY_SPEED, clamped).apply()
        player.setPlaybackSpeed(clamped)
        _state.update { it.copy(speed = clamped) }
    }

    /** Held at 2x while the finger is down, released back to the speed the user had chosen. */
    fun boost(active: Boolean) {
        if (active) {
            player.setPlaybackSpeed(BOOST_SPEED)
        } else {
            player.setPlaybackSpeed(normalSpeed)
        }
        _state.update { it.copy(speed = player.playbackParameters.speed) }
    }

    private fun fetchSegments(videoId: String) {
        segmentsJob?.cancel()
        segmentsJob = scope.launch {
            rawSegments = runCatching { sponsorBlock.segments(videoId) }.getOrDefault(emptyList())
            applyCategoryFilter()
            if (rawSegments.isEmpty()) {
                log.info("player", "sponsor segments empty for $videoId")
            } else {
                log.info("player", "sponsor segments ${rawSegments.size} for $videoId")
            }
        }
    }

    fun setSponsorSkip(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSOR_SKIP, enabled).apply()
        _state.update { it.copy(sponsorSkip = enabled) }
        log.info("player", "sponsor skip ${if (enabled) "on" else "off"}")
        if (enabled) {
            lastSkipEndMs = -1L
            val videoId = currentVideoId
            if (videoId != null && rawSegments.isEmpty() && segments.isEmpty()) {
                log.info("player", "sponsor refetch for $videoId after enable")
                fetchSegments(videoId)
            }
        }
    }

    /** One category on or off. The next video uses it, and the current one re-filters now. */
    fun setSponsorCategory(category: String, enabled: Boolean) {
        val current = _state.value.sponsorCategories.toMutableSet()
        if (enabled) current.add(category) else current.remove(category)
        prefs.edit().putStringSet(KEY_SPONSOR_CATEGORIES, current).apply()
        _state.update { it.copy(sponsorCategories = current) }
        applyCategoryFilter()
    }

    private fun applyCategoryFilter() {
        val enabled = _state.value.sponsorCategories
        segments = rawSegments.filter { it.category in enabled }
        _state.update { it.copy(segments = segments) }
    }

    fun clearLastSkip() {
        _state.update { it.copy(lastSkip = null) }
    }

    private fun tick() {
        val current = _state.value
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)

        // No seek while buffering: a seek discards buffered data and restarts buffering, so a
        // segment at 0 would add a second buffer cycle to the first frame.
        if (current.sponsorSkip && segments.isNotEmpty() && !current.isBuffering && shouldSkipSegment(player.playbackState)) {
            val hit = segments.firstOrNull { it.contains(position) }
            if (hit != null && hit.endMs != lastSkipEndMs) {
                lastSkipEndMs = hit.endMs
                log.info("player", "sponsor skip ${hit.startMs}ms -> ${hit.endMs}ms category=${hit.category}")
                player.seekTo(hit.endMs)
                _state.update { it.copy(lastSkip = hit) }
            }
        }

        // No update when idle: the same position re-emitted every tick recomposes the
        // whole player screen for nothing while it is loading or paused.
        if (position != current.positionMs || duration != current.durationMs) {
            _state.update {
                it.copy(positionMs = position, durationMs = duration)
            }
        }

        // Persist while playing, from here rather than a ViewModel: the screen can be gone and
        // the player is still the thing that knows where you are.
        if (player.isPlaying && currentVideoId != null && duration > 0) {
            ticksSinceSave++
            if (ticksSinceSave >= SAVE_EVERY_TICKS) {
                ticksSinceSave = 0
                saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val id = currentVideoId ?: return
        val duration = player.duration.coerceAtLeast(0L)
        if (duration <= 0) return
        val position = player.currentPosition.coerceAtLeast(0L)
        scope.launch(Dispatchers.IO) {
            runCatching {
                db.progress().save(
                    ProgressEntity(
                        videoId = id,
                        title = currentTitle,
                        positionMs = position,
                        durationMs = duration,
                        updatedAt = System.currentTimeMillis(),
                        thumbUrl = currentThumb,
                    ),
                )
            }
        }
    }

    private fun startService() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, PlaybackService::class.java),
        )
    }

    /** Speeds offered in the popup, editable in Settings. Stored as a comma list. */
    fun speedPresets(): List<Float> = prefs.getString(KEY_SPEED_PRESETS, null)
        ?.split(',')
        ?.mapNotNull { it.trim().toFloatOrNull() }
        ?.filter { it in MIN_SPEED..MAX_SPEED }
        ?.sorted()
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_PRESETS

    fun setSpeedPresets(speeds: List<Float>) {
        val clean = speeds.map { it.coerceIn(MIN_SPEED, MAX_SPEED) }.distinct().sorted()
        prefs.edit().putString(KEY_SPEED_PRESETS, clean.joinToString(",")).apply()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"

    companion object {
        const val TICK_MS = 250L
        /** Balanced first frame. A tiny start regressed to a 27s READY in testing, so the
         *  1200/2500 values from the last known fast open are the default. Min stays at or
         *  above the after-rebuffer threshold; ExoPlayer refuses the app otherwise. */
        const val MIN_BUFFER_MS = 3_000
        const val MAX_BUFFER_MS = 8_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_200
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500
        const val SAVE_EVERY_TICKS = 20
        const val KEY_SPONSOR_SKIP = "sponsor_skip"
        const val KEY_SPONSOR_CATEGORIES = "sponsor_categories"
        const val KEY_SPEED_PRESETS = "speed_presets"
        val DEFAULT_PRESETS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
        const val KEY_SPEED = "playback_speed"
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 5.0f
        const val BOOST_SPEED = 2.0f
    }
}
