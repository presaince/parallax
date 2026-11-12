/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.db

import com.presaince.glasstube.data.model.StreamState
import com.presaince.glasstube.data.model.Video

/**
 * The row as the rest of the app reads it. The one place the storage shape turns into the model,
 * so a column added here and not to [Video] is a decision made in this file rather than a screen's
 * problem.
 *
 * `channelId` is stored as the channel's URL, so it becomes [Video.channelUrl] on the way out.
 */
fun VideoEntity.toVideo() = Video(
    id = id,
    title = title,
    channelUrl = channelId,
    channelName = channelName,
    durationSec = durationSec,
    thumbUrl = thumbUrl,
    publishedAt = publishedAt,
    lastSeenAt = fetchedAt,
    viewCount = viewCount,
    likeCount = likeCount,
    commentCount = commentCount,
    streamState = streamStateOf(streamState),
)

/**
 * The stored text as the enum. An unknown value is NORMAL rather than a crash: the column is text
 * so a future version can add a value without a migration, and an old build should read it.
 */
fun streamStateOf(text: String): StreamState =
    runCatching { StreamState.valueOf(text) }.getOrDefault(StreamState.NORMAL)
