/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.model

/**
 * A video as the app talks about it: what a feed row, a card and the player all need, with no
 * knowledge of how it was stored.
 *
 * [com.presaince.glasstube.data.db.VideoEntity] is the row. This is the thing. What stays behind is
 * bookkeeping: when LIVE was first seen exists so the database can stop the badge flickering, and
 * nothing here depends on it.
 */
data class Video(
    val id: String,
    val title: String,
    val channelUrl: String?,
    val channelName: String,
    val durationSec: Long,
    val thumbUrl: String?,
    val publishedAt: Long?,
    /**
     * When the library last saw this row in a listing. The only date a live stream carries, which
     * is why sorting by date needs it: a stream has no publish date to sort by.
     */
    val lastSeenAt: Long,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val streamState: StreamState = StreamState.NORMAL,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

/**
 * The card's shape. One copy, because a video that is LIVE in the feed is LIVE in the library and
 * in a collection too — the three copies this replaces disagreed about exactly that.
 */
fun Video.toSummary() = VideoSummary(
    id = id,
    url = url,
    title = title,
    channelName = channelName,
    durationSec = durationSec,
    thumbnailUrl = thumbUrl,
    channelUrl = channelUrl,
    publishedAtMs = publishedAt,
    viewCount = viewCount,
    likeCount = likeCount,
    commentCount = commentCount,
    streamState = streamState,
)
