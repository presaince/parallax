/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.R
import com.presaince.parallax.core.SortControl
import com.presaince.parallax.core.SortOption
import com.presaince.parallax.core.SortRow
import com.presaince.parallax.core.SortState
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.StreamState
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.ui.component.CardLayout
import com.presaince.parallax.ui.component.CardStyle
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow
import com.presaince.parallax.ui.component.VideoTile
import com.presaince.parallax.ui.component.SineRiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    content: ContentRepository,
    db: ParallaxDb,
    onOpenVideo: (VideoSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    openHistorySignal: Int = 0,
    showFeedSignal: Int = 0,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FeedViewModel(content, db) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hideLive = context.getSharedPreferences("parallax", Context.MODE_PRIVATE)
        .getBoolean("hide_live", false)
    val history = state.tab == FeedTab.HISTORY

    LaunchedEffect(openHistorySignal) {
        if (openHistorySignal > 0) viewModel.selectTab(FeedTab.HISTORY)
    }

    // Tapping Feed from the History list goes back to the Feed list, without a network refresh:
    // switching lists and fetching are different actions here.
    LaunchedEffect(showFeedSignal) {
        if (showFeedSignal > 0) viewModel.selectTab(FeedTab.FEED)
    }

    // The header is inside the list, not above it. A pinned header plus a pinned sort row is two
    // fixed storeys on a screen whose whole job is a long list; both travel with the content and
    // the scroll-to-top button brings them back. History is a mode of the same list.
    val cardRevision = CardStyle.revision
    val cardStyle = remember(cardRevision) { CardStyle.read(context) }
    val pictureOnly = cardStyle.layout == CardLayout.MINIMAL &&
        !cardStyle.title && !cardStyle.channel && !cardStyle.age &&
        !cardStyle.views && !cardStyle.likes

    if (history) {
        HistoryList(
            state = state,
            sortState = state.sortHistory,
            onSortState = viewModel::setSortHistory,
            onSwitchTab = { viewModel.selectTab(FeedTab.FEED) },
            onRefresh = viewModel::refresh,
            onOpenVideo = onOpenVideo,
            onRemove = viewModel::removeHistory,
            onClear = viewModel::clearHistory,
        )
    } else {
        FeedList(
            state = state,
            sortState = state.sortFeed,
            onSortState = viewModel::setSortFeed,
            onSwitchTab = { viewModel.selectTab(FeedTab.HISTORY) },
            onOpenVideo = onOpenVideo,
            onOpenLibrary = onOpenLibrary,
            onRefresh = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
            hideLive = hideLive,
            pictureOnly = pictureOnly,
            cardStyle = cardStyle,
        )
    }
}

/**
 * The part of the feed that scrolls: sort on the left, the Feed/History switch on the right. It
 * moves with the list. While a refresh is running the sort control steps aside for the river, so
 * the one moving thing on screen is the one action happening; the pull-to-refresh indicator is
 * the standard one and has already gone by then.
 */
@Composable
private fun FeedHeader(
    history: Boolean,
    sortState: SortState,
    onSortState: (SortState) -> Unit,
    options: List<SortOption>,
    refreshing: Boolean,
    onSwitchTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (refreshing) {
            SineRiver(
                modifier = Modifier.weight(1f).height(28.dp).padding(end = 8.dp),
                alpha = 0.55f,
                speedMillis = 2600,
            )
        } else {
            SortControl(
                state = sortState,
                onState = onSortState,
                options = options,
                modifier = Modifier.weight(1f),
            )
        }
        ModeButton(
            label = if (history) "Feed" else "History",
            selected = history,
            onClick = onSwitchTab,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    state: FeedUiState,
    sortState: SortState,
    onSortState: (SortState) -> Unit,
    onSwitchTab: () -> Unit,
    onOpenVideo: (VideoSummary) -> Unit,
    onOpenLibrary: () -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    hideLive: Boolean,
    pictureOnly: Boolean,
    cardStyle: CardStyle,
) {
    val videos = state.videos
        .filter { video -> video.title.any { it.isLetterOrDigit() } }
        .filter { video -> !hideLive || video.streamState == "NORMAL" }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissMessage)
                    .padding(vertical = 6.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (videos.isEmpty() && !state.refreshing) {
                EmptyFeed(onOpenLibrary = onOpenLibrary)
            } else if (pictureOnly) {
                // Every word switched off: the picture is the only thing left, so it becomes a
                // two-column grid instead of a column of thumbnails with empty space beside them.
                val gridState = rememberLazyGridState()
                var anchorTop by remember { mutableStateOf(false) }
                var anchorId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(sortState) {
                    if (anchorTop) {
                        gridState.scrollToItem(0)
                        anchorTop = false
                    } else {
                        anchorId?.let { id ->
                            val index = videos.indexOfFirst { it.id == id }
                            if (index >= 0) gridState.scrollToItem(index + 1)
                        }
                        anchorId = null
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(bottom = 84.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                            FeedHeader(
                                history = false,
                                sortState = sortState,
                                onSortState = { next ->
                                    anchorTop = gridState.firstVisibleItemIndex <= 1
                                    anchorId = if (anchorTop) {
                                        null
                                    } else {
                                        videos.getOrNull(gridState.firstVisibleItemIndex - 1)?.id
                                    }
                                    onSortState(next)
                                },
                                options = FEED_SORTS,
                                refreshing = state.refreshing,
                                onSwitchTab = onSwitchTab,
                            )
                        }
                        gridItems(videos, key = { it.id }) { video ->
                            VideoTile(
                                video = video.toSummary(),
                                watched = state.watchedFraction(video.id),
                                style = cardStyle,
                                onClick = { onOpenVideo(video.toSummary()) },
                            )
                        }
                    }
                    ScrollToTopButton(
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                // Changing the order must not move the reader. The header is item 0, so a scroll
                // at or above it is "the top", and the top is exactly where they stay.
                var anchorTop by remember { mutableStateOf(false) }
                var anchorId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(sortState) {
                    if (anchorTop) {
                        listState.scrollToItem(0)
                        anchorTop = false
                    } else {
                        anchorId?.let { id ->
                            val index = videos.indexOfFirst { it.id == id }
                            if (index >= 0) listState.scrollToItem(index + 1)
                        }
                        anchorId = null
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "header") {
                            FeedHeader(
                                history = false,
                                sortState = sortState,
                                onSortState = { next ->
                                    anchorTop = listState.firstVisibleItemIndex <= 1
                                    anchorId = if (anchorTop) {
                                        null
                                    } else {
                                        videos.getOrNull(listState.firstVisibleItemIndex - 1)?.id
                                    }
                                    onSortState(next)
                                },
                                options = FEED_SORTS,
                                refreshing = state.refreshing,
                                onSwitchTab = onSwitchTab,
                            )
                        }
                        items(videos, key = { it.id }) { video ->
                            VideoRow(
                                video = video.toSummary(),
                                watched = state.watchedFraction(video.id),
                                onClick = { onOpenVideo(video.toSummary()) },
                            )
                        }
                        if (state.refreshing && state.videos.isEmpty()) {
                            item(key = "loading") {
                                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(
    state: FeedUiState,
    sortState: SortState,
    onSortState: (SortState) -> Unit,
    onSwitchTab: () -> Unit,
    onRefresh: () -> Unit,
    onOpenVideo: (VideoSummary) -> Unit,
    onRemove: (String) -> Unit,
    onClear: (FeedViewModel.ClearPeriod) -> Unit,
) {
    var pendingClear by remember { mutableStateOf<FeedViewModel.ClearPeriod?>(null) }
    val listState = rememberLazyListState()
    // A sort change while the header is on screen must not carry the header away.
    var anchorTop by remember { mutableStateOf(false) }
    LaunchedEffect(sortState) {
        if (anchorTop) {
            listState.scrollToItem(0)
            anchorTop = false
        }
    }

    // History is a view of the feed, so a pull here means "give me new videos": go where they
    // appear and fetch them, rather than refreshing a record of what is already watched.
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            onSwitchTab()
            onRefresh()
        },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 84.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                FeedHeader(
                    history = true,
                    sortState = sortState,
                    onSortState = { next ->
                        anchorTop = listState.firstVisibleItemIndex <= 1
                        onSortState(next)
                    },
                    options = HISTORY_SORTS,
                    refreshing = false,
                    onSwitchTab = onSwitchTab,
                )
            }
            item(key = "clear") {
                // One label for the group, then named buttons big enough to hit. Three unlabelled
                // icons under a caption asked the reader to decode a bin, a calendar and a
                // calendar-with-arrow.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Clear history",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pendingClear = FeedViewModel.ClearPeriod.TODAY }) {
                        Text("Today")
                    }
                    TextButton(onClick = { pendingClear = FeedViewModel.ClearPeriod.WEEK }) {
                        Text("Week")
                    }
                    TextButton(onClick = { pendingClear = FeedViewModel.ClearPeriod.ALL }) {
                        Text("All")
                    }
                }
            }

            if (state.history.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nothing watched yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(state.history, key = { it.videoId }) { entry ->
                    val summary = VideoSummary(
                        id = entry.videoId,
                        url = "https://www.youtube.com/watch?v=${entry.videoId}",
                        title = entry.title,
                        channelName = entry.channelName,
                        durationSec = 0,
                        thumbnailUrl = entry.thumbUrl,
                    )
                    VideoRow(
                        video = summary,
                        watched = state.watchedFraction(entry.videoId),
                        meta = buildString {
                            append(entry.channelName)
                            append(" · watched ${timeAgo(entry.watchedAt)}")
                            if (entry.watches > 1) append(" · ${entry.watches} times")
                        },
                        onClick = { onOpenVideo(summary) },
                        trailing = {
                            IconButton(
                                onClick = { onRemove(entry.videoId) },
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC000000))
                                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Remove from history",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
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

    pendingClear?.let { period ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("Clear history?") },
            text = {
                Text(
                    when (period) {
                        FeedViewModel.ClearPeriod.TODAY -> "Remove everything watched in the last 24 hours?"
                        FeedViewModel.ClearPeriod.WEEK -> "Remove everything watched in the last 7 days?"
                        FeedViewModel.ClearPeriod.ALL -> "Remove all watch history?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClear(period)
                    pendingClear = null
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("Cancel") }
            },
        )
    }
}

/** The Feed/History switch: a named pill with the history mark, not a bare icon. */
@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyFeed(onOpenLibrary: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text(text = "No videos yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Import subscriptions from NewPipe, LibreTube or Google Takeout, then the " +
                "feed fills itself every few hours. Pull down to fetch now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onOpenLibrary, modifier = Modifier.padding(top = 8.dp)) {
            Text("Open Library")
        }
    }
}

fun VideoEntity.toSummary() = VideoSummary(
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
    streamState = runCatching { StreamState.valueOf(streamState) }
        .getOrDefault(StreamState.NORMAL),
)

/** Only the sorts the list can honour: likes exist for opened videos, watches only in history. */
private val FEED_SORTS = listOf(
    SortOption.DATE,
    SortOption.DURATION,
    SortOption.VIEWS,
    SortOption.TITLE,
)

private val HISTORY_SORTS = listOf(
    SortOption.DATE,
    SortOption.WATCHES,
    SortOption.PROGRESS,
    SortOption.TITLE,
)
