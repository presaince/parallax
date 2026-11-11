/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.core.FeedProgress
import com.presaince.glasstube.core.SortOption
import com.presaince.glasstube.core.SortState
import com.presaince.glasstube.core.sortList
import com.presaince.glasstube.core.timeAgo
import com.presaince.glasstube.data.db.HiddenRow
import com.presaince.glasstube.data.db.HistoryGroup
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.db.VideoEntity
import com.presaince.glasstube.data.library.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTab { FEED, HISTORY, HIDDEN }

data class FeedUiState(
    val videos: List<VideoEntity> = emptyList(),
    val history: List<HistoryGroup> = emptyList(),
    val progress: Map<String, ProgressEntity> = emptyMap(),
    val noteCounts: Map<String, Int> = emptyMap(),
    /** What the feed leaves out, newest first, for the Hidden section. */
    val hidden: List<HiddenRow> = emptyList(),
    val tab: FeedTab = FeedTab.FEED,
    val refreshing: Boolean = false,
    /** How far the running refresh has got, or null when nothing is running. */
    val refreshProgress: FeedProgress? = null,
    val message: String? = null,
    val lastFetchedAt: Long? = null,
    val sortFeed: SortState = SortState(SortOption.DATE),
    val sortHistory: SortState = SortState(SortOption.DATE),
    /** The row the swipe just removed, held for one snackbar's undo. */
    val lastRemoved: HistoryGroup? = null,
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
    db: GlassTubeDb,
) : ViewModel() {

    private val status = MutableStateFlow(FeedUiState())
    private val dbRef = db
    /** A refresh asked for while one is running, so an import's request is not dropped. */
    private var refreshAgain = false

    val state: StateFlow<FeedUiState> = combine(
        // The hidden set filters the feed here, not in the database: the same rows have to be
        // listed in the Hidden section, and one predicate is easier to trust than a query plus a
        // copy of the same rule.
        combine(content.observeFeed(), db.hidden().observeAll()) { videos, hidden ->
            visibleVideos(videos, hidden) to hidden
        },
        db.history().observeGrouped(),
        db.progress().observeAll(),
        db.notes().counts("deck"),
        combine(status, content.feedProgress) { current, refresh -> current to refresh },
    ) { (videos, hidden), history, progress, noteCounts, (current, refresh) ->
        val byVideo = progress.associateBy { it.videoId }
        current.copy(
            noteCounts = noteCounts.associate { it.videoId to it.n },
            videos = sortList(
                videos,
                current.sortFeed,
                date = { it.publishedAt ?: it.fetchedAt },
                likes = { it.likeCount },
                duration = { it.durationSec },
                views = { it.viewCount },
                title = { it.title },
            ),
            hidden = hidden,
            refreshProgress = refresh,
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
        // Settings stays on top of a composed feed, so an import that adds channels cannot rely
        // on this ViewModel being created again. The request is the only signal it hears.
        viewModelScope.launch {
            content.feedRefreshRequests.drop(1).collect { refresh() }
        }
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
        if (status.value.refreshing) {
            refreshAgain = true
            return
        }
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
            if (refreshAgain) {
                refreshAgain = false
                refresh()
            }
        }
    }

    fun dismissMessage() {
        status.value = status.value.copy(message = null)
    }

    fun removeHistory(videoId: String) {
        viewModelScope.launch {
            val group = state.value.history.firstOrNull { it.videoId == videoId }
            dbRef.history().clear(videoId)
            status.value = status.value.copy(lastRemoved = group)
        }
    }

    /** Puts the swiped row back as one open event. The watch count does not survive a swipe. */
    fun undoRemove() {
        viewModelScope.launch {
            val group = status.value.lastRemoved
            if (group != null) {
                dbRef.history().add(
                    com.presaince.glasstube.data.db.HistoryEntity(
                        videoId = group.videoId,
                        title = group.title,
                        channelName = group.channelName,
                        openedAt = group.watchedAt,
                        thumbUrl = group.thumbUrl,
                    ),
                )
                status.value = status.value.copy(lastRemoved = null)
            }
        }
    }

    fun dismissRemoved() {
        status.value = status.value.copy(lastRemoved = null)
    }

    /** Leaves this one video out of the feed. It is not deleted: the Hidden section lists it. */
    fun hideVideo(video: VideoEntity, reason: String = "hidden by you") {
        viewModelScope.launch {
            dbRef.hidden().hide(hiddenVideo(video, reason))
        }
    }

    /** Leaves this channel's live streams out of the feed, and leaves its uploads alone. */
    fun hideLiveChannel(channelUrl: String, channelName: String) {
        viewModelScope.launch {
            dbRef.hidden().hide(
                hiddenLiveChannel(channelUrl, channelName, "live streams hidden by you"),
            )
        }
    }

    fun unhide(key: String) {
        viewModelScope.launch { dbRef.hidden().unhide(key) }
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
