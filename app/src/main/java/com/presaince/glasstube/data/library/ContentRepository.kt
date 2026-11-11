/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.library

import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.cache.CommentCodec
import com.presaince.glasstube.data.db.ChannelEntity
import com.presaince.glasstube.data.db.CommentCacheEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.SubscriptionEntity
import com.presaince.glasstube.data.db.VideoEntity
import com.presaince.glasstube.data.model.Channel
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource
import com.presaince.glasstube.core.FeedProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class FeedRefresh(
    val channels: Int,
    val videos: Int,
    val failures: Int,
    val error: String? = null,
)

/**
 * What the feed and the comment reader read instead of the network. The feed is a database view:
 * if the app has fetched once, it opens offline. Refreshing is the only thing that goes out.
 */
class ContentRepository(
    private val source: VideoSource,
    private val db: GlassTubeDb,
    private val log: AppLog,
    /** Player buffering state, so a feed refresh does not compete with the first frame. */
    private val playbackBusy: () -> Boolean = { false },
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeFeed(): Flow<List<VideoEntity>> =
        hideWatchedPercent.flatMapLatest { percent ->
            if (percent <= 0) {
                db.videos().observeFeed()
            } else {
                db.videos().observeFeedHidingWatched(percent)
            }
        }

    /**
     * How finished a video has to be before the feed drops it, 0..100. Zero is off, which is the
     * factory default: a feed that hides things on its own is a surprise. `205752` asked for the
     * range, and a StateFlow is what lets the slider repaint the feed without a fetch.
     */
    private val _hideWatchedPercent = MutableStateFlow(0)
    val hideWatchedPercent: StateFlow<Int> = _hideWatchedPercent.asStateFlow()

    fun setHideWatchedPercent(percent: Int) {
        _hideWatchedPercent.value = percent.coerceIn(0, 100)
    }

    /**
     * Whether a refresh asks each channel's live tab as well as its uploads. On by default: the
     * uploads list never carries a stream that is happening now, which is why a subscribed channel
     * that went live showed nothing (`104716`). Off costs half the requests per refresh.
     */
    private val _feedLive = MutableStateFlow(true)
    val feedLive: StateFlow<Boolean> = _feedLive.asStateFlow()

    fun setFeedLive(enabled: Boolean) {
        _feedLive.value = enabled
    }

    /**
     * How far the running refresh has got, or null when nothing is running. Published per channel,
     * so the feed can say "12 of 47 · 26% · Hard Reset" instead of animating at nobody.
     */
    private val _feedProgress = MutableStateFlow<FeedProgress?>(null)
    val feedProgress: StateFlow<FeedProgress?> = _feedProgress.asStateFlow()

    /**
     * A counter, not an event: a request raised while the feed is not composed is still pending
     * when it is. Nothing listens for the value itself, only for a change.
     */
    private val _feedRefreshRequests = MutableStateFlow(0)
    val feedRefreshRequests: StateFlow<Int> = _feedRefreshRequests.asStateFlow()

    /** An import added channels: the feed is a database view and will not fetch them on its own. */
    fun requestFeedRefresh() {
        _feedRefreshRequests.update { it + 1 }
    }

    suspend fun lastFetchedAt(): Long? = withContext(Dispatchers.IO) {
        db.videos().lastFetchedAt()
    }

    /** One video row as the cards read it. The feed and search listings carry views but no likes;
     *  the likes arrive the first time the video is opened. */
    suspend fun video(id: String): VideoEntity? = withContext(Dispatchers.IO) {
        db.videos().find(id)
    }

    suspend fun refreshFeed(now: Long = System.currentTimeMillis()): FeedRefresh =
        withContext(Dispatchers.IO) {
            val subscriptions = db.subscriptions().all()
            if (subscriptions.isEmpty()) {
                return@withContext FeedRefresh(0, 0, 0, "No subscriptions to fetch")
            }

            var failures = 0
            var firstError: String? = null
            var stored = 0
            val startedAt = System.currentTimeMillis()
            log.info("feed", "refresh start: ${subscriptions.size} subscriptions")

                // Bounded fan-out: four channels at a time, never a stampede when the list is long.
                val liveSeen = runCatching {
                    db.videos().liveSeen()
                        .mapNotNull { row -> row.liveSeenAt?.let { row.id to it } }
                        .toMap()
                }.getOrDefault(emptyMap())
                // REPLACE upserts carry no like or comment counts (the listing has none), so the
                // learned columns have to be carried forward or a refresh would erase them.
                val learned = runCatching {
                    db.videos().learnedCounts().associateBy { it.id }
                }.getOrDefault(emptyMap())
                var done = 0
                _feedProgress.value = FeedProgress(0, subscriptions.size)
                try {
                for (batch in subscriptions.chunked(MAX_PARALLEL)) {
                // Buffering owns the network: the first frame shares nothing with a feed batch.
                while (playbackBusy()) {
                    if (!isActive) return@withContext FeedRefresh(0, 0, 0, "Refresh stopped")
                    delay(PLAYBACK_RETRY_MS)
                }
                val results = coroutineScope {
                    batch.map { subscription ->
                        async {
                            val uploads = async { source.channelUploads(subscription.url) }
                            val info = source.channelInfo(subscription.url)
                            // The live tab is asked only when the channel reports it. YouTube's own
                            // tab list is the authority; asking a channel with no Live tab is a
                            // failure, and the repair path logs failures as faults.
                            val lives = if (_feedLive.value && hasLiveTab(info)) {
                                async { source.channelTab(subscription.url, LIVE_FILTER) }
                            } else {
                                null
                            }
                            subscription to Triple(uploads.await(), info, lives?.await())
                        }
                    }.awaitAll()
                }
                for ((subscription, triple) in results) {
                    val uploads = triple.first
                    val info = triple.second
                    val lives = triple.third
                    when (uploads) {
                        is AppResult.Ok -> {
                            val videos = uploads.value.items.filter { it.title.isNotBlank() }
                            // A stream that is happening now lives on its own tab, so the uploads
                            // list never carries it (`104716`). Live entries go first and the
                            // distinct keeps them: their state is the one worth showing.
                            val liveItems = (lives as? AppResult.Ok)?.value?.items.orEmpty()
                            val merged = (liveItems + videos).distinctBy { it.id }
                            db.videos().upsertAll(merged.map { it.toEntity(now, liveSeen, learned) })
                            stored += merged.size
                        }
                        is AppResult.Err -> {
                            failures++
                            if (firstError == null) firstError = uploads.message
                            if (failures <= MAX_LOGGED_FAILURES) {
                                log.warn(
                                    "feed",
                                    "channel failed: ${subscription.name} - ${uploads.message}",
                                )
                            }
                        }
                    }
                    // The avatar is optional: a channel row without one is still usable.
                    val channel = (info as? AppResult.Ok)?.value
                    db.channels().upsertAll(
                        listOf(
                            ChannelEntity(
                                url = subscription.url,
                                name = channel?.name?.ifBlank { subscription.name }
                                    ?: subscription.name,
                                avatarUrl = channel?.avatarUrl,
                                subscriberCount = channel?.subscriberCount ?: -1L,
                                fetchedAt = now,
                            ),
                        ),
                    )
                    done++
                    _feedProgress.value = FeedProgress(done, subscriptions.size, subscription.name)
                }
                }
                } finally {
                    // Never leave a half-finished count on screen because a refresh was cancelled.
                    _feedProgress.value = null
                }

            db.videos().pruneOlderThan(now - RETAIN_MS)
            log.info(
                "feed",
                "refresh done: $stored videos from ${subscriptions.size} channels, " +
                    "$failures failed, ${System.currentTimeMillis() - startedAt}ms",
            )
            FeedRefresh(
                channels = subscriptions.size,
                videos = stored,
                failures = failures,
                error = firstError,
            )
        }

    /**
     * [refresh] skips the cache even when the stored page is still inside its hour. The reader's
     * long press on the comments header is the only caller: a stale page has no other way out.
     */
    suspend fun comments(
        videoId: String,
        page: String?,
        refresh: Boolean = false,
    ): AppResult<Page<Comment>> =
        withContext(Dispatchers.IO) {
            val key = page?.takeIf { it.isNotBlank() } ?: FIRST_PAGE
            val cached = runCatching { db.commentCache().find(videoId, key) }.getOrNull()
            val now = System.currentTimeMillis()

            // A cached page can predate a parser change. A top-level comment that claims replies
            // but carries no token is such a page: refetch instead of showing a dead button.
            val decoded = cached?.let { CommentCodec.decode(it.json) }?.takeIf { cachedPage ->
                cachedPage.items.none { it.replyCount > 0 && it.repliesPage.isNullOrBlank() }
            }
            val isFresh = !refresh && cached != null && now - cached.fetchedAt < COMMENTS_TTL_MS
            if (decoded != null && isFresh) {
                return@withContext AppResult.Ok(decoded)
            }

            when (val result = source.comments(videoId, page)) {
                is AppResult.Ok -> {
                    // The first page carries the video's comment total; store it on the row so the
                    // list can show it without another network call.
                    result.value.total?.let { total ->
                        runCatching { db.videos().setCommentCount(videoId, total) }
                    }
                    runCatching {
                        db.commentCache().save(
                            CommentCacheEntity(
                                videoId = videoId,
                                pageKey = key,
                                json = CommentCodec.encode(result.value),
                                fetchedAt = now,
                            ),
                        )
                        db.commentCache().pruneOlderThan(now - RETAIN_MS)
                    }
                    result
                }
                // A stale page beats an error banner when the extractor is having a bad day.
                is AppResult.Err ->
                    cached?.let { CommentCodec.decode(it.json) }?.let { AppResult.Ok(it) } ?: result
            }
        }

    private fun VideoSummary.toEntity(
        now: Long,
        liveSeen: Map<String, Long>,
        learned: Map<String, com.presaince.glasstube.data.db.VideoLearned>,
    ) = run {
        // A live badge that appears and disappears on the same video is two listings disagreeing.
        // Once seen live, it stays live for a grace period even if a later listing says otherwise.
        val seenAt = liveSeen[id]
        val stillLive = streamState == com.presaince.glasstube.data.model.StreamState.LIVE ||
            (seenAt != null && now - seenAt < LIVE_GRACE_MS)
        val kept = learned[id]
        VideoEntity(
            id = id,
            title = title,
            channelId = channelUrl,
            channelName = channelName,
            durationSec = durationSec,
            thumbUrl = thumbnailUrl,
            publishedAt = publishedAtMs,
            fetchedAt = now,
            viewCount = viewCount,
            likeCount = likeCount ?: kept?.likeCount,
            commentCount = commentCount ?: kept?.commentCount,
            streamState = if (stillLive) "LIVE" else streamState.name,
            liveSeenAt = if (stillLive) (seenAt ?: now) else null,
        )
    }

    private fun SubscriptionEntity.toChannel(now: Long) = ChannelEntity(
        url = url,
        name = name,
        avatarUrl = null,
        subscriberCount = -1L,
        fetchedAt = now,
    )

    companion object {
        /** The preference behind [hideWatchedPercent]: the feed's "finished" cut, 0 = off. */
        const val KEY_HIDE_WATCHED = "feed_hide_watched_percent"

        private const val MAX_PARALLEL = 4
        private const val PLAYBACK_RETRY_MS = 400L
        private const val FIRST_PAGE = "first"
        private const val COMMENTS_TTL_MS = 60 * 60 * 1000L
        private const val RETAIN_MS = 30L * 24 * 60 * 60 * 1000L
        private const val MAX_LOGGED_FAILURES = 5

        /** The extractor's own filter name for a channel's Live tab. */
        private const val LIVE_FILTER = "livestreams"

        /** Whether a channel has a Live tab to ask. */
        private fun hasLiveTab(info: AppResult<Channel>): Boolean =
            (info as? AppResult.Ok)?.value?.tabs?.contains(LIVE_FILTER) == true

        /** How long a live badge survives a listing that claims the stream is over. */
        private const val LIVE_GRACE_MS = 3L * 60 * 60 * 1000L
    }
}
