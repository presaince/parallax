/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.Thumbnails

/**
 * The one video row: a small picture on the left, the words beside it. Full-width thumbnails with
 * a title and a meta line underneath made every card three storeys tall, so seeing the list meant
 * scrolling instead of looking. The sideways card fits roughly twice as many videos in the same
 * screen without losing a single fact.
 *
 * Used by the feed, search, library, channel, history and the walk, so they cannot drift apart.
 */
@Composable
fun VideoRow(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    watched: Float? = null,
    watches: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    // Read on every style change: the card style is set on another screen, and under layered
    // navigation the list stays composed while that screen is open.
    val revision = CardStyle.revision
    val style = remember(revision) { CardStyle.read(context) }
    val line = meta ?: metaFor(video, watches, style)
    // With every word switched off, a minimal row is a thumbnail and a rectangle of empty space.
    // Give the picture the whole row; the feed turns that row into a two-column grid.
    val pictureOnly = style.layout == CardLayout.MINIMAL &&
        !style.title && !style.channel && !style.age && !style.views && !style.likes &&
        !style.watches && !style.comments

    when (style.layout) {
        // No border on the card: the progress line under the thumbnail already says how far
        // the video was watched, and a border only repeats it.
        CardLayout.CLASSIC -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        ) {
            Box {
                Thumbnail(
                    url = Thumbnails.forVideo(video.id, video.thumbnailUrl),
                    durationSec = if (style.duration) video.durationSec else 0,
                    progress = if (style.progress) watched else null,
                    state = video.streamState,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (trailing != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) { trailing() }
                }
                if (badge != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) { badge() }
                }
            }
            CardWords(
                title = video.title,
                line = line,
                showTitle = style.title,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        CardLayout.SIDE -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(0.40f)) {
                Thumbnail(
                    url = Thumbnails.forVideo(video.id, video.thumbnailUrl),
                    durationSec = if (style.duration) video.durationSec else 0,
                    progress = if (style.progress) watched else null,
                    state = video.streamState,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (trailing != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) { trailing() }
                }
                if (badge != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) { badge() }
                }
            }
            CardWords(
                title = video.title,
                line = line,
                showTitle = style.title,
                modifier = Modifier.weight(0.60f).padding(start = 12.dp),
            )
        }

        CardLayout.MINIMAL -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = if (pictureOnly) Modifier.fillMaxWidth() else Modifier.weight(0.26f)) {
                Thumbnail(
                    url = Thumbnails.forVideo(video.id, video.thumbnailUrl),
                    durationSec = if (style.duration) video.durationSec else 0,
                    progress = if (style.progress) watched else null,
                    state = video.streamState,
                    // A minimal row is a small picture: the length belongs in its corner, not
                    // floating a thumb-width inside it.
                    badgePadding = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (badge != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) { badge() }
                }
            }
            if (!pictureOnly) {
                CardWords(
                    title = video.title,
                    line = line,
                    showTitle = style.title,
                    modifier = Modifier.weight(0.74f).padding(start = 10.dp),
                )
            }
        }
    }
}

/** A full-width thumbnail for the picture-only grid: no words, nothing to truncate. */
@Composable
fun VideoTile(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watched: Float? = null,
    style: CardStyle,
    badge: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Thumbnail(
            url = Thumbnails.forVideo(video.id, video.thumbnailUrl),
            durationSec = if (style.duration) video.durationSec else 0,
            progress = if (style.progress) watched else null,
            state = video.streamState,
            modifier = Modifier.fillMaxWidth(),
        )
        if (badge != null) {
            val topPad = if (video.streamState != com.presaince.parallax.data.model.StreamState.NORMAL) 30.dp else 6.dp
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = topPad, end = 6.dp)) { badge() }
        }
    }
}

/** The words on a card: the title, then whatever facts the style allows, in that order. */
@Composable
private fun CardWords(
    title: String,
    line: String,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (line.isNotBlank()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (showTitle) 4.dp else 0.dp),
            )
        }
    }
}

private fun metaFor(video: VideoSummary, watches: Int?, style: CardStyle): String = buildList {
    if (style.channel && video.channelName.isNotBlank()) add(video.channelName)
    if (style.age) video.publishedAtMs?.let { add(timeAgo(it)) }
    if (style.views) video.viewCount?.let { add("${compactCount(it)} views") }
    if (style.likes) video.likeCount?.let { add("${compactCount(it)} likes") }
    if (style.watches && watches != null && watches > 1) add("watched $watches times")
    if (style.comments) video.commentCount?.let { add("${compactCount(it)} comments") }
}.joinToString(" · ")

/** 1.2M rather than 1200000: a list row has no space to waste. */
fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
