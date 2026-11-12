/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.library

import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.cache.CommentCodec
import com.presaince.glasstube.data.db.CommentCacheEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import com.presaince.glasstube.data.source.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comments, paged and cached. Its own class because it shares nothing with the feed: the feed is
 * a library read plus a fan-out of channel fetches, and this is one video's thread with an
 * hour-long cache. Splitting them is what stops a refresh fix from having to be reasoned about
 * beside a cache-expiry rule.
 */
class CommentEngine(
    private val source: VideoSource,
    private val db: GlassTubeDb,
) {

    /**
     * One page of a video's comments, from the hour cache when it is fresh. `refresh` skips the
     * cache even when the stored page is still inside its hour: the reader's long press on the
     * comments header is the only caller, because a stale page has no other way out.
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

    private companion object {
        private const val FIRST_PAGE = "first"
        private const val COMMENTS_TTL_MS = 60 * 60 * 1000L
        private const val RETAIN_MS = 30L * 24 * 60 * 60 * 1000L
    }
}
