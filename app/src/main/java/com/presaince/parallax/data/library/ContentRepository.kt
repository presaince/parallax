/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.library

import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.cache.CommentCodec
import com.presaince.parallax.data.db.ChannelEntity
import com.presaince.parallax.data.db.CommentCacheEntity
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.model.Page
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    private val db: ParallaxDb,
    private val log: AppLog,
    /** Player buffering state, so a feed refresh does not compete with the first frame. */
    private val playbackBusy: () -> Boolean = { false },
) {

    fun observeFeed(): Flow<List<VideoEntity>> = db.videos().observeFeed()

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
                for (batch in subscriptions.chunked(MAX_PARALLEL)) {
                // Buffering owns the network: the first frame shares nothing with a feed batch.
                while (playbackBusy()) {
                    if (!isActive) return@withContext FeedRefresh(0, 0, 0, "Refresh stopped")
                    delay(PLAYBACK_RETRY_MS)
                }
                val results = coroutineScope {
                    batch.map { subscription ->
                        async {
                            subscription to Pair(
                                source.channelUploads(subscription.url),
                                source.channelInfo(subscription.url),
                            )
                        }
                    }.awaitAll()
                }
                for ((subscription, pair) in results) {
                    val uploads = pair.first
                    val info = pair.second
                    when (uploads) {
                        is AppResult.Ok -> {
                            val videos = uploads.value.items.filter { it.title.isNotBlank() }
                            db.videos().upsertAll(videos.map { it.toEntity(now, liveSeen, learned) })
                            stored += videos.size
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
                }
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
        learned: Map<String, com.presaince.parallax.data.db.VideoLearned>,
    ) = run {
        // A live badge that appears and disappears on the same video is two listings disagreeing.
        // Once seen live, it stays live for a grace period even if a later listing says otherwise.
        val seenAt = liveSeen[id]
        val stillLive = streamState == com.presaince.parallax.data.model.StreamState.LIVE ||
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

    private companion object {
        const val MAX_PARALLEL = 4
        const val PLAYBACK_RETRY_MS = 400L
        const val FIRST_PAGE = "first"
        const val COMMENTS_TTL_MS = 60 * 60 * 1000L
        const val RETAIN_MS = 30L * 24 * 60 * 60 * 1000L
        const val MAX_LOGGED_FAILURES = 5

        /** How long a live badge survives a listing that claims the stream is over. */
        const val LIVE_GRACE_MS = 3L * 60 * 60 * 1000L
    }
}
