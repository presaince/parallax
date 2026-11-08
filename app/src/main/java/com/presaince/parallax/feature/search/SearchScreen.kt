/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow

enum class SearchSort { NONE, DATE, VIEWS, DURATION }

@Composable
fun SearchScreen(
    source: VideoSource,
    db: ParallaxDb,
    preferences: android.content.SharedPreferences,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(source, db, preferences) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val hideLive = LocalContext.current
        .getSharedPreferences("parallax", android.content.Context.MODE_PRIVATE)
        .getBoolean("hide_live", false)
    var sortBy by remember { mutableStateOf(SearchSort.NONE) }
    val visible = state.visible.filter { video ->
        !hideLive || video.streamState == com.presaince.parallax.data.model.StreamState.NORMAL
    }.let { list ->
        when (sortBy) {
            SearchSort.NONE -> list
            SearchSort.DATE -> list.sortedByDescending { it.publishedAtMs ?: 0L }
            SearchSort.VIEWS -> list.sortedByDescending { it.viewCount ?: 0L }
            SearchSort.DURATION -> list.sortedByDescending { it.durationSec }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::search,
            focusRequester = focus,
        )

        TabRow(
            selectedTabIndex = state.mode.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            listOf(
                SearchMode.ALL to "All YouTube",
                SearchMode.SUBS to "Subscriptions",
                SearchMode.BORED to "Bored",
            ).forEach { (mode, label) ->
                Tab(
                    selected = state.mode == mode,
                    onClick = { viewModel.selectMode(mode) },
                    text = { Text(label) },
                )
            }
        }

        // The three readings are one surface, so a horizontal swipe between them is the same
        // gesture as the tab row, without moving the hand to the top of the screen.
        val modes = SearchMode.entries
        val density = LocalDensity.current
        val swipeThreshold = with(density) { 64.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.mode, state.loading) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onHorizontalDrag = { _, dx -> travelled += dx },
                        onDragEnd = {
                            val index = modes.indexOf(state.mode)
                            when {
                                travelled <= -swipeThreshold && index < modes.lastIndex ->
                                    viewModel.selectMode(modes[index + 1])
                                travelled >= swipeThreshold && index > 0 ->
                                    viewModel.selectMode(modes[index - 1])
                            }
                        },
                    )
                },
        ) {
            when {
                state.loading && state.visible.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                state.error != null -> Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                state.visible.isEmpty() && state.history.isNotEmpty() && state.query.isBlank() -> {
                    HistoryPane(
                        history = state.history,
                        onPick = viewModel::useHistory,
                        onRemove = viewModel::removeHistory,
                        onClear = viewModel::clearHistory,
                    )
                }
                // A zero count is not a caption: it is the answer to the whole search, so it gets
                // the middle of the screen and a sentence, not "0 from your subscriptions".
                state.mode == SearchMode.SUBS && visible.isEmpty() && !state.loading &&
                    state.resultsQuery.isNotBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No videos here from the channels you follow.\n" +
                                "The rest of this search is under All YouTube.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    if (state.loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchSort.entries.forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = option == sortBy,
                                onClick = { sortBy = option },
                                label = {
                                    Text(
                                        when (option) {
                                            SearchSort.NONE -> "Relevance"
                                            SearchSort.DATE -> "Newest"
                                            SearchSort.VIEWS -> "Most views"
                                            SearchSort.DURATION -> "Longest"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    state.note?.let {
                        val subscriptions = state.mode == SearchMode.SUBS
                        Text(
                            text = it,
                            style = if (subscriptions) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = if (subscriptions) TextAlign.Center else TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        ) {
                            items(visible, key = { it.id }) { video ->
                                VideoRow(video = video, onClick = { onOpenVideo(video) })
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
}

/**
 * No floating label: the placeholder does the job and the field is focused on arrival, so the
 * label was a box around a word that was already obvious.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 14.dp)
                    .focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
            TextButton(onClick = onSearch) { Text("Go") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryPane(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { query ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.combinedClickable(
                        onClick = { onPick(query) },
                        onLongClick = { onRemove(query) },
                    ),
                ) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
