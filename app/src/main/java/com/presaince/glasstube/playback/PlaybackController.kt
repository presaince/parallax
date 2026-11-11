/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.NetworkMonitor
import com.presaince.glasstube.core.transportLabel
import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.sponsorblock.Segment
import com.presaince.glasstube.data.sponsorblock.SponsorBlock
import com.presaince.glasstube.data.sponsorblock.SponsorCategories
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
import okhttp3.OkHttpClient
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** The height ExoPlayer resolved to, so Auto can say what it is actually using. */
    val videoHeight: Int = 0,
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
    /** How long the player has wanted bytes without an error. Zero when it is not waiting. */
    val stalledForMs: Long = 0L,
    /** Quiet recovery tries spent on this video, so the pill knows when to become the way back. */
    val recoveryAttempts: Int = 0,
    /**
     * The phone has no usable connection. Read from the system, not guessed from a failed
     * request: a stream error while offline is not a fault to spend a retry on.
     */
    val networkDown: Boolean = false,
    /** How much of the stream ExoPlayer is holding, 0..100. Only the buffering pill reads it. */
    val bufferedPercent: Int = 0,
)

/**
 * Elapsed stall time from a start stamp. Zero when nothing is being timed, and never negative:
 * the wall clock can step backwards. Pure, so the detector has a test without a device.
 */
internal fun stalledForMs(sinceMs: Long, nowMs: Long): Long =
    if (sinceMs <= 0L) 0L else (nowMs - sinceMs).coerceAtLeast(0L)

/** When a wait is long enough to be called a stall. Above any rebuffer ExoPlayer recovers from alone. */
const val STALL_LIMIT_MS = 20_000L

/** Two quiet tries, the same budget a player error gets. Past them the pill is the way back. */
const val STALL_RETRY_LIMIT = 2

/**
 * Whether the player should spend one of its quiet tries. A video is loaded, the budget is not
 * spent, and either a stream raised an error or it has gone quiet for [STALL_LIMIT_MS]. Pure, so
 * the retry rule has a test without a device. Pausing is not this function's business: the
 * stopwatch only runs while the player is trying to play, so a paused player never reaches it.
 *
 * A phone with no connection is not a stream that failed. Spending a try there is how a DNS drop
 * used to leave a video dead: both tries burned while there was nothing to reach, and no budget
 * left for the moment the connection came back. The error is still there when it does, so the
 * retry happens then instead.
 */
internal fun shouldRecover(
    hasVideo: Boolean,
    hasError: Boolean,
    stalledForMs: Long,
    attemptsUsed: Int,
    networkDown: Boolean = false,
): Boolean =
    hasVideo && !networkDown && attemptsUsed < STALL_RETRY_LIMIT &&
        (hasError || stalledForMs >= STALL_LIMIT_MS)

/**
 * What a press on play or pause should do. ExoPlayer in [Player.STATE_IDLE] with an item loaded
 * ignores `play()`: it is waiting to be prepared, not to be told to play. That is the fault the
 * reports called "won't play, no toast, no log, restart fixed it" — the press did nothing, so
 * nothing was logged and nothing was shown. Pure, so the four cases have a test.
 */
internal enum class PlayAction { Nothing, Play, Pause, Restart, Reprepare }

internal fun playAction(
    playbackState: Int,
    hasMedia: Boolean,
    hasError: Boolean,
    isPlaying: Boolean,
): PlayAction = when {
    // Nothing loaded: there is no item to prepare, so this is a no-op rather than a fault.
    !hasMedia -> PlayAction.Nothing
    // A failed stream leaves the player IDLE, and so does anything else that took the media away.
    hasError || playbackState == Player.STATE_IDLE -> PlayAction.Reprepare
    playbackState == Player.STATE_ENDED -> PlayAction.Restart
    isPlaying -> PlayAction.Pause
    else -> PlayAction.Play
}

/** A sponsor seek during buffering discards what ExoPlayer buffered and restarts it. */
internal fun shouldSkipSegment(playbackState: Int): Boolean = playbackState == Player.STATE_READY

/**
 * The player's data source sits on the shared OkHttp client, so a stream's connection is the
 * pool the extractor and thumbnails already use instead of a new handshake per play.
 */
internal fun playerDataSourceFactory(client: OkHttpClient): DataSource.Factory =
    OkHttpDataSource.Factory(client)

/**
 * What the player tells the audio system it is. Usage matters more than content type: `USAGE_MEDIA`
 * is what every other media app asks for, so a focus request from NewPipe collides with this one
 * instead of taking focus beside it. Paired with `handleAudioFocus = true` at the player, this is
 * the whole of the rule that another app's video pauses this one.
 */
internal fun mediaAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

/**
 * The disk cache key for one stream file: the video plus its itag. Signed URLs change on every
 * fetch but the file behind an itag does not, so this key survives a refetch while a video's
 * videos and its audio (different itags) never collide. A URL with no itag falls back to its
 * own hash — it simply never hits, which is safe.
 */
internal fun mediaCacheKey(videoId: String, url: String): String {
    val itag = url.substringAfter("itag=", "").substringBefore("&").takeIf { it.isNotEmpty() }
    return if (itag != null) "$videoId:$itag" else "$videoId:${url.hashCode()}"
}

/** The user's stream-cache ceiling, in bytes. Zero means the cache is off. */
internal fun streamCacheLimitBytes(mb: Int): Long = mb.toLong() * 1024 * 1024

/** The identity of a stream set, so a quality change is never mistaken for a warm restart. */
internal fun streamsKey(streams: StreamSet): String =
    streams.muxedUrl ?: "${streams.videoUrl}|${streams.audioUrl}"

/**
 * Whether [play] can skip the teardown: the same video, the same stream URLs, media still
 * loaded, and no player error. Anything else takes the full prepare, so a quality pick or a
 * fresh retry after a rejection still fetches and prepares its streams.
 */
internal fun canReuseLoadedMedia(
    currentVideoId: String?,
    videoId: String,
    currentStreamsKey: String?,
    streams: StreamSet,
    hasMedia: Boolean,
    hasPlayerError: Boolean,
): Boolean =
    currentVideoId == videoId && hasMedia && !hasPlayerError && currentStreamsKey == streamsKey(streams)

/**
 * The one owner of the player. The service keeps it alive in the foreground; screens only read
 * [state] and call commands. Position-driven work (SponsorBlock markers and skip) lives here rather
 * than in a screen, so it keeps working with the activity gone.
 */
class PlaybackController(
    private val appContext: Context,
    private val sponsorBlock: SponsorBlock,
    private val prefs: SharedPreferences,
    private val db: GlassTubeDb,
    private val log: AppLog,
    private val http: OkHttpClient,
    /**
     * Fresh signed streams for a video whose current ones failed or went quiet. Injected rather
     * than taking the whole source, so the recovery rule stays testable with a lambda and the
     * controller keeps one door to the data layer.
     */
    private val refreshStreams: suspend (String) -> AppResult<StreamSet>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The connection, as a value. The tick reads it to decide whether a retry is worth spending;
     * the pill reads it to say "no connection" instead of blaming the stream.
     */
    private val network = NetworkMonitor(appContext)

    /**
     * Whether the display is on. Seeded from the PowerManager and kept current by [screenReceiver],
     * so the tick loop does not ask a system service four times a second.
     */
    private var screenOn: Boolean = true

    /**
     * The screen is the only thing that decides this. The controller lives for the whole process,
     * so the receiver is registered once and never taken down.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    setVideoTrackEnabled(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    setVideoTrackEnabled(true)
                }
            }
        }
    }

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        // A fat buffer behind the playhead: seeking back 30s must play immediately, not re-queue.
        // The disk cache holds the rest; the memory stops at 60s because at 1080p and above every
        // second of it is a copy of a byte the cache already has.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                // ExoPlayer retains nothing behind the playhead by default, so seeking back two
                // seconds left the buffer and became a network read. A short tail starting at
                // the keyframe before the target makes that a memory read again.
                .setBackBuffer(BACK_BUFFER_MS, true)
                // The byte heuristic (about 141 MB for split video and audio) would otherwise
                // clip the 30 s forward target at 1080p and above; the time targets above are
                // the ones that matter here.
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        // The screen going off is expected to leave the sound running. Without a wake lock the CPU
        // may sleep between buffers, and with the video track disabled there is no decoder holding
        // it up either. NETWORK lets it sleep while the buffer is full.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            // ExoPlayer's default is no audio focus at all: it never asks, so it never hears that
            // another app started playing and both soundtracks run over each other. Taking focus
            // is what makes a second player stop this one.
            setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus = */ true)
        }

    /**
     * Stream bytes on disk, capped by the user's setting. Everything the player buffers while
     * playing passes through this cache, so reopening a video starts from the bytes already
     * fetched instead of asking the network for them again. Read once at first use: changing
     * the setting takes effect the next time the app starts.
     */
    private val streamCache: SimpleCache by lazy {
        // Indexing the folder is the only part of a large cache that costs anything, and it
        // happens once. Timed so a two-gigabyte cache can be judged by the log, not guessed at.
        val startedAt = System.currentTimeMillis()
        val cache = SimpleCache(
            File(appContext.cacheDir, "media-cache"),
            LeastRecentlyUsedCacheEvictor(streamCacheLimitBytes(streamCacheMb())),
        )
        log.info(
            "player",
            "media cache ${cache.keys.size} keys, ${cache.cacheSpace / (1024 * 1024)}MB, " +
                "indexed in ${System.currentTimeMillis() - startedAt}ms",
        )
        cache
    }

    /**
     * One factory per play, bound to the video so the cache key can be video plus itag. Every
     * play builds its media sources from this, muxed and split alike, so both go through the
     * shared pool and the disk cache.
     */
    private fun mediaFactoryFor(videoId: String): ProgressiveMediaSource.Factory {
        val upstream = playerDataSourceFactory(http)
        val dataSourceFactory: DataSource.Factory =
            if (streamCacheMb() == 0) {
                upstream
            } else {
                CacheDataSource.Factory()
                    .setCache(streamCache)
                    .setUpstreamDataSourceFactory(upstream)
                    // A cache the system cleared, or a span gone bad, must cost a re-download
                    // and never the picture. Without this the cache's own write is a failure
                    // the viewer sees as a broken stream.
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .setCacheKeyFactory(
                        CacheKeyFactory { spec ->
                            mediaCacheKey(videoId, spec.uri.toString())
                        },
                    )
            }
        return ProgressiveMediaSource.Factory(dataSourceFactory)
    }

    /**
     * The muxed item, or video and audio merged, through the cache-aware factory. One shape for
     * playback and for the SponsorBlock prefetch, so both read and warm the same cache keys.
     * [audioOnly] drops the video source from a split set: the prefetch asks for it while the
     * screen is off, where only the sound will be heard.
     */
    private fun mediaSourceFor(
        videoId: String,
        streams: StreamSet,
        metadata: MediaMetadata? = null,
        audioOnly: Boolean = false,
    ): MediaSource {
        val factory = mediaFactoryFor(videoId)
        fun item(url: String): MediaItem {
            val builder = MediaItem.Builder().setUri(url).setMediaId(videoId)
            if (metadata != null) builder.setMediaMetadata(metadata)
            return builder.build()
        }
        val muxed = streams.muxedUrl
        if (muxed != null) return factory.createMediaSource(item(muxed))
        val video = factory.createMediaSource(item(requireNotNull(streams.videoUrl)))
        val audio = factory.createMediaSource(MediaItem.fromUri(requireNotNull(streams.audioUrl)))
        return if (audioOnly) audio else MergingMediaSource(video, audio)
    }

    private val _state = MutableStateFlow(
        PlaybackState(
            sponsorSkip = prefs.getBoolean(KEY_SPONSOR_SKIP, true),
            sponsorCategories = readSponsorCategories(),
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

    /** A video ID raised by the OS share sheet or a YouTube link; the nav opens the player. */
    data class SharedVideo(val id: String, val startMs: Long? = null, val endMs: Long? = null)

    private val _pendingVideo = MutableStateFlow<SharedVideo?>(null)
    val pendingVideo: StateFlow<SharedVideo?> = _pendingVideo.asStateFlow()

    fun requestOpenVideo(videoId: String?, startMs: Long? = null, endMs: Long? = null) {
        _pendingVideo.value = videoId?.let { SharedVideo(it, startMs, endMs) }
    }

    fun consumePendingVideo() {
        _pendingVideo.value = null
    }

    /**
     * Warms the far side of a long SponsorBlock block before the skip, so the seek lands on bytes
     * already on disk instead of a spinner. Shares the player's media source factory and cache.
     */
    private val sponsorPrefetch = SponsorPrefetch(
        context = appContext,
        scope = scope,
        log = log,
    ) { videoId, streams, audioOnly -> mediaSourceFor(videoId, streams, audioOnly = audioOnly) }

    private var segments: List<Segment> = emptyList()
    private var rawSegments: List<Segment> = emptyList()
    private var segmentsJob: Job? = null
    private var normalSpeed: Float = prefs.getFloat(KEY_SPEED, 1f)
    private var lastSkipEndMs = -1L
    private var currentVideoId: String? = null
    private var currentStreamsKey: String? = null
    /** The streams of the current play, so a prefetch warms exactly what is playing. */
    private var currentStreams: StreamSet? = null
    /** The block end already warmed, so the tick does not start the same fetch four times a second. */
    private var prefetchedEndMs: Long? = null
    private var currentTitle: String = ""
    private var currentChannel: String = ""
    private var currentThumb: String? = null
    private var pendingResumeMs: Long? = null
    private var pendingSegmentsFetch = false
    private var ticksSinceSave = 0
    /** When [play] was called, so buffering time can be logged alongside fetch time. */
    private var playStartedAtMs = 0L
    /** One row per play: Fresh until the first frame plays, then Open, then Watched. */
    private var historyPhase = HistoryPhase.Fresh
    private var historyWatchedMs = 0L
    private var historyRowId: Long? = null
    /** User-chosen token ("30s", "60s", "25%", "100%", ...) for when an open counts as a watch. */
    private var watchThresholdToken: String =
        prefs.getString(KEY_WATCH_THRESHOLD, "30s") ?: "30s"
    /** True once READY for the current play has been logged. */
    private var readyLogged = false
    /** True between the deliberate stop/clear and the prepare at the start of [play]. */
    private var preparing = false
    /** When this buffering spell began, or 0 while the player is not waiting for bytes. */
    private var stallSinceMs = 0L
    /** One log line per stall, not four a second. */
    private var stallLogged = false
    /** The quiet try in flight, so two ticks cannot both start one. */
    private var recoveryJob: Job? = null
    /** Quiet tries spent on the current video. Reset only when a different video plays. */
    private var recoveryAttempts = 0

    init {
        screenOn = screenInteractive()
        ContextCompat.registerReceiver(
            appContext,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _state.update {
                    it.copy(videoHeight = videoSize.height.coerceAtLeast(0))
                }
            }

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
                if (playbackState == Player.STATE_READY) {
                    _state.update {
                        it.copy(
                            videoHeight = (player.videoFormat?.height ?: it.videoHeight)
                                .coerceAtLeast(0),
                        )
                    }
                }
                if (playbackState == Player.STATE_READY && currentVideoId != null && !readyLogged) {
                    readyLogged = true
                    _state.update { it.copy(firstFrameReady = true) }
                    log.info("player", "ready in ${System.currentTimeMillis() - playStartedAtMs}ms for $currentVideoId")
                }
                // IDLE with a video loaded means something took the media away. It is the only
                // clue when a stream stops on its own, so the log names it — and names what the
                // player was doing, because the press that then did nothing left no other trace.
                if (playbackState == Player.STATE_IDLE && currentVideoId != null && !preparing) {
                    log.warn(
                        "player",
                        "idle while loaded: $currentVideoId at ${player.currentPosition}ms " +
                            "items=${player.mediaItemCount} wantPlay=${player.playWhenReady}",
                    )
                }
                val resume = pendingResumeMs
                if (playbackState == Player.STATE_READY && resume != null) {
                    pendingResumeMs = null
                    // Clipped seek already applied: only the fallback case (still at zero) seeks.
                    if (player.currentPosition < resume / 2) {
                        player.seekTo(resume.coerceIn(0, player.duration.coerceAtLeast(0)))
                        log.info("player", "resumed at ${resume}ms")
                    }
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
            // One line per change, never one per tick: a dropped connection used to reach the log
            // as two failed retries per video, which read like the app was at fault.
            var last: Boolean? = null
            network.online.collect { up ->
                _state.update { it.copy(networkDown = !up) }
                if (last != null && last != up) {
                    log.info("player", if (up) "network back" else "network lost")
                }
                last = up
            }
        }

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
        playStartedAtMs = System.currentTimeMillis()
        readyLogged = false
        historyPhase = HistoryPhase.Fresh
        historyWatchedMs = 0L
        historyRowId = null
        // A different video starts with a clean recovery budget. The same video keeps what it
        // has spent, so the play() called by a retry cannot refill its own allowance.
        if (videoId != currentVideoId) recoveryAttempts = 0
        val host = streams.muxedUrl?.let { hostOf(it) }
            ?: listOfNotNull(streams.videoUrl, streams.audioUrl).joinToString(",") { hostOf(it) }
        log.info("player", "play $videoId (${if (streams.muxedUrl != null) "muxed" else "split"} host=$host net=${transportLabel(appContext)})")

        if (
            canReuseLoadedMedia(
                currentVideoId,
                videoId,
                currentStreamsKey,
                streams,
                player.playbackState != Player.STATE_IDLE,
                player.playerError != null,
            )
        ) {
            // Same video, same streams, already loaded: seek and play on the live timeline
            // instead of tearing the player down and buffering from zero.
            val startMs = resumeAtMs?.takeIf { it > 5_000 } ?: 0L
            currentStreams = streams
            player.seekTo(startMs)
            player.playWhenReady = true
            player.setPlaybackSpeed(normalSpeed)
            stallSinceMs = 0L
            stallLogged = false
            log.info("player", "warm restart $videoId at ${startMs}ms")
            _state.update {
                it.copy(
                    positionMs = startMs,
                    speed = normalSpeed,
                    videoId = videoId,
                    videoTitle = title,
                    stalledForMs = 0,
                    recoveryAttempts = recoveryAttempts,
                )
            }
            return
        }

        currentVideoId = videoId
        currentStreamsKey = streamsKey(streams)
        currentTitle = title
        currentChannel = channel
        currentThumb = thumbUrl
        ticksSinceSave = 0

        // stop() and clearMediaItems() both report IDLE. That is us doing it, not a stream that
        // died, so the warning below is suppressed until the new media is prepared.
        //
        // The media item is deliberately NOT cleared here. Clearing it left the media session with
        // no metadata for a moment — exactly the moment a mid-stream recovery replaces the source —
        // and media3 then posted its fallback notification, which the shade shows as
        // "glasstube is running". setMediaSource replaces the item anyway.
        preparing = true
        player.stop()

        // Start at the resume position so the first buffering is at the right place. A seek
        // after READY would buffer at zero first and seek again.
        val startMs = resumeAtMs?.takeIf { it > 5_000 } ?: 0L

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

        player.setMediaSource(mediaSourceFor(videoId, streams, metadata))

        currentStreams = streams
        // A play that starts with the screen already off must not open the video source at all.
        // Set before prepare, which is where the track selection is first built.
        setVideoTrackEnabled(screenOn)
        lastSkipEndMs = -1L
        prefetchedEndMs = null
        sponsorPrefetch.cancel()
        segments = emptyList()
        rawSegments = emptyList()
        // Deferred until READY so the SponsorBlock request does not compete with the first
        // frame for the shared pool and bandwidth.
        pendingSegmentsFetch = true

        // Play when ready, before prepare, so the first frame starts as soon as it can.
        // Seek before prepare: the player queues it and buffers once at the resume position.
        // Kept as a fallback: if the queued seek was dropped and we are still at zero on
        // READY, seek then.
        if (startMs > 0L) {
            player.seekTo(startMs)
            pendingResumeMs = startMs
            log.info("player", "starting at ${startMs}ms")
        } else {
            pendingResumeMs = null
        }
        // A new attempt starts with a clean stopwatch, so the retry rule can arm again.
        stallSinceMs = 0L
        stallLogged = false
        player.playWhenReady = true
        player.setPlaybackSpeed(normalSpeed)
        // The service goes foreground only now, with an item that carries a title: starting it at
        // the top of play() meant a failed first load left a foreground service with nothing to
        // show, and the system filled the gap with "glasstube is running".
        startService()
        player.prepare()
        preparing = false
        _state.update {
            it.copy(
                positionMs = startMs,
                durationMs = 0,
                speed = normalSpeed,
                videoId = videoId,
                videoTitle = title,
                playerError = null,
                firstFrameReady = false,
                stalledForMs = 0,
                segments = emptyList(),
                lastSkip = null,
                recoveryAttempts = recoveryAttempts,
            )
        }
    }

    /** Close from the mini-player: the next play starts from scratch. */
    fun stop() {
        log.info("player", "stop $currentVideoId at ${player.currentPosition}ms")
        // Clear the id first: the IDLE that stop() reports is this call, not a stream failure.
        currentVideoId = null
        currentStreamsKey = null
        currentStreams = null
        prefetchedEndMs = null
        sponsorPrefetch.cancel()
        historyPhase = HistoryPhase.Fresh
        historyWatchedMs = 0L
        historyRowId = null
        preparing = true
        player.stop()
        player.clearMediaItems()
        preparing = false
        // With the item gone the session has no metadata, and a service still in the foreground
        // is exactly what the shade renders as "glasstube is running". A close means no service.
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
        segments = emptyList()
        segmentsJob?.cancel()
        recoveryJob?.cancel()
        recoveryAttempts = 0
        stallSinceMs = 0L
        stallLogged = false
        _state.update {
            it.copy(
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                videoId = null,
                videoTitle = "",
                playerError = null,
                firstFrameReady = false,
                stalledForMs = 0,
                segments = emptyList(),
                lastSkip = null,
                recoveryAttempts = 0,
            )
        }
    }

    fun togglePlayPause() {
        when (
            val action = playAction(
                playbackState = player.playbackState,
                hasMedia = player.mediaItemCount > 0,
                hasError = player.playerError != null,
                isPlaying = player.isPlaying,
            )
        ) {
            // A paused stream can expire its URL, and an IDLE player ignores play(). Re-preparing
            // with the same item is the cheap try for both; the log says which one happened,
            // because a press that does nothing is the fault this branch exists to end.
            PlayAction.Reprepare -> {
                log.warn(
                    "player",
                    "play pressed while " +
                        "${if (player.playerError != null) "in error ${player.playerError?.errorCodeName}" else "idle"}" +
                        "; re-preparing $currentVideoId",
                )
                player.prepare()
                player.playWhenReady = true
            }
            PlayAction.Restart -> {
                player.seekTo(0)
                player.play()
            }
            PlayAction.Pause -> player.pause()
            PlayAction.Play -> player.play()
            PlayAction.Nothing -> log.warn("player", "play pressed with nothing loaded")
        }
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
        } else {
            // No skip will happen, so nothing needs warming.
            prefetchedEndMs = null
            sponsorPrefetch.cancel()
        }
    }

    /**
     * Re-read the settings a backup restore wrote underneath the live player. The theme has the
     * same hook; between them, every holder of a once-read preference is told to look again.
     * The playing item's speed is left alone: the stored value is what the next reset uses.
     */
    fun reload() {
        normalSpeed = prefs.getFloat(KEY_SPEED, 1f)
        watchThresholdToken = prefs.getString(KEY_WATCH_THRESHOLD, "30s") ?: "30s"
        val skip = prefs.getBoolean(KEY_SPONSOR_SKIP, true)
        val categories = readSponsorCategories()
        _state.update { it.copy(sponsorSkip = skip, sponsorCategories = categories) }
        if (skip) applyCategoryFilter() else sponsorPrefetch.cancel()
    }

    /** One category on or off. The next video uses it, and the current one re-filters now. */
    fun setSponsorCategory(category: String, enabled: Boolean) {
        val current = _state.value.sponsorCategories.toMutableSet()
        if (enabled) current.add(category) else current.remove(category)
        // An empty set is meaningless ("skip nothing" is the master toggle's job), and the
        // filter with one would hide every marker while the skip toggle still says on.
        val resolved = current.ifEmpty { SponsorCategories.default.toMutableSet() }
        prefs.edit().putStringSet(KEY_SPONSOR_CATEGORIES, resolved).apply()
        _state.update { it.copy(sponsorCategories = resolved) }
        applyCategoryFilter()
    }

    private fun applyCategoryFilter() {
        val enabled = _state.value.sponsorCategories.ifEmpty { SponsorCategories.default }
        segments = rawSegments.filter { it.category in enabled }
        if (rawSegments.isNotEmpty() && segments.isEmpty()) {
            log.info("player", "sponsor categories filtered out all ${rawSegments.size} segments")
        }
        _state.update { it.copy(segments = segments) }
    }

    fun clearLastSkip() {
        _state.update { it.copy(lastSkip = null) }
    }

    /**
     * The tap on a pill that has spent its quiet tries. Resets the budget because the viewer
     * asked, not because the player guessed, then takes the same path a quiet try takes.
     */
    fun retryNow() {
        recoveryAttempts = 0
        _state.update { it.copy(recoveryAttempts = 0) }
        recover(afterError = player.playerError != null)
    }

    /**
     * One quiet try: fresh signed streams, then the same video back at the place it stopped.
     * The budget is spent before the request so two ticks cannot both start one, and it is not
     * refilled by the [play] this very retry calls — that would make the allowance infinite.
     */
    private fun recover(afterError: Boolean) {
        val videoId = currentVideoId ?: return
        val position = player.currentPosition
        val resumePlayback = player.playWhenReady
        recoveryAttempts++
        stallSinceMs = 0L
        stallLogged = false
        _state.update { it.copy(recoveryAttempts = recoveryAttempts, stalledForMs = 0) }
        log.warn(
            "player",
            "${if (afterError) "error" else "stall"} recovery $recoveryAttempts/$STALL_RETRY_LIMIT " +
                "for $videoId at ${position}ms net=${transportLabel(appContext)}",
        )
        recoveryJob = scope.launch {
            // The failed read gets a beat to close instead of racing the new one, which is why
            // a rejected URL does not come back as two errors in the log.
            delay(RECOVERY_DELAY_MS)
            when (val fresh = refreshStreams(videoId)) {
                is AppResult.Ok -> {
                    play(videoId, currentTitle, currentChannel, currentThumb, fresh.value, position)
                    // Paused while the retry was in flight: a retry must not start the video.
                    if (!resumePlayback) player.pause()
                }
                is AppResult.Err -> {
                    log.error("player", "recovery streams failed for $videoId: ${fresh.message}")
                }
            }
        }
    }

    private fun tick() {
        val current = _state.value
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        val nowMs = System.currentTimeMillis()

        // A stream that never delivers bytes raises no error: ExoPlayer sits in STATE_BUFFERING
        // and every recovery path is keyed on an error that never arrives. Measured here because
        // this loop already samples the player four times a second. A paused player is waiting
        // for nobody, so its clock is stopped — otherwise pausing a slow load would end in a
        // retry that started the video again on its own.
        if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
            if (stallSinceMs == 0L) stallSinceMs = nowMs
        } else {
            stallSinceMs = 0L
            stallLogged = false
        }
        val stalled = stalledForMs(stallSinceMs, nowMs)
        if (stalled >= STALL_LOG_MS && !stallLogged && currentVideoId != null && !preparing) {
            stallLogged = true
            log.warn(
                "player",
                "stalled ${stalled}ms on $currentVideoId at ${player.currentPosition}ms " +
                    "net=${transportLabel(appContext)}",
            )
        }
        if (stalled != current.stalledForMs) {
            _state.update { it.copy(stalledForMs = stalled) }
        }
        // The buffering pill's percentage. Only published while the picture is actually waiting,
        // so a playing video does not churn the state four times a second.
        val buffered = if (player.playbackState == Player.STATE_BUFFERING) {
            player.bufferedPercentage.coerceIn(0, 100)
        } else {
            0
        }
        if (buffered != current.bufferedPercent) {
            _state.update { it.copy(bufferedPercent = buffered) }
        }

        // Recovery is owned here, not by the screen: a stall on the mini-player or with the
        // activity gone still gets its two tries, and the budget is per video, not per visit.
        if (
            recoveryJob?.isActive != true &&
            shouldRecover(
                hasVideo = currentVideoId != null,
                hasError = current.playerError != null,
                stalledForMs = stalled,
                attemptsUsed = recoveryAttempts,
                networkDown = current.networkDown,
            )
        ) {
            recover(afterError = current.playerError != null)
        }

        // No seek while buffering: a seek discards buffered data and restarts buffering, so a
        // segment at 0 would add a second buffer cycle to the first frame.
        if (current.sponsorSkip && segments.isNotEmpty() && !current.isBuffering && shouldSkipSegment(player.playbackState)) {
            val hit = segments.firstOrNull { it.contains(position) }
            if (hit != null && hit.endMs != lastSkipEndMs) {
                lastSkipEndMs = hit.endMs
                // The prefetch, if it ran, has already put the far side in the cache; this seek
                // then reads bytes rather than waiting for them.
                prefetchedEndMs = hit.endMs
                log.info("player", "sponsor skip ${hit.startMs}ms -> ${hit.endMs}ms category=${hit.category}")
                player.seekTo(hit.endMs)
                _state.update { it.copy(lastSkip = hit) }
            }
        }

        // A long block is a seek past what the player holds, and the far side is not on disk yet.
        // Warm it while the block still has minutes to play; once per destination.
        if (current.sponsorSkip && segments.isNotEmpty()) {
            val videoId = currentVideoId
            val streams = currentStreams
            if (videoId != null && streams != null) {
                val target = sponsorPrefetchTarget(
                    positionMs = position,
                    bufferedMs = player.bufferedPosition,
                    segments = segments,
                    prefetchedEndMs = prefetchedEndMs,
                )
                if (target != null) {
                    prefetchedEndMs = target
                    // With the screen off the far side is played as sound, so warming the video
                    // bytes there would be a second download for nothing.
                    sponsorPrefetch.start(videoId, streams, target, audioOnly = !screenOn)
                }
            }
        }

        // No update when idle: the same position re-emitted every tick recomposes the
        // whole player screen for nothing while it is loading or paused.
        if (position != current.positionMs || duration != current.durationMs) {
            _state.update {
                it.copy(positionMs = position, durationMs = duration)
            }
        }

        // One row per play. Fresh opens on the first played frame, ungated; once truly
        // watched past the user threshold the same row is stamped watched. Watched time
        // only accumulates while the player is really playing (READY, not buffering).
        val readyPlaying = player.isPlaying && player.playbackState == Player.STATE_READY
        if (readyPlaying && historyPhase != HistoryPhase.Watched) {
            historyWatchedMs += TICK_MS
        }
        val (nextPhase, write) = advanceHistory(
            historyPhase,
            readyPlaying,
            historyWatchedMs,
            watchThresholdMs(watchThresholdToken, duration),
        )
        if (write != null && currentVideoId != null) {
            historyPhase = nextPhase
            val videoId = currentVideoId!!
            val title = currentTitle
            val channel = currentChannel
            val thumb = currentThumb
            scope.launch(Dispatchers.IO) {
                runCatching {
                    when (write) {
                        HistoryWrite.Open ->
                            historyRowId = db.history().add(
                                HistoryEntity(
                                    videoId = videoId,
                                    title = title,
                                    channelName = channel,
                                    openedAt = System.currentTimeMillis(),
                                    thumbUrl = thumb,
                                ),
                            )
                        HistoryWrite.Watch -> {
                            historyRowId?.let { db.history().markWatched(it, System.currentTimeMillis()) }
                        }
                    }
                }
            }
        } else {
            historyPhase = nextPhase
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

    /** The current "count a watch after" token, e.g. "30s", "25%", "100%". */
    fun watchThreshold(): String = watchThresholdToken

    /** Change when an open counts as a watch. Takes effect on the next play. */
    fun setWatchThreshold(token: String) {
        watchThresholdToken = token
        prefs.edit().putString(KEY_WATCH_THRESHOLD, token).apply()
        log.info("player", "watch threshold $token")
    }

    /** The stream cache ceiling in megabytes. Zero is off. Takes effect on the next app start. */
    fun streamCacheMb(): Int = prefs.getInt(KEY_STREAM_CACHE_MB, DEFAULT_STREAM_CACHE_MB)

    fun setStreamCacheMb(mb: Int) {
        prefs.edit().putInt(KEY_STREAM_CACHE_MB, mb).apply()
        log.info("player", "stream cache ${mb}MB")
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"

    /** Categories to skip. An empty stored set means "no choice made", not "skip nothing". */
    private fun readSponsorCategories(): Set<String> {
        val stored = prefs.getStringSet(KEY_SPONSOR_CATEGORIES, null)?.toSet()
        val resolved = stored?.takeIf { it.isNotEmpty() } ?: SponsorCategories.default
        if (resolved !== stored) {
            prefs.edit().putStringSet(KEY_SPONSOR_CATEGORIES, resolved).apply()
            log.info("player", "sponsor categories reset to default (was empty)")
        }
        return resolved
    }

    /**
     * Audio only while the screen is off. Disabling the video track stops ExoPlayer reading the
     * video source at all, so the bytes stop with it; the audio source keeps its position and is
     * never re-prepared, so the sound does not gap. Turning it back on needs a keyframe and a
     * decoder, which is the frozen picture for a moment when the screen returns — not a rebuffer,
     * and not a fresh fetch of streams.
     *
     * Only a split stream can do this. Inside one muxed file the video bytes sit between the audio
     * bytes, so nothing is saved and the toggle is left alone.
     */
    private fun setVideoTrackEnabled(enabled: Boolean) {
        // Nothing loaded: there is no track to toggle, and the log would only be noise.
        if (currentVideoId == null) return
        val parameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
        player.trackSelectionParameters = parameters
        log.info(
            "player",
            "video track ${if (enabled) "on" else "off"} for $currentVideoId " +
                "shape=${if (currentStreams?.muxedUrl == null) "split" else "muxed"}",
        )
    }

    /** Whether the display is on, so a play started behind a locked screen stays audio-only. */
    private fun screenInteractive(): Boolean = runCatching {
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        power?.isInteractive ?: true
    }.getOrDefault(true)

    companion object {
        const val TICK_MS = 250L
        /** 30s ahead keeps a jittery connection fed; the disk cache covers the rest. */
        const val MIN_BUFFER_MS = 30_000
        const val MAX_BUFFER_MS = 60_000
        /**
         * The tail kept behind the playhead. ExoPlayer's own default is zero, which is why a
         * backward seek always left the buffer. It shares the byte budget with the forward
         * buffer, so it stays half of the minimum rather than growing with it.
         */
        const val BACK_BUFFER_MS = 15_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
        const val SAVE_EVERY_TICKS = 20
        /** When a wait becomes a stall worth naming. Past it, and at [STALL_LIMIT_MS], it retries. */
        const val STALL_LOG_MS = 15_000L
        /** The beat a failed read gets before the recovery request goes out. */
        const val RECOVERY_DELAY_MS = 1_200L

        const val KEY_SPONSOR_SKIP = "sponsor_skip"
        const val KEY_SPONSOR_CATEGORIES = "sponsor_categories"
        const val KEY_SPEED_PRESETS = "speed_presets"
        const val KEY_WATCH_THRESHOLD = "watch_threshold"
        const val KEY_STREAM_CACHE_MB = "stream_cache_mb"
        /** Two gigabytes holds a two-hour podcast at 720p, or the last forty minutes of 1080p. */
        const val DEFAULT_STREAM_CACHE_MB = 2048

        /**
         * Turns a "watch threshold" token into milliseconds, given the video's duration.
         * "30s" -> 30000 ms; "25%" -> a quarter of the duration; "100%" -> the whole video.
         * Unknown or missing durations fall back to 30 seconds.
         */
        fun watchThresholdMs(token: String, durationMs: Long): Long {
            val t = token.trim()
            return if (t.endsWith("%")) {
                val pct = t.removeSuffix("%").trim().toIntOrNull() ?: 30
                if (durationMs <= 0L) 30_000L
                else (durationMs * pct.coerceIn(1, 100) / 100).coerceAtLeast(1_000L)
            } else {
                val seconds = t.removeSuffix("s").trim().toLongOrNull() ?: 30L
                (seconds.coerceAtLeast(1L) * 1_000L)
            }
        }

        val DEFAULT_PRESETS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
        const val KEY_SPEED = "playback_speed"
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 5.0f
        const val BOOST_SPEED = 2.0f
    }
}
