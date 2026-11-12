/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.Transport
import com.presaince.glasstube.core.activeTransport
import com.presaince.glasstube.core.cleanHtml
import com.presaince.glasstube.core.firstLoadHeight
import com.presaince.glasstube.core.panelShortSidePx
import com.presaince.glasstube.core.KEY_QUALITY_SESSION
import com.presaince.glasstube.core.qualityInForce
import com.presaince.glasstube.core.qualityKey
import com.presaince.glasstube.core.snapToLadder
import com.presaince.glasstube.data.model.CaptionTrackInfo
import com.presaince.glasstube.data.model.Channel
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Cue
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.model.PreviewFrames
import com.presaince.glasstube.data.model.StreamSet
import com.presaince.glasstube.data.model.SubtitleTrack
import com.presaince.glasstube.data.model.StreamState
import com.presaince.glasstube.data.model.VideoDetail
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.resync.SubtitleParser
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page as ExtractorPage
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Primary source. Extraction happens on the device; no GlassTube server exists or is needed.
 *
 * A parse failure here is an expected failure, not a bug: the extractor breaks when YouTube
 * changes its markup. It returns Err so the caller can fall back to another source.
 */
class NewPipeSource(
    private val http: OkHttpClient,
    private val appContext: android.content.Context? = null,
    private val log: AppLog? = null,
    private val repairPlanner: LlmRepairPlanner? = null,
) : VideoSource {

    override val id: String = "newpipe"

    private val service = ServiceList.YouTube

    // TTL caches: metadata 6h, related 12h, channel uploads 30m (comments 1h live in
    // ContentRepository beside their on-disk pages). In-memory, so a restart still fetches.
    // Concurrent so parallel opens do not race a plain map into a second fetch.
    private val videoCache = ConcurrentHashMap<String, Pair<Long, VideoDetail>>()
    private val relatedCache = ConcurrentHashMap<String, Pair<Long, List<VideoSummary>>>()
    private val uploadsCache = ConcurrentHashMap<String, Pair<Long, Page<VideoSummary>>>()
    private val streamInfoCache = ConcurrentHashMap<String, Pair<Long, StreamInfo>>()
    /** One in-flight fetch per video, shared by metadata and streams. */
    private val streamInfoJobs = ConcurrentHashMap<String, Deferred<StreamInfo>>()
    private val fetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val metadataTTL = 6L * 60 * 60 * 1000
    private val relatedTTL = 12L * 60 * 60 * 1000
    private val uploadsTTL = 30L * 60 * 1000
    private val streamInfoTTL = 60L * 60 * 1000

    /**
     * One parse per video: metadata and streams share it, so opening a video costs one fetch.
     * Concurrent callers share the same in-flight job instead of fetching twice.
     */
    private suspend fun streamInfo(id: String): StreamInfo {
        val now = System.currentTimeMillis()
        streamInfoCache[id]?.takeIf { now - it.first < streamInfoTTL }?.let { return it.second }
        val job = streamInfoJobs.computeIfAbsent(id) {
            fetchScope.async { StreamInfo.getInfo(service, watchUrl(id)) }
        }
        try {
            val parsed = job.await()
            streamInfoCache[id] = System.currentTimeMillis() to parsed
            return parsed
        } finally {
            streamInfoJobs.remove(id, job)
        }
    }

    override suspend fun invalidateStreams(id: String) {
        streamInfoCache.remove(id)
        streamInfoJobs[id]?.cancel()
        streamInfoJobs.remove(id)
        videoCache.remove(id)
        log?.info("source", "streams cache cleared for $id")
    }

    /** Reset per listing call: the log should explain a list, not flood it. */
    private var ambiguousSeen = 0

    /**
     * The extractor's country is global state, so only one pass may set it at a time. A search
     * that spans regions takes it for its whole run; a feed refresh in progress keeps asking as
     * the main region and is not affected, because a channel's uploads do not change by country.
     */
    private val regionLock = Mutex()

    /** Re-initialises the extractor on the main region. The repair wrapper calls this once. */
    private suspend fun resetMainRegion() {
        val code = appContext?.let { resolvedRegions(it).firstOrNull() }.orEmpty()
        regionLock.withLock { initContentCountry(http, code, log) }
    }

    /**
     * One query, every chosen country. Three regions is three fetches, roughly five seconds each,
     * run one after another — not three at once. Results are merged in the order the regions were
     * chosen and deduplicated by video id, so a video found in two countries appears once, where
     * the main region put it.
     */
    override suspend fun search(query: String): AppResult<Page<VideoSummary>> =
        withContext(Dispatchers.IO) {
            ambiguousSeen = 0
            val regions = appContext?.let { resolvedRegions(it) } ?: listOf("")
            if (regions.size <= 1) {
                val primary = withSourceRepair(log, "search", { resetMainRegion() }) { searchOnce(query) }
                val primaryError = primary as? AppResult.Err
                if (primaryError == null || primaryError.sourceError != com.presaince.glasstube.core.SourceError.ExtractorBroken || repairPlanner == null) {
                    return@withContext primary
                }
                val proposal = repairPlanner.plan(query, primaryError.message) ?: return@withContext primary
                val altQuery = proposal.queryAlternative ?: return@withContext primary
                log?.info(REPAIR_TAG, "search llm tier: trying alternative query")
                return@withContext withSourceRepair(log, "search-alt", { resetMainRegion() }) { searchOnce(altQuery) }
            }

            val merged = LinkedHashMap<String, VideoSummary>()
            var firstError: AppResult.Err? = null
            for (region in regions) {
                log?.info(REPAIR_TAG, "search region=$region")
                val result = regionLock.withLock {
                    initContentCountry(http, region, log)
                    withSourceRepair(log, "search", { resetMainRegion() }) { searchOnce(query) }
                }
                when (result) {
                    is AppResult.Ok -> {
                        log?.info(REPAIR_TAG, "search region=$region ok ${result.value.items.size}")
                        result.value.items.forEach { merged.putIfAbsent(it.id, it) }
                    }
                    is AppResult.Err -> {
                        log?.warn(REPAIR_TAG, "search region=$region failed ${result.message.take(120)}")
                        if (firstError == null) firstError = result
                    }
                }
            }
            // Leave the extractor on the main region: everything else assumes it.
            regionLock.withLock { initContentCountry(http, regions.first(), log) }

            if (merged.isEmpty() && firstError != null) {
                firstError
            } else {
                AppResult.Ok(Page(items = merged.values.toList(), next = null))
            }
        }

    private suspend fun searchOnce(query: String): Page<VideoSummary> {
        val extractor = service.getSearchExtractor(query)
        // SearchInfo.getInfo(extractor) does not fetch; the service overload does.
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        return Page(
            items = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toSummary() },
        )
    }

    override suspend fun video(id: String): AppResult<VideoDetail> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        videoCache[id]?.takeIf { now - it.first < metadataTTL }?.let {
            return@withContext AppResult.Ok(it.second)
        }
        withSourceRepair(log, "video", { resetMainRegion() }) {
            val info = streamInfo(id)
            val detail = VideoDetail(
                id = id,
                url = watchUrl(id),
                title = info.name.orEmpty(),
                channelName = info.uploaderName.orEmpty(),
                channelUrl = runCatching { info.uploaderUrl }.getOrNull(),
                durationSec = info.duration,
                description = cleanHtml(runCatching { info.description?.content() }.getOrNull().orEmpty()),
                likeCount = runCatching { info.likeCount }.getOrNull()?.takeIf { it >= 0 },
                previewFrames = runCatching {
                    info.previewFrames.map {
                        PreviewFrames(
                            urls = it.urls,
                            framesPerPageX = it.framesPerPageX,
                            framesPerPageY = it.framesPerPageY,
                            frameWidth = it.frameWidth,
                            frameHeight = it.frameHeight,
                            durationPerFrameMs = it.durationPerFrame,
                            totalCount = it.totalCount,
                        )
                    }
                }.getOrDefault(emptyList()),
            )
            videoCache[id] = now to detail
            detail
        }
    }

    override suspend fun streams(id: String): AppResult<StreamSet> = withContext(Dispatchers.IO) {
        withSourceRepair(log, "streams", { resetMainRegion() }) {
            val info = streamInfo(id)

            // Video-only plus audio, always, when the extractor offers both. Progressive files
            // (video+audio in one) stop at 720p, so a stored 1080p or 4K was unreachable on any
            // video with one audio track; split is also what lets the player drop the picture
            // with the screen off without stopping the sound. A live stream has no split set,
            // and then the muxed file is the only thing there is.
            val target = firstLoadTargetHeight()
            val autoCeiling = autoCeiling()
            val video = pick(info.videoOnlyStreams, target, autoCeiling)
            val audio = pickAudio(info.audioStreams)
            if (video != null && audio != null) {
                log?.info(
                    "source",
                    "streams for $id target=${describeTarget(target, autoCeiling)} split " +
                        "video=${describe(video)} host=${hostOf(video.content)} " +
                        "audio=${hostOf(audio)}",
                )
                StreamSet(videoUrl = video.content, audioUrl = audio)
            } else {
                val muxed = pick(info.videoStreams, target, autoCeiling)
                if (muxed == null) {
                    throw IllegalStateException("no playable stream for $id")
                }
                // Named with the height that was actually chosen: the muxed file tops out at 720p,
                // and a target the set cannot reach is explained here rather than guessed at.
                log?.info(
                    "source",
                    "streams for $id target=${describeTarget(target, autoCeiling)} " +
                        "muxed (no split set) ${describe(muxed)} host=${hostOf(muxed.content)}",
                )
                StreamSet(muxedUrl = muxed.content)
            }
        }
    }

    override suspend fun comments(id: String, page: String?): AppResult<Page<Comment>> =
        withContext(Dispatchers.IO) {
            withSourceRepair(log, "comments", { resetMainRegion() }) {
                val url = watchUrl(id)
                val (items, next, total, disabled) = if (page.isNullOrBlank()) {
                    val info = CommentsInfo.getInfo(service, url)
                    val count = runCatching { info.commentsCount }.getOrNull()
                        ?.takeIf { it >= 0 }?.toLong()
                    val off = runCatching { info.isCommentsDisabled }.getOrDefault(false)
                    CommentsPage(info.relatedItems, info.nextPage, count, off)
                } else {
                    // The extractor wants the continuation token in Page.id, not a URL.
                    val more = CommentsInfo.getMoreItems(service, url, ExtractorPage(null, page))
                    CommentsPage(more.items, more.nextPage, null, false)
                }
                Page(
                    items = items.mapNotNull { it.toComment() },
                    next = next?.id?.takeIf { it.isNotBlank() },
                    // The extractor knows the total and the door state only on the first page.
                    total = total,
                    disabled = disabled,
                )
            }
        }

    override suspend fun related(id: String): AppResult<List<VideoSummary>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            relatedCache[id]?.takeIf { now - it.first < relatedTTL }?.let {
                return@withContext AppResult.Ok(it.second)
            }
            ambiguousSeen = 0
            withSourceRepair(log, "related", { resetMainRegion() }) {
                val items = streamInfo(id).relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toSummary() }
                relatedCache[id] = now to items
                items
            }
        }

    /**
     * [channelUrl] is the channel page URL, which is what an import stores. Pagination reuses the
     * extractor's next-page URL, so a caller only ever passes a token back in.
     */
    override suspend fun channelUploads(
        channelUrl: String,
        page: String?,
    ): AppResult<Page<VideoSummary>> = channelTab(channelUrl, "videos", page)

    /**
     * A channel tab by filter name. The channel's own tab list is what decides whether the filter
     * exists: asking a channel with no Shorts for its shorts tab is an answerable failure, not an
     * empty page that reads as a channel with nothing on it.
     */
    override suspend fun channelTab(
        channelUrl: String,
        filter: String,
        page: String?,
    ): AppResult<Page<VideoSummary>> = withContext(Dispatchers.IO) {
        val cacheKey = "$channelUrl#$filter"
        if (page.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            uploadsCache[cacheKey]?.takeIf { now - it.first < uploadsTTL }?.let {
                return@withContext AppResult.Ok(it.second)
            }
        }
        ambiguousSeen = 0
        withSourceRepair(log, "channelTab:$filter", { resetMainRegion() }) {
            val tabs = ChannelInfo.getInfo(service, channelUrl).tabs
            val tab = tabs.firstOrNull { filter in it.contentFilters }
                ?: throw IllegalStateException("this channel has no $filter tab")

            val extractor = service.getChannelTabExtractor(tab)
            extractor.fetchPage()
            val itemsPage = if (page.isNullOrBlank()) {
                extractor.initialPage
            } else {
                extractor.getPage(ExtractorPage(null, page))
            }

            val result = Page(
                items = itemsPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toSummary() },
                next = itemsPage.nextPage?.id,
            )
            if (page.isNullOrBlank()) {
                uploadsCache[cacheKey] = System.currentTimeMillis() to result
            }
            result
        }
    }

    /** Channel name, avatar, subscribers, the about text and which tabs exist. */
    override suspend fun channelInfo(channelUrl: String): AppResult<Channel> =
        withContext(Dispatchers.IO) {
            withSourceRepair(log, "channelInfo", { resetMainRegion() }) {
                val info = ChannelInfo.getInfo(service, channelUrl)
                Channel(
                    url = channelUrl,
                    name = info.name.orEmpty().ifBlank { channelUrl },
                    avatarUrl = runCatching { info.avatars.maxByOrNull { it.width }?.url }
                        .getOrNull(),
                    subscriberCount = info.subscriberCount,
                    description = info.description?.takeIf { it.isNotBlank() },
                    tabs = info.tabs.flatMap { it.contentFilters }.distinct(),
                )
            }
        }

    /**
     * The caption tracks the video offers. Read from the same cached StreamInfo that streams and
     * the description come from, so listing them costs no request of its own. A track without a
     * playable URL is dropped here rather than offered and then failing.
     */
    override suspend fun captionTracks(id: String): AppResult<List<CaptionTrackInfo>> =
        withContext(Dispatchers.IO) {
            withSourceRepair(log, "captions", { resetMainRegion() }) {
                streamInfo(id).subtitles
                    .filter { it.isUrl() && it.content.isNotBlank() }
                    .distinctBy { it.languageTag to it.isAutoGenerated }
                    .map { candidate ->
                        CaptionTrackInfo(
                            languageTag = candidate.languageTag.orEmpty(),
                            label = candidate.displayLanguageName?.takeIf { it.isNotBlank() }
                                ?: candidate.languageTag.orEmpty(),
                            autoGenerated = candidate.isAutoGenerated,
                        )
                    }
            }
        }

    /**
     * Captions for playback. The same fetch and parse the resync path uses, with the reader's
     * language kept when the video still offers it. A stored tag that has gone is not a failure:
     * the best track takes over, which is what the old single-track path always did.
     */
    override suspend fun captions(id: String, languageTag: String?): AppResult<SubtitleTrack> =
        withContext(Dispatchers.IO) {
            withSourceRepair(log, "subtitles", { resetMainRegion() }) {
                val available = streamInfo(id).subtitles
                    .filter { it.isUrl() && it.content.isNotBlank() }
                val wanted = languageTag?.let { tag ->
                    available.filter { it.languageTag.equals(tag, ignoreCase = true) }
                        .sortedBy { it.isAutoGenerated }
                }.orEmpty()
                // A video can carry both a human track and a machine one for the same tag, so the
                // wanted list is tried in order and the general order is the fallback.
                for (candidate in (wanted + ranked(available)).distinct()) {
                    val track = readTrack(candidate)
                    if (track != null) return@withSourceRepair track
                }
                throw IllegalStateException("no readable captions for $id")
            }
        }

    override suspend fun subtitles(id: String): AppResult<SubtitleTrack> = captions(id, null)

    private fun ranked(candidates: List<SubtitlesStream>): List<SubtitlesStream> =
        candidates.sortedWith(
            compareBy<SubtitlesStream>(
                { it.isAutoGenerated },
                { if (it.languageTag?.startsWith("en") == true) 0 else 1 },
                { formatRank(it.format) },
            ),
        )

    private fun readTrack(candidate: SubtitlesStream): SubtitleTrack? {
        val raw = fetchText(candidate.content)
        val cues = when (candidate.format) {
            MediaFormat.TTML -> SubtitleParser.parseTtml(raw)
            else -> SubtitleParser.parseVttOrSrt(raw)
        }
        if (cues.isEmpty()) return null
        return SubtitleTrack(
            language = candidate.languageTag.orEmpty(),
            autoGenerated = candidate.isAutoGenerated,
            cues = cues,
        )
    }

    private fun formatRank(format: MediaFormat?): Int = when (format) {
        MediaFormat.VTT -> 0
        MediaFormat.SRT -> 1
        MediaFormat.TTML -> 2
        else -> 9
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("captions: HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Auto takes the tallest rendition this screen can show, and the higher bitrate of two at the
     * same height. A chosen height wins otherwise: the closest stream at or below it, so "720p"
     * never turns into a 1080p stream on a phone that asked for less.
     *
     * The ceiling is the panel's short side, bounded by [AUTO_MAX_HEIGHT]: YouTube serves 8K, this
     * phone cannot decode it, and a 4K file on a 1080p screen is bytes for lines nobody sees.
     */
    private fun pick(
        streams: List<VideoStream>,
        targetHeight: Int = 0,
        autoCeiling: Int = AUTO_MAX_HEIGHT,
    ): VideoStream? {
        val playable = streams.filter {
            runCatching { it.isUrl() && it.content.isNotBlank() }.getOrDefault(false)
        }
        if (playable.isEmpty()) return null
        val chosen = if (targetHeight <= 0) {
            fun heightOf(stream: VideoStream) = runCatching { stream.height }.getOrDefault(0)
            playable
                .filter { heightOf(it) in 1..autoCeiling }
                .maxWithOrNull(
                    compareBy({ heightOf(it) }, { runCatching { it.bitrate }.getOrDefault(0) }),
                )
                ?: playable.minByOrNull { heightOf(it).takeIf { h -> h > 0 } ?: Int.MAX_VALUE }
        } else {
            playable
                .filter { runCatching { it.height }.getOrDefault(0) in 1..targetHeight }
                .maxByOrNull { runCatching { it.height }.getOrDefault(0) }
                ?: playable.minByOrNull { runCatching { it.height }.getOrDefault(Int.MAX_VALUE) }
        }
        return chosen
    }

    /** One token for the log: the height chosen, so a target the set could not meet is visible. */
    private fun describe(stream: VideoStream): String =
        runCatching { "${stream.height}p" }.getOrDefault("unknown")

    /** 0 is Auto, which has no target of its own: it has a ceiling instead. */
    private fun describeTarget(target: Int, autoCeiling: Int): String =
        if (target <= 0) "auto<=${autoCeiling}p" else "${target}p"

    /** The tallest Auto may ask for: the panel's short side, never above 4K. */
    private fun autoCeiling(): Int {
        val panel = panelShortSidePx(appContext)
        return if (panel in 1 until AUTO_MAX_HEIGHT) panel else AUTO_MAX_HEIGHT
    }

    /** 0 means Auto; anything else is the ceiling height in pixels, per connection. */
    private fun qualityHeight(): Int = runCatching {
        val prefs = appContext?.getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
        // A step-down this session made outranks the stored choice: the reader's pick is a wish,
        // and a connection that cannot carry it has already said so.
        val stored = qualityInForce(
            prefs?.getInt(qualityKey(activeTransport(appContext)), 0) ?: 0,
            prefs?.getInt(KEY_QUALITY_SESSION, 0) ?: 0,
        )
        // A choice made on a taller ladder, or before the panel cap existed, falls to the tallest
        // stop this screen offers instead of silently fetching 4K for a 1080p panel.
        snapToLadder(stored, panelShortSidePx(appContext))
    }.getOrNull() ?: 0

    /**
     * First-load ceiling. A chosen height is taken as-is; Auto is capped only on a metered
     * connection, where a 720p first frame costs about a third of the bytes.
     */
    private fun firstLoadTargetHeight(): Int = firstLoadHeight(qualityHeight())

    /**
     * The original language wins, never a dub and never an audio-description track. Only then does
     * bitrate decide, and the device language is the tie-breaker that makes a multi-language video
     * sound like the one the user expects.
     */
    private fun pickAudio(streams: List<AudioStream>): String? {
        val playable = streams.filter {
            runCatching { it.isUrl() && it.content.isNotBlank() }.getOrDefault(false)
        }
        if (playable.isEmpty()) return null
        val original = playable.filter {
            runCatching { it.audioTrackType == AudioTrackType.ORIGINAL }.getOrDefault(false)
        }
        val candidates = original.ifEmpty {
            playable.filter {
                val type = runCatching { it.audioTrackType }.getOrNull()
                type != AudioTrackType.DESCRIPTIVE && type != AudioTrackType.SECONDARY
            }.ifEmpty { playable }
        }
        val language = Locale.getDefault().language
        val preferred = candidates.firstOrNull {
            runCatching { it.audioLocale?.language == language }.getOrDefault(false)
        }
        return (preferred ?: candidates.maxByOrNull {
            runCatching { it.averageBitrate }.getOrDefault(0)
        })?.content
    }

    private fun StreamInfoItem.toSummary(): VideoSummary? {        val id = videoIdFromUrl(url) ?: return null
        val title = name?.trim().orEmpty()
        // Upcoming and members-only entries come through with no title; a blank row is worse than
        // no row.
        if (title.isEmpty()) return null
        val state = streamStateOf(streamType, duration, textualUploadDate, title)
        // An unknown duration with no hint is either a live item the extractor did not label or a
        // video with no length. Record it so the Logs screen answers "why has this no pill?".
        if (state == StreamState.NORMAL && duration <= 0 && ambiguousSeen < MAX_AMBIGUOUS_LOGS) {
            ambiguousSeen++
            log?.warn(
                "source",
                "no state for $id: type=$streamType date='${textualUploadDate.orEmpty()}'",
            )
        }
        return VideoSummary(
            id = id,
            url = url,
            title = title,
            channelName = uploaderName.orEmpty(),
            durationSec = duration,
            thumbnailUrl = runCatching { thumbnails.maxByOrNull { it.width }?.url }.getOrNull(),
            channelUrl = uploaderUrl,
            publishedAtMs = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull(),
            viewCount = runCatching { viewCount }.getOrNull()?.takeIf { it >= 0 },
            streamState = state,
        )
    }

    private fun CommentsInfoItem.toComment(): Comment? {
        val id = commentId?.takeIf { it.isNotBlank() } ?: return null
        val replyCount = replyCount.takeIf { it != CommentsInfoItem.UNKNOWN_REPLY_COUNT } ?: 0
        return Comment(
            id = id,
            author = uploaderName.orEmpty(),
            authorUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            text = cleanHtml(runCatching { commentText?.content() }.getOrNull().orEmpty()),
            likeCount = likeCount,
            publishedText = textualUploadDate.orEmpty(),
            publishedAtMs = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull(),
            timestampSec = streamPosition
                .takeIf { it != CommentsInfoItem.NO_STREAM_POSITION }
                ?.toLong(),
            pinned = isPinned,
            replyCount = replyCount,
            repliesPage = runCatching { replies?.id }.getOrNull()?.takeIf { it.isNotBlank() },
        )
    }

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Host only, never the token, so logs diagnose googlevideo without leaking the URL. */
    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Auto's ceiling. YouTube serves 8K; this phone cannot decode it, so it is not a choice. */
        const val AUTO_MAX_HEIGHT = 2160

        const val MAX_AMBIGUOUS_LOGS = 3
    }
}

/** One comments page: items, the continuation, and the two facts only the first page knows. */
private data class CommentsPage(
    val items: List<CommentsInfoItem>,
    val next: ExtractorPage?,
    val total: Long?,
    val disabled: Boolean,
)
