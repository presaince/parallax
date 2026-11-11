/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.feed

import com.presaince.glasstube.data.db.HiddenRow
import com.presaince.glasstube.data.db.VideoEntity
import com.presaince.glasstube.data.model.VideoSummary

/** The key a hidden channel's live streams are stored under. One per channel, stable across streams. */
fun liveChannelKey(channelUrl: String): String = "live:$channelUrl"

/** A row that hides exactly one video. */
fun hiddenVideo(video: VideoEntity, reason: String, now: Long = System.currentTimeMillis()) = HiddenRow(
    key = video.id,
    videoId = video.id,
    channelUrl = video.channelId,
    title = video.title,
    thumbUrl = video.thumbUrl,
    liveOnly = false,
    reason = reason,
    hiddenAt = now,
)

/** The same row for a listing the player is holding, which knows the video but not the entity. */
fun hiddenVideo(
    video: com.presaince.glasstube.data.model.VideoSummary,
    reason: String,
    now: Long = System.currentTimeMillis(),
) = HiddenRow(
    key = video.id,
    videoId = video.id,
    channelUrl = video.channelUrl,
    title = video.title,
    thumbUrl = video.thumbnailUrl,
    liveOnly = false,
    reason = reason,
    hiddenAt = now,
)

/**
 * A row that hides a channel's live streams without touching its uploads: the perm-live channel
 * whose current stream sits at the top of the feed forever.
 */
fun hiddenLiveChannel(
    channelUrl: String,
    channelName: String,
    reason: String,
    now: Long = System.currentTimeMillis(),
) = HiddenRow(
    key = liveChannelKey(channelUrl),
    videoId = null,
    channelUrl = channelUrl,
    title = "$channelName — live streams",
    thumbUrl = null,
    liveOnly = true,
    reason = reason,
    hiddenAt = now,
)

/**
 * The feed with the hidden set taken out. Pure, because the two ways to be hidden — this video, or
 * this channel's lives — have to agree with what the Hidden section lists, and a database query
 * cannot be read in a test without a device.
 */
fun visibleVideos(videos: List<VideoEntity>, hidden: List<HiddenRow>): List<VideoEntity> {
    if (hidden.isEmpty()) return videos
    val ids = hidden.filterNot { it.liveOnly }.mapNotNull { it.videoId }.toSet()
    val liveChannels = hidden.filter { it.liveOnly }.mapNotNull { it.channelUrl }.toSet()
    return videos.filterNot { video ->
        video.id in ids || (video.streamState == "LIVE" && video.channelId in liveChannels)
    }
}
