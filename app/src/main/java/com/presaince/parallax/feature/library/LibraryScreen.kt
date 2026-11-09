/*
 * Parallax â€” Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.R
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.ProgressEntity
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.feature.collections.CollectionsSection
import com.presaince.parallax.ui.component.CardStyle
import com.presaince.parallax.ui.component.ChannelAvatar
import com.presaince.parallax.ui.component.NoteCountBadge
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow
import com.presaince.parallax.ui.component.noteCounts
import com.presaince.parallax.ui.component.watchCounts
import com.presaince.parallax.ui.theme.parallaxFilterChipColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    db: ParallaxDb,
    content: ContentRepository,
    onOpenVideo: (VideoSummary) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(db, context.contentResolver, content) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes = noteCounts(db)
    val watches = watchCounts(db)
    val cardRevision = CardStyle.revision
    val cardContext = LocalContext.current
    val cardStyle = remember(cardRevision) { CardStyle.read(cardContext) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "${state.subscriptions.size} subscriptions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A full-width row, not a lone icon: History is a destination, so it gets a name and
            // a whole row to itself instead of a 40dp square beside a count.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable(onClick = onOpenHistory)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Three ways to read the same library: the covers, the channels, or a rule. The chips are
        // the whole switch, so nothing hides behind a text button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                LibraryMode.GRID -> "Grid"
                                LibraryMode.SECTIONS -> "By channel"
                                LibraryMode.COLLECTIONS -> "Collections"
                            },
                        )
                    },
                    colors = parallaxFilterChipColors(),
                )
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::dismissMessage)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        when (state.mode) {
            LibraryMode.GRID -> PullToRefreshBox(
                isRefreshing = state.busy,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ChannelGrid(state = state, onOpenChannel = onOpenChannel)
            }
            LibraryMode.SECTIONS -> PullToRefreshBox(
                isRefreshing = state.busy,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ChannelSections(
                    state = state,
                    notes = notes,
                    watches = watches,
                    cardStyle = cardStyle,
                    onOpenChannel = onOpenChannel,
                    onOpenVideo = onOpenVideo,
                )
            }
            LibraryMode.COLLECTIONS -> PullToRefreshBox(
                isRefreshing = state.busy,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                CollectionsSection(db = db, onOpenVideo = onOpenVideo)
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    state: LibraryUiState,
    onOpenChannel: (String, String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, top = 12.dp, end = 12.dp, bottom = 84.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        items(state.subscriptions, key = { it.url }) { subscription ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChannel(subscription.url, subscription.name) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(
                    url = state.avatarFor(subscription.url),
                    name = subscription.name,
                    size = 64.dp,
                )
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        }
        ScrollToTopButton(
            gridState = gridState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ChannelSections(
    state: LibraryUiState,
    notes: Map<String, Int>,
    watches: Map<String, Int>,
    cardStyle: CardStyle,
    onOpenChannel: (String, String) -> Unit,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
        if (state.continueWatching.isNotEmpty()) {
            item(key = "continue-header") { SectionHeader("Continue watching") }
            items(state.continueWatching, key = { "p-${it.videoId}" }) { progress ->
                VideoRow(
                    video = progress.toSummary(),
                    watched = state.watchedFraction(progress.videoId),
                    watches = watches[progress.videoId],
                    meta = "${formatDuration(progress.positionMs / 1000)} watched" +
                        if (cardStyle.watches) {
                            watches[progress.videoId]?.takeIf { it > 1 }?.let { " · $it times" }.orEmpty()
                        } else {
                            ""
                        },
                    onClick = { onOpenVideo(progress.toSummary()) },
                    badge = notes[progress.videoId]?.takeIf { it > 0 }?.takeIf { cardStyle.notes }?.let { n ->
                        { NoteCountBadge(count = n) }
                    },
                )
            }
        }

        item(key = "subs-header") { SectionHeader("Channels") }
        for (subscription in state.subscriptions) {
            item(key = "sub-${subscription.url}") {
                SubscriptionHeader(
                    subscription = subscription,
                    avatarUrl = state.avatarFor(subscription.url),
                    onClick = { onOpenChannel(subscription.url, subscription.name) },
                )
            }
            val videos = state.videosFor(subscription)
            items(videos, key = { "v-${subscription.url}-${it.id}" }) { video ->
                VideoRow(
                    video = video.toSummary(),
                    watched = state.watchedFraction(video.id),
                    watches = watches[video.id],
                    onClick = { onOpenVideo(video.toSummary()) },
                    badge = notes[video.id]?.takeIf { it > 0 }?.takeIf { cardStyle.notes }?.let { n ->
                        { NoteCountBadge(count = n) }
                    },
                )
            }
            if (videos.isEmpty()) {
                item(key = "empty-${subscription.url}") {
                    Text(
                        text = "Nothing stored yet. Pull to refresh the feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
        }
        ScrollToTopButton(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SubscriptionHeader(
    subscription: SubscriptionEntity,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(url = avatarUrl, name = subscription.name, size = 32.dp)
        Text(
            text = subscription.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

private fun ProgressEntity.toSummary() = VideoSummary(
    id = videoId,
    url = "https://www.youtube.com/watch?v=$videoId",
    title = title,
    channelName = "",
    durationSec = durationMs / 1000,
    thumbnailUrl = thumbUrl,
)

private fun VideoEntity.toSummary() = VideoSummary(
    id = id,
    url = "https://www.youtube.com/watch?v=$id",
    title = title,
    channelName = channelName,
    durationSec = durationSec,
    thumbnailUrl = thumbUrl,
    channelUrl = channelId,
    publishedAtMs = publishedAt,
    viewCount = viewCount,
    likeCount = likeCount,
    commentCount = commentCount,
)
