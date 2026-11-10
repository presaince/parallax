/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.SortOption
import com.presaince.parallax.core.SortState
import com.presaince.parallax.core.sortList
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.model.Channel
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a channel page can be read as. The filter is the extractor's own name for the tab, so the
 * screen never invents one the channel does not have: `Watched` is local and `About` is text.
 */
enum class ChannelTab(val label: String, val filter: String?) {
    VIDEOS("Videos", "videos"),
    SHORTS("Shorts", "shorts"),
    LIVE("Live", "livestreams"),
    WATCHED("Watched", null),
    ABOUT("About", null),
}

/** What one of the channel's own videos looks like in the local watch history. */
data class WatchedHere(
    val videoId: String,
    val title: String,
    val thumbUrl: String?,
    val watchedAt: Long,
)

/**
 * Which chips the page offers: the extractor's tabs this screen can draw, then the two local ones.
 * A channel with no Shorts tab must not offer Shorts — an empty page reads as a broken app.
 */
fun channelTabsFor(available: List<String>): List<ChannelTab> = buildList {
    ChannelTab.entries.forEach { tab ->
        if (tab.filter == null || tab.filter in available) add(tab)
    }
}

data class ChannelUiState(
    val channel: Channel? = null,
    val tab: ChannelTab = ChannelTab.VIDEOS,
    /** Tabs this channel actually has, in the order the page offers them. */
    val tabs: List<ChannelTab> = listOf(ChannelTab.VIDEOS),
    val items: List<VideoSummary> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val subscribed: Boolean = false,
    val sort: SortState = SortState(option = SortOption.DATE, ascending = false),
    val watched: List<WatchedHere> = emptyList(),
) {
    /** The list the videos tab draws: sorted, with the channel's pinned-first nothing to honour. */
    val sorted: List<VideoSummary>
        get() = sortList(
            items,
            sort,
            date = { it.publishedAtMs },
            views = { it.viewCount },
            duration = { it.durationSec },
        )
}

class ChannelViewModel(
    private val source: VideoSource,
    private val db: ParallaxDb,
    private val channelUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    /** Every watched row with the channel it belongs to, until the channel's name is known. */
    private var historyRows: List<Pair<WatchedHere, String>> = emptyList()

    init {
        loadInfo()
        loadTab(ChannelTab.VIDEOS)
        watchSubscription()
        watchHistory()
    }

    fun loadFirstPage() = loadTab(_state.value.tab)

    fun loadMore() {
        val next = _state.value.next ?: return
        if (_state.value.loading) return
        fetch(_state.value.tab, page = next, append = true)
    }

    /** Pull to refresh: the channel page is a list like any other and can go stale. */
    fun refresh() {
        val tab = _state.value.tab
        if (tab.filter == null) return
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            when (val result = source.channelTab(channelUrl, requireNotNull(tab.filter), null)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        items = result.value.items,
                        next = result.value.next,
                        refreshing = false,
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(refreshing = false, error = result.message)
                }
            }
        }
    }

    /** A tab change, or the same tab again: the local tabs never touch the network. */
    fun selectTab(tab: ChannelTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, items = emptyList(), next = null, error = null) }
        if (tab.filter != null) loadTab(tab)
    }

    fun setSort(state: SortState) {
        _state.update { it.copy(sort = state) }
    }

    /**
     * Subscribe or leave. There is no account: a subscription is a row on this phone, which is
     * why the button works offline and why the feed refresh follows it.
     */
    fun toggleSubscription() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            if (_state.value.subscribed) {
                db.subscriptions().delete(channelUrl)
            } else {
                db.subscriptions().insertAll(
                    listOf(SubscriptionEntity(url = channelUrl, name = channel.name, addedAt = 0)),
                )
            }
        }
    }

    private fun loadInfo() {
        viewModelScope.launch {
            when (val result = source.channelInfo(channelUrl)) {
                is AppResult.Ok -> {
                    val channel = result.value
                    val tabs = channelTabsFor(channel.tabs)
                    _state.update { it.copy(channel = channel, tabs = tabs.ifEmpty { listOf(ChannelTab.VIDEOS) }) }
                    refreshWatched()
                }
                // The list still works without the header: a failed info call is not a failed page.
                is AppResult.Err -> Unit
            }
        }
    }

    private fun loadTab(tab: ChannelTab) {
        if (tab.filter == null) return
        _state.update { it.copy(items = emptyList(), next = null) }
        fetch(tab, page = null, append = false)
    }

    private fun fetch(tab: ChannelTab, page: String?, append: Boolean) {
        val filter = tab.filter ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = source.channelTab(channelUrl, filter, page)) {
                is AppResult.Ok -> _state.update {
                    // A page that arrives after the reader changed tab belongs to the old tab.
                    if (it.tab != tab) {
                        it.copy(loading = false)
                    } else {
                        it.copy(
                            items = if (append) it.items + result.value.items else result.value.items,
                            next = result.value.next,
                            loading = false,
                        )
                    }
                }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    private fun watchSubscription() {
        viewModelScope.launch {
            db.subscriptions().observeAll().collect { all ->
                _state.update { it.copy(subscribed = all.any { row -> row.url == channelUrl }) }
            }
        }
    }

    /** The channel's videos the reader has already opened, newest first. Local, so it is free. */
    private fun watchHistory() {
        viewModelScope.launch {
            db.history().observeGrouped().collect { groups ->
                historyRows = groups.map { group ->
                    WatchedHere(
                        videoId = group.videoId,
                        title = group.title,
                        thumbUrl = group.thumbUrl,
                        watchedAt = group.watchedAt,
                    ) to group.channelName
                }
                refreshWatched()
            }
        }
    }

    /**
     * History arrives before the channel's name does, and the name is what decides which rows are
     * this channel's. Both writes call this, so neither order can leave the Watched tab empty.
     */
    private fun refreshWatched() {
        val name = _state.value.channel?.name ?: return
        val mine = historyRows.filter { (_, channel) -> channel.equals(name, ignoreCase = true) }
        _state.update { it.copy(watched = mine.map { row -> row.first }) }
    }
}
