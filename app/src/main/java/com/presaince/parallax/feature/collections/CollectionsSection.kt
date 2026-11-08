/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.collections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.ui.component.ScrollToTopButton
import com.presaince.parallax.ui.component.VideoRow
import com.presaince.parallax.ui.theme.parallaxFilterChipColors

/**
 * Collections, as a way of reading the library rather than a place of their own. The chips are
 * the four questions; the list answers whichever one is chosen.
 */
@Composable
fun CollectionsSection(
    db: ParallaxDb,
    onOpenVideo: (VideoSummary) -> Unit,
) {
    val viewModel: CollectionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CollectionsViewModel(db) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = state.lists[state.selected].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionKind.entries.forEach { collection ->
                FilterChip(
                    selected = state.selected == collection,
                    onClick = { viewModel.select(collection) },
                    label = { Text(collection.label) },
                    colors = parallaxFilterChipColors(),
                )
            }
        }

        Text(
            text = state.selected.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            items.isEmpty() -> Text(
                text = "Nothing matches yet. Watch a few videos and come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    ) {
                        items(items, key = { it.id }) { video ->
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
