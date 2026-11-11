/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.presaince.glasstube.core.SortOption
import com.presaince.glasstube.core.SortRow
import com.presaince.glasstube.core.formatDuration
import com.presaince.glasstube.core.plural
import com.presaince.glasstube.core.timeAgo
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource
import com.presaince.glasstube.ui.component.CardStyle
import com.presaince.glasstube.ui.component.LinkifiedText
import com.presaince.glasstube.ui.component.NoteCountBadge
import com.presaince.glasstube.ui.component.ScrollToTopButton
import com.presaince.glasstube.ui.component.Thumbnail
import com.presaince.glasstube.ui.component.VideoRow
import com.presaince.glasstube.ui.component.compactCount
import com.presaince.glasstube.ui.component.noteCounts
import com.presaince.glasstube.ui.component.watchCounts
import com.presaince.glasstube.ui.component.subscribedChannels
import com.presaince.glasstube.ui.theme.accentTextColour
import com.presaince.glasstube.ui.theme.glassTubeFilterChipColors

/**
 * One channel: who it is, whether this phone follows it, and its videos by tab. The top bar owns
 * the name, so the header does not repeat it as a bare label — it carries the avatar, the numbers
 * and the subscribe control instead, which is what the old one-line label was missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    source: VideoSource,
    channelUrl: String,
    channelName: String,
    db: GlassTubeDb,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val viewModel: ChannelViewModel = viewModel(
        key = "channel-$channelUrl",
        factory = viewModelFactory {
            initializer { ChannelViewModel(source, db, channelUrl) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes = noteCounts(db)
    val watches = watchCounts(db)
    val subscribed = subscribedChannels(db)
    val cardRevision = CardStyle.revision
    val cardContext = LocalContext.current
    val cardStyle = remember(cardRevision) { CardStyle.read(cardContext) }
    val hideLive = cardContext
        .getSharedPreferences("glasstube", android.content.Context.MODE_PRIVATE)
        .getBoolean("hide_live", false)
    val name = state.channel?.name ?: channelName
    val videos = state.sorted.filter { video ->
        !hideLive || video.streamState == com.presaince.glasstube.data.model.StreamState.NORMAL
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ChannelHeader(
            state = state,
            fallbackName = name,
            onToggleSubscription = viewModel::toggleSubscription,
        )

        TabChips(
            tabs = state.tabs,
            selected = state.tab,
            onSelect = viewModel::selectTab,
        )

        when (state.tab) {
            ChannelTab.ABOUT -> AboutBlock(state.channel?.description)
            ChannelTab.WATCHED -> WatchedBlock(state.watched, name, onOpenVideo)
            else -> VideoTab(
                state = state,
                videos = videos,
                notes = notes,
                watches = watches,
                subscribed = subscribed,
                cardStyle = cardStyle,
                onSort = viewModel::setSort,
                onOpenVideo = onOpenVideo,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

@Composable
private fun ChannelHeader(
    state: ChannelUiState,
    fallbackName: String,
    onToggleSubscription: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        // The name has its own line, with the one thing that qualifies it: whether this phone
        // follows the channel. Everything else (the numbers, the button) sits underneath.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatar = state.channel?.avatarUrl
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = fallbackName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.subscribed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Subscribed",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentTextColour(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val subscribers = state.channel?.subscriberCount ?: -1
            Text(
                text = when {
                    subscribers < 0 -> ""
                    subscribers == 0L -> "No subscriber count"
                    else -> plural(subscribers, "subscriber")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.subscribed) {
                OutlinedButton(onClick = onToggleSubscription) { Text("Subscribed") }
            } else {
                Button(onClick = onToggleSubscription) { Text("Subscribe") }
            }
        }
    }
}

@Composable
private fun TabChips(
    tabs: List<ChannelTab>,
    selected: ChannelTab,
    onSelect: (ChannelTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = { Text(tab.label) },
                colors = glassTubeFilterChipColors(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.VideoTab(
    state: ChannelUiState,
    videos: List<VideoSummary>,
    notes: Map<String, Int>,
    watches: Map<String, Int>,
    subscribed: Set<String>,
    cardStyle: CardStyle,
    onSort: (com.presaince.glasstube.core.SortState) -> Unit,
    onOpenVideo: (VideoSummary) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    if (videos.isNotEmpty()) {
        SortRow(
            state = state.sort,
            onState = onSort,
            options = listOf(SortOption.DATE, SortOption.VIEWS, SortOption.DURATION),
        )
    }
    state.error?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        TextButton(onClick = onRefresh) { Text("Try again") }
    }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.loading && videos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(24.dp))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 84.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        watches = watches[video.id],
                        onClick = { onOpenVideo(video) },
                        subscribed = video.channelName.lowercase() in subscribed,
                        badge = notes[video.id]?.takeIf { it > 0 }?.takeIf { cardStyle.notes }?.let { n ->
                            { NoteCountBadge(count = n) }
                        },
                    )
                }
                if (state.next != null) {
                    item(key = "more") {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.loading) "Loading…" else "More")
                        }
                    }
                }
                if (!state.loading && videos.isEmpty() && state.error == null) {
                    item(key = "empty") {
                        Text(
                            text = "Nothing on this tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
            ScrollToTopButton(
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun AboutBlock(description: String?) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // The description is a body of text with links in it, exactly like a video's, so it
            // gets the same treatment: a URL opens (`205649` — the About tab used to print it as
            // words). A bare `@handle` is deliberately *not* a link: the text does not say which
            // network it belongs to, and guessing YouTube sent the owner to the wrong site.
            LinkifiedText(
                text = description ?: "This channel's page carries no description.",
                style = MaterialTheme.typography.bodyMedium,
                colour = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun WatchedBlock(
    watched: List<WatchedHere>,
    channelName: String,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    if (watched.isEmpty()) {
        Text(
            text = "Nothing from $channelName in your history yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 84.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(watched, key = { it.videoId }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onOpenVideo(
                            VideoSummary(
                                id = row.videoId,
                                url = "https://www.youtube.com/watch?v=${row.videoId}",
                                title = row.title,
                                channelName = channelName,
                                durationSec = 0,
                                thumbnailUrl = row.thumbUrl,
                            ),
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The picture the row already carried and never drew (`205625`): a Watched row
                // without one is a line of text that could be anything.
                Thumbnail(
                    url = row.thumbUrl.orEmpty(),
                    durationSec = 0,
                    modifier = Modifier
                        .width(112.dp)
                        .padding(end = 10.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeAgo(row.watchedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
