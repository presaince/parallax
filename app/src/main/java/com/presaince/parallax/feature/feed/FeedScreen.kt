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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import kotlin.math.roundToInt
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.StreamState
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.ui.component.CardLayout
import com.presaince.parallax.ui.component.CardStyle
import com.presaince.parallax.ui.component.NoteCountBadge
import com.presaince.parallax.ui.component.subscribedChannels
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
    onOpenChannel: (String, String) -> Unit = { _, _ -> },
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
    val subscribed = subscribedChannels(db)
    val pictureOnly = cardStyle.layout == CardLayout.MINIMAL &&
        !cardStyle.title && !cardStyle.channel && !cardStyle.age &&
        !cardStyle.views && !cardStyle.likes &&
        !cardStyle.watches && !cardStyle.comments

    if (history) {
        HistoryList(
            state = state,
            sortState = state.sortHistory,
            onSortState = viewModel::setSortHistory,
            onSwitchTab = { viewModel.selectTab(FeedTab.FEED) },
            onRefresh = viewModel::refresh,
            onOpenVideo = onOpenVideo,
            onOpenChannel = onOpenChannel,
            subscribed = subscribed,
            onRemove = viewModel::removeHistory,
            onUndo = viewModel::undoRemove,
            onClear = viewModel::clearHistory,
        )
    } else {
        FeedList(
            state = state,
            sortState = state.sortFeed,
            onSortState = viewModel::setSortFeed,
            onSwitchTab = { viewModel.selectTab(FeedTab.HISTORY) },
            onOpenVideo = onOpenVideo,
            onOpenChannel = onOpenChannel,
            subscribed = subscribed,
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
    onOpenChannel: (String, String) -> Unit,
    subscribed: Set<String>,
    onOpenLibrary: () -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    hideLive: Boolean,
    pictureOnly: Boolean,
    cardStyle: CardStyle,
) {
    fun noteBadge(videoId: String): (@Composable () -> Unit)? {
        if (!cardStyle.notes) return null
        val n = state.noteCounts[videoId] ?: return null
        if (n <= 0) return null
        return { NoteCountBadge(count = n) }
    }
    val watchCounts = state.history.associate { it.videoId to it.watches }
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
                                watches = watchCounts[video.id],
                                onClick = { onOpenVideo(video.toSummary()) },
                                badge = noteBadge(video.id),
                                onChannel = onOpenChannel,
                                subscribed = video.channelName.lowercase() in subscribed,
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
    onOpenChannel: (String, String) -> Unit,
    subscribed: Set<String>,
    onRemove: (String) -> Unit,
    onUndo: () -> Unit,
    onClear: (FeedViewModel.ClearPeriod) -> Unit,
) {
    var pendingClear by remember { mutableStateOf<FeedViewModel.ClearPeriod?>(null) }
    val cardRevision = CardStyle.revision
    val cardContext = LocalContext.current
    val cardStyle = remember(cardRevision) { CardStyle.read(cardContext) }
    val snackbar = remember { SnackbarHostState() }
    // One swipe, one undo: the row is gone at once, and the strip is the way back.
    LaunchedEffect(state.lastRemoved) {
        if (state.lastRemoved != null) {
            val result = snackbar.showSnackbar(
                message = "Removed from history",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
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
                    // Slide to delete: the row swipes away and the undo strip answers, instead
                    // of a big X circle on the thumbnail fading into Side and Minimal layouts.
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { target ->
                            if (target == SwipeToDismissBoxValue.EndToStart) {
                                onRemove(entry.videoId)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        modifier = Modifier.padding(top = 10.dp),
                        state = dismissState,
                        backgroundContent = {
                            // The swipe is the whole gesture: no icon, no word behind the row.
                            // The backdrop is the card's own shape, or the row's rounded corners
                            // sit inside a grey square while it moves.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp),
                                    ),
                            )
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        val percent = state.watchedFraction(entry.videoId)
                            ?.let { (it * 100).roundToInt() }
                        VideoRow(
                            video = summary,
                            watched = state.watchedFraction(entry.videoId),
                            meta = buildString {
                                append(entry.channelName)
                                if (percent != null) append(" · $percent% watched")
                                if (cardStyle.watches && entry.watches > 1) append(" · ${entry.watches} times")
                            },
                            metaEnd = true,
                            onClick = { onOpenVideo(summary) },
                            onChannel = onOpenChannel,
                            subscribed = summary.channelName.lowercase() in subscribed,
                            badge = state.noteCounts[entry.videoId]?.takeIf { it > 0 }?.takeIf { cardStyle.notes }?.let { n ->
                                { NoteCountBadge(count = n) }
                            },
                        )
                    }
                }
            }
        }
        ScrollToTopButton(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
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
