/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val url: String,
    val name: String,
    val addedAt: Long,
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val thumbUrl: String? = null,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val channelName: String,
    val openedAt: Long,
    val watchedAt: Long? = null,
    val thumbUrl: String? = null,
)

/** One row per video seen in a feed or search result; the feed is read from here, not the network. */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelId: String?,
    val channelName: String,
    val durationSec: Long,
    val thumbUrl: String?,
    val publishedAt: Long?,
    val fetchedAt: Long,
    val viewCount: Long? = null,
    /** Filled the first time the video is opened; search results carry no like count. */
    val likeCount: Long? = null,
    /** Filled the first time the comments load, from the extractor's own total. */
    val commentCount: Long? = null,
    /** NORMAL, LIVE or UPCOMING. Stored as text so a future value does not need a migration. */
    val streamState: String = "NORMAL",
    /** When LIVE was first seen, so a listing that disagrees cannot make the pill flicker. */
    val liveSeenAt: Long? = null,
)

/** Projection for [VideoDao.liveSeen]: the id and when it was last observed live. */
data class VideoLiveSeen(val id: String, val liveSeenAt: Long?)

/** The columns that are learned by opening a video, not by listing it. */
data class VideoLearned(val id: String, val likeCount: Long?, val commentCount: Long?)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val url: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long,
    val fetchedAt: Long,
)

/** Raw extractor JSON per page, so a second visit inside the TTL never touches the network. */
@Entity(tableName = "comment_cache", primaryKeys = ["videoId", "pageKey"])
data class CommentCacheEntity(
    val videoId: String,
    val pageKey: String,
    val json: String,
    val fetchedAt: Long,
)

/** A note pinned to a second of a video. [kind] is "deck" today; resync and recipes reuse it. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val atSec: Long,
    val text: String,
    val createdAt: Long,
    val kind: String = "deck",
)

/**
 * One aligned moment. [contextHash] is the cue slice it was computed from, so a changed caption
 * track invalidates the answer and nothing else does.
 */
@Entity(tableName = "resync", primaryKeys = ["videoId", "commentId"])
data class ResyncEntity(
    val videoId: String,
    val commentId: String,
    val originalSec: Long,
    val alignedSec: Long,
    val model: String,
    val createdAt: Long,
    val contextHash: String,
)

/**
 * One AI-generated chapter for a video without chapters in its description. Cached so a second
 * visit is a database read, not a model call.
 */
@Entity(tableName = "ai_chapters", primaryKeys = ["videoId", "atSec"])
data class AiChapterEntity(
    val videoId: String,
    val atSec: Long,
    val title: String,
    val model: String,
    val createdAt: Long,
)

/** Built-in and imported recipes live in one table; built-ins are not privileged. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val name: String,
    val json: String,
    val source: String,
    val createdAt: Long,
)

/**
 * Something the reader does not want offered in the feed. Two shapes share one table:
 *
 * - a video, keyed by its id ([videoId]), which is `110306`'s "hide that one";
 * - a channel's live streams, keyed `live:<channelUrl>` with [liveOnly], which is the perm-live
 *   channel whose current stream sits at the top of the feed forever. Its ordinary uploads still
 *   arrive, because the complaint was about the stream, not the channel.
 *
 * The title and picture are copied in so the Hidden list can name a row whose video has since left
 * the feed table. A refresh replaces every video row, which is why this is a table and not a
 * column: a flag there would be wiped on the next fetch.
 */
@Entity(tableName = "hidden_videos")
data class HiddenRow(
    @PrimaryKey val key: String,
    val videoId: String?,
    val channelUrl: String?,
    val title: String,
    val thumbUrl: String?,
    val liveOnly: Boolean,
    val reason: String,
    val hiddenAt: Long,
)
