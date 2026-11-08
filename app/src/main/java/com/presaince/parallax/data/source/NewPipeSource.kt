/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.cleanHtml
import com.presaince.parallax.data.model.Channel
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Cue
import com.presaince.parallax.data.model.Page
import com.presaince.parallax.data.model.StreamSet
import com.presaince.parallax.data.model.SubtitleTrack
import com.presaince.parallax.data.model.StreamState
import com.presaince.parallax.data.model.VideoDetail
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.resync.SubtitleParser
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Primary source. Extraction happens on the device; no Parallax server exists or is needed.
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
    private val videoCache = mutableMapOf<String, Pair<Long, VideoDetail>>()
    private val relatedCache = mutableMapOf<String, Pair<Long, List<VideoSummary>>>()
    private val uploadsCache = mutableMapOf<String, Pair<Long, Page<VideoSummary>>>()
    private val metadataTTL = 6L * 60 * 60 * 1000
    private val relatedTTL = 12L * 60 * 60 * 1000
    private val uploadsTTL = 30L * 60 * 1000

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
        val code = appContext?.let { readContentRegions(it).firstOrNull() } ?: "US"
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
            val regions = appContext?.let { readContentRegions(it) } ?: listOf("US")
            if (regions.size <= 1) {
                val primary = withSourceRepair(log, "search", { resetMainRegion() }) { searchOnce(query) }
                val primaryError = primary as? AppResult.Err
                if (primaryError == null || primaryError.sourceError != com.presaince.parallax.core.SourceError.ExtractorBroken || repairPlanner == null) {
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
            val info = StreamInfo.getInfo(service, watchUrl(id))
            val detail = VideoDetail(
                id = id,
                url = watchUrl(id),
                title = info.name.orEmpty(),
                channelName = info.uploaderName.orEmpty(),
                channelUrl = runCatching { info.uploaderUrl }.getOrNull(),
                durationSec = info.duration,
                description = cleanHtml(runCatching { info.description?.content() }.getOrNull().orEmpty()),
                likeCount = runCatching { info.likeCount }.getOrNull()?.takeIf { it >= 0 },
            )
            videoCache[id] = now to detail
            detail
        }
    }

    override suspend fun streams(id: String): AppResult<StreamSet> = withContext(Dispatchers.IO) {
        withSourceRepair(log, "streams", { resetMainRegion() }) {
            val info = StreamInfo.getInfo(service, watchUrl(id))

            // Progressive (video+audio in one file) is the smallest moving part and plays
            // everywhere, but it comes with whichever audio track YouTube chose for the file. A
            // video with several language tracks is played as a merge so the language is ours.
            val target = qualityHeight()
            val audioTracks = info.audioStreams.mapNotNull { it.audioTrackId }.distinct()
            val muxed = if (audioTracks.size > 1) null else pick(info.videoStreams, target)
            if (muxed != null) {
                StreamSet(muxedUrl = muxed)
            } else {
                val video = pick(info.videoOnlyStreams, target)
                val audio = pickAudio(info.audioStreams)
                if (video == null || audio == null) {
                    throw IllegalStateException("no playable stream for $id")
                }
                StreamSet(videoUrl = video, audioUrl = audio)
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
                val items = StreamInfo.getInfo(service, watchUrl(id)).relatedItems
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
    ): AppResult<Page<VideoSummary>> = withContext(Dispatchers.IO) {
        if (page.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            uploadsCache[channelUrl]?.takeIf { now - it.first < uploadsTTL }?.let {
                return@withContext AppResult.Ok(it.second)
            }
        }
        ambiguousSeen = 0
        withSourceRepair(log, "channelUploads", { resetMainRegion() }) {
            val tab = ChannelInfo.getInfo(service, channelUrl).tabs
                .firstOrNull { "videos" in it.contentFilters }
                ?: throw IllegalStateException("channel has no videos tab: $channelUrl")

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
                uploadsCache[channelUrl] = System.currentTimeMillis() to result
            }
            result
        }
    }

    /** Channel name and avatar, for the Library grid. */
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
                )
            }
        }

    /**
     * Captions for the resync feature. A human-written track is better than a machine one, but
     * both are acceptable: the point is to find where a phrase was said, not to publish a
     * transcript. Unreadable formats are skipped rather than failing the whole call.
     */
    override suspend fun subtitles(id: String): AppResult<SubtitleTrack> =
        withContext(Dispatchers.IO) {
            withSourceRepair(log, "subtitles", { resetMainRegion() }) {
                val info = StreamInfo.getInfo(service, watchUrl(id))
                val candidates = info.subtitles
                    .filter { it.isUrl() && it.content.isNotBlank() }
                    .sortedWith(
                        compareBy<SubtitlesStream>(
                            { it.isAutoGenerated },
                            { if (it.languageTag?.startsWith("en") == true) 0 else 1 },
                            { formatRank(it.format) },
                        ),
                    )

                for (candidate in candidates) {
                    val raw = fetchText(candidate.content)
                    val cues = when (candidate.format) {
                        MediaFormat.TTML -> SubtitleParser.parseTtml(raw)
                        else -> SubtitleParser.parseVttOrSrt(raw)
                    }
                    if (cues.isNotEmpty()) {
                        return@withSourceRepair SubtitleTrack(
                            language = candidate.languageTag.orEmpty(),
                            autoGenerated = candidate.isAutoGenerated,
                            cues = cues,
                        )
                    }
                }
                throw IllegalStateException("no readable captions for $id")
            }
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
     * Highest bitrate wins for Auto. A chosen height wins otherwise: the closest stream at or
     * below it, so "720p" never turns into a 1080p stream on a phone that asked for less.
     */
    private fun pick(streams: List<VideoStream>, targetHeight: Int = 0): String? {
        val playable = streams.filter {
            runCatching { it.isUrl() && it.content.isNotBlank() }.getOrDefault(false)
        }
        if (playable.isEmpty()) return null
        val chosen = if (targetHeight <= 0) {
            playable.sortedByDescending { runCatching { it.bitrate }.getOrDefault(0) }.first()
        } else {
            playable
                .filter { runCatching { it.height }.getOrDefault(0) in 1..targetHeight }
                .maxByOrNull { runCatching { it.height }.getOrDefault(0) }
                ?: playable.minByOrNull { runCatching { it.height }.getOrDefault(Int.MAX_VALUE) }
        }
        return chosen?.content
    }

    /** 0 means Auto; anything else is the ceiling height in pixels. */
    private fun qualityHeight(): Int = runCatching {
        appContext?.getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
            ?.getInt("video_quality", 0)
    }.getOrNull() ?: 0

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
        val state = when {
            streamType == StreamType.LIVE_STREAM ||
                streamType == StreamType.AUDIO_LIVE_STREAM -> StreamState.LIVE
            duration <= 0 && UPCOMING_HINT.containsMatchIn(textualUploadDate.orEmpty()) ->
                StreamState.UPCOMING
            duration <= 0 && LIVE_HINT.containsMatchIn(
                "${textualUploadDate.orEmpty()} $title",
            ) -> StreamState.LIVE
            else -> StreamState.NORMAL
        }
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
            text = cleanHtml(runCatching { commentText?.content() }.getOrNull().orEmpty()),
            likeCount = likeCount,
            publishedText = textualUploadDate.orEmpty(),
            timestampSec = streamPosition
                .takeIf { it != CommentsInfoItem.NO_STREAM_POSITION }
                ?.toLong(),
            pinned = isPinned,
            replyCount = replyCount,
            repliesPage = runCatching { replies?.id }.getOrNull()?.takeIf { it.isNotBlank() },
        )
    }

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** How YouTube words a video that has not aired yet. */
        val UPCOMING_HINT = Regex("(?i)premier|schedul|upcoming|waiting|starting")

        /** How a listing marks something that is live right now. */
        val LIVE_HINT = Regex("(?i)watching|live now|\\blive\\b|24/7|radio")

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
