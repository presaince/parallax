/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.SortOption
import com.presaince.parallax.core.SortState
import com.presaince.parallax.core.sortList
import com.presaince.parallax.core.timeAgo
import com.presaince.parallax.data.db.HistoryGroup
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.ProgressEntity
import com.presaince.parallax.data.db.VideoEntity
import com.presaince.parallax.data.library.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTab { FEED, HISTORY }

data class FeedUiState(
    val videos: List<VideoEntity> = emptyList(),
    val history: List<HistoryGroup> = emptyList(),
    val progress: Map<String, ProgressEntity> = emptyMap(),
    val tab: FeedTab = FeedTab.FEED,
    val refreshing: Boolean = false,
    val message: String? = null,
    val lastFetchedAt: Long? = null,
    val sortFeed: SortState = SortState(SortOption.DATE),
    val sortHistory: SortState = SortState(SortOption.DATE),
) {
    /** What the line above the list says. The feed is a database view, so this is honest. */
    val updatedLabel: String
        get() = when {
            refreshing && videos.isEmpty() -> "Loading…"
            lastFetchedAt == null -> "Not fetched yet"
            else -> "Updated ${timeAgo(lastFetchedAt)}"
        }

    fun watchedFraction(videoId: String): Float? {
        val entry = progress[videoId] ?: return null
        if (entry.durationMs <= 0) return null
        return (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    }
}

class FeedViewModel(
    private val content: ContentRepository,
    db: ParallaxDb,
) : ViewModel() {

    private val status = MutableStateFlow(FeedUiState())
    private val dbRef = db

    val state: StateFlow<FeedUiState> = combine(
        content.observeFeed(),
        db.history().observeGrouped(),
        db.progress().observeAll(),
        status,
    ) { videos, history, progress, current ->
        val byVideo = progress.associateBy { it.videoId }
        current.copy(
            videos = sortList(
                videos,
                current.sortFeed,
                date = { it.publishedAt ?: it.fetchedAt },
                likes = { it.likeCount },
                duration = { it.durationSec },
                views = { it.viewCount },
                title = { it.title },
            ),
            history = sortList(
                history,
                current.sortHistory,
                date = { it.watchedAt },
                watches = { it.watches },
                progress = { entry ->
                    val saved = byVideo[entry.videoId]
                    if (saved != null && saved.durationMs > 0) {
                        (saved.positionMs.toFloat() / saved.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                title = { it.title },
            ),
            progress = byVideo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState(),
    )

    init {
        viewModelScope.launch {
            val last = content.lastFetchedAt()
            status.value = status.value.copy(lastFetchedAt = last)
            // Opening the feed should not mean staring at nothing after a day away.
            val stale = last == null || System.currentTimeMillis() - last > STALE_MS
            if (stale) refresh()
        }
    }

    fun selectTab(tab: FeedTab) {
        status.value = status.value.copy(tab = tab)
    }

    fun setSortFeed(state: SortState) {
        status.value = status.value.copy(sortFeed = state)
    }

    fun setSortHistory(state: SortState) {
        status.value = status.value.copy(sortHistory = state)
    }

    fun refresh() {
        if (status.value.refreshing) return
        viewModelScope.launch {
            status.value = status.value.copy(refreshing = true, message = null)
            val result = content.refreshFeed()
            status.value = status.value.copy(
                refreshing = false,
                lastFetchedAt = content.lastFetchedAt() ?: status.value.lastFetchedAt,
                message = when {
                    result.channels == 0 -> result.error
                    result.failures == 0 -> "${result.channels} channels, ${result.videos} videos"
                    result.failures == result.channels -> "Every channel failed: ${result.error}"
                    else -> "${result.failures} of ${result.channels} channels failed"
                },
            )
        }
    }

    fun dismissMessage() {
        status.value = status.value.copy(message = null)
    }

    fun removeHistory(videoId: String) {
        viewModelScope.launch { dbRef.history().clear(videoId) }
    }

    enum class ClearPeriod { TODAY, WEEK, ALL }

    fun clearHistory(period: ClearPeriod) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (period) {
                ClearPeriod.TODAY -> dbRef.history().clearSince(now - 24L * 60 * 60 * 1000)
                ClearPeriod.WEEK -> dbRef.history().clearSince(now - 7L * 24 * 60 * 60 * 1000)
                ClearPeriod.ALL -> dbRef.history().clearAll()
            }
        }
    }

    private companion object {
        const val STALE_MS = 30L * 60 * 1000
    }
}
