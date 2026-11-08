/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.resync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.resync.AlignedMoment
import com.presaince.parallax.data.resync.ResyncEngine
import com.presaince.parallax.data.resync.ResyncPrompt
import com.presaince.parallax.data.resync.ResyncSettings
import com.presaince.parallax.data.resync.ResyncStore
import com.presaince.parallax.data.resync.TimestampedComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResyncUiState(
    val settings: ResyncSettings = ResyncSettings(),
    val settingsOpen: Boolean = false,
    val timestamped: List<TimestampedComment> = emptyList(),
    val moments: List<AlignedMoment> = emptyList(),
    val comments: Map<String, Comment> = emptyMap(),
    val trackLanguage: String? = null,
    val loadingComments: Boolean = false,
    val aligning: Boolean = false,
    val message: String? = null,
)

class ResyncViewModel(
    private val content: ContentRepository,
    private val engine: ResyncEngine,
    private val store: ResyncStore,
    private val videoId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ResyncUiState(settings = store.load()))
    val state: StateFlow<ResyncUiState> = _state.asStateFlow()

    init {
        loadCached()
    }

    fun toggleSettings() {
        _state.update { it.copy(settingsOpen = !it.settingsOpen) }
    }

    fun saveSettings(enabled: Boolean, endpoint: String, model: String, apiKey: String) {
        val updated = ResyncSettings(
            enabled = enabled,
            endpoint = endpoint.trim().ifBlank { ResyncSettings.DEFAULT_ENDPOINT },
            model = model.trim().ifBlank { ResyncSettings.DEFAULT_MODEL },
            apiKey = apiKey,
        )
        store.save(updated)
        _state.update {
            it.copy(settings = store.load(), message = if (updated.enabled) "Resync on" else "Resync off")
        }
    }

    /** Reads up to [PAGES] pages of comments; enough for the gate, not a scroll marathon. */
    fun loadComments() {
        if (_state.value.loadingComments) return
        viewModelScope.launch {
            _state.update { it.copy(loadingComments = true, message = null) }
            val loaded = mutableListOf<Comment>()
            var page: String? = null
            var pages = 0
            while (pages < PAGES) {
                pages++
                when (val result = content.comments(videoId, page)) {
                    is AppResult.Ok -> {
                        loaded += result.value.items
                        page = result.value.next
                        if (page == null) break
                    }
                    is AppResult.Err -> {
                        _state.update {
                            it.copy(loadingComments = false, message = result.message)
                        }
                        return@launch
                    }
                }
            }

            val timestamped = ResyncPrompt.timestamped(loaded)
            _state.update {
                it.copy(
                    loadingComments = false,
                    timestamped = timestamped,
                    comments = loaded.associateBy { comment -> comment.id },
                    message = if (timestamped.isEmpty()) {
                        "No timestamped comments in ${loaded.size} loaded"
                    } else {
                        "${timestamped.size} timestamped of ${loaded.size} comments"
                    },
                )
            }
        }
    }

    fun align() {
        val comments = _state.value.comments.values.toList()
        if (comments.isEmpty()) {
            loadComments()
            return
        }
        if (_state.value.aligning) return
        viewModelScope.launch {
            _state.update { it.copy(aligning = true, message = null) }
            when (val result = engine.align(videoId, comments)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        aligning = false,
                        moments = result.value.moments,
                        trackLanguage = result.value.trackLanguage,
                        message = "${result.value.moments.size} aligned, " +
                            "${result.value.calls} model call(s)",
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(aligning = false, message = result.message)
                }
            }
        }
    }

    private fun loadCached() {
        viewModelScope.launch {
            val cached = engine.stored(videoId)
            if (cached.isNotEmpty()) _state.update { it.copy(moments = cached) }
        }
    }

    private companion object {
        const val PAGES = 3
    }
}
