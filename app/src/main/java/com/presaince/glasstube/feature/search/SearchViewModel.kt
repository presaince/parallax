/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.source.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { ALL, SUBS, BORED }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.ALL,
    val loading: Boolean = false,
    val error: String? = null,
    /** Everything the query returned; the tab decides what is shown. */
    val results: List<VideoSummary> = emptyList(),
    /** The query those results belong to, so a mode switch can tell stale from current. */
    val resultsQuery: String = "",
    val visible: List<VideoSummary> = emptyList(),
    val history: List<String> = emptyList(),
    val note: String? = null,
)

/**
 * One query, three readings: everything YouTube returned, only the channels you subscribe to, and
 * a short walk out from the best match. Search history is local.
 */
class SearchViewModel(
    private val source: VideoSource,
    private val db: GlassTubeDb,
    private val preferences: android.content.SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(history = readHistory()))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var subscribedUrls: Set<String> = emptySet()
    private var subscribedNames: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            refreshSubscriptions()
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun selectMode(mode: SearchMode) {
        _state.update { it.copy(mode = mode) }
        val current = _state.value
        // Bored walks out from the query's best match. Switching to it with a query that has not
        // been searched yet used to walk the previous query's results, so the words on screen had
        // nothing to do with the list.
        if (mode == SearchMode.BORED && current.query.isNotBlank() &&
            !current.query.trim().equals(current.resultsQuery, ignoreCase = true)
        ) {
            search()
        } else {
            recompute()
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        // The same query on the same screen is the same search: re-running it made the list
        // shuffle under the reader, which read as the search losing results. Go still re-reads
        // the current set, and a changed query or an earlier failure does hit the network.
        val previous = _state.value
        if (query.equals(previous.resultsQuery, ignoreCase = true) &&
            previous.results.isNotEmpty() && previous.error == null
        ) {
            recompute()
            return
        }

        rememberQuery(query)
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, note = null) }
            // Subscriptions can change while the screen is alive; a stale set silently drops
            // matches, which reads as an unreliable search.
            refreshSubscriptions()
            when (val result = source.search(query)) {
                is AppResult.Ok -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            results = result.value.items,
                            resultsQuery = query,
                            history = readHistory(),
                        )
                    }
                    recompute()
                }
                is AppResult.Err -> _state.update {
                    it.copy(loading = false, error = result.message, results = emptyList())
                }
            }
        }
    }

    private suspend fun refreshSubscriptions() {
        val subs = withContext(Dispatchers.IO) { db.subscriptions().all() }
        subscribedUrls = subs.map { it.url.trimEnd('/') }.toSet()
        subscribedNames = subs.map { it.name.trim().lowercase() }.toSet()
    }

    private fun recompute() {
        val current = _state.value
        when (current.mode) {
            SearchMode.ALL -> _state.update {
                it.copy(visible = current.results, note = "${current.results.size} results")
            }
            SearchMode.SUBS -> {
                val mine = current.results.filter { video ->
                    video.channelUrl.orEmpty().trimEnd('/') in subscribedUrls ||
                        video.channelName.trim().lowercase() in subscribedNames
                }
                _state.update {
                    it.copy(
                        visible = mine,
                        note = if (mine.isEmpty()) {
                            null
                        } else {
                            "${com.presaince.glasstube.core.plural(mine.size.toLong(), "video")} from your subscriptions"
                        },
                    )
                }
            }
            SearchMode.BORED -> walkFromFirstResult(
                seed = current.results.firstOrNull(),
                query = current.resultsQuery,
            )
        }
    }

    /** Bored: the related graph out of the closest match, which is a different reading of a query. */
    private fun walkFromFirstResult(seed: VideoSummary?, query: String) {
        if (seed == null) {
            _state.update {
                it.copy(visible = emptyList(), note = "Search something first, then Bored walks out from it")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val related = source.related(seed.id)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        visible = related.value,
                        note = if (query.isBlank()) {
                            "Related to ${seed.title.take(40)}"
                        } else {
                            "Related to your search for “$query”"
                        },
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(loading = false, error = related.message)
                }
            }
        }
    }

    private fun readHistory(): List<String> =
        preferences.getString(HISTORY_KEY, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.take(10)
            .orEmpty()

    private fun rememberQuery(query: String) {
        val updated = (listOf(query) + readHistory().filterNot { it.equals(query, true) })
            .take(10)
        preferences.edit().putString(HISTORY_KEY, updated.joinToString("\n")).apply()
    }

    fun useHistory(query: String) {
        _state.update { it.copy(query = query) }
        search()
    }

    fun clearHistory() {
        preferences.edit().remove(HISTORY_KEY).apply()
        _state.update { it.copy(history = emptyList()) }
    }

    /** One pill, one query: a long press removes just that search, not the whole list. */
    fun removeHistory(query: String) {
        val updated = readHistory().filterNot { it.equals(query, ignoreCase = true) }
        preferences.edit().putString(HISTORY_KEY, updated.joinToString("\n")).apply()
        _state.update { it.copy(history = updated) }
    }

    private companion object {
        const val HISTORY_KEY = "search_history"
    }
}
