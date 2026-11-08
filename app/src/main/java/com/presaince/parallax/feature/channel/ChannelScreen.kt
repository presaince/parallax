/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.ui.component.NoteCountBadge
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow
import com.presaince.parallax.ui.component.noteCounts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    source: VideoSource,
    channelUrl: String,
    channelName: String,
    db: ParallaxDb,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val viewModel: ChannelViewModel = viewModel(
        key = "channel-$channelUrl",
        factory = viewModelFactory {
            initializer { ChannelViewModel(source, channelUrl) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes = noteCounts(db)
    val hideLive = LocalContext.current
        .getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
        .getBoolean("hide_live", false)
    val visible = state.items.filter { video ->
        !hideLive || video.streamState == com.presaince.parallax.data.model.StreamState.NORMAL
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = channelName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TextButton(onClick = viewModel::loadFirstPage) { Text("Try again") }
        }

        if (state.loading && state.items.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { video ->
                        VideoRow(
                            video = video,
                            onClick = { onOpenVideo(video) },
                            badge = notes[video.id]?.takeIf { it > 0 }?.let { n ->
                                { NoteCountBadge(count = n) }
                            },
                        )
                    }

                    if (state.next != null) {
                        item(key = "more") {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (state.loading) "Loading…" else "More")
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
    }
}
