/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.model.VideoSummary
import com.presaince.parallax.data.source.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelUiState(
    val items: List<VideoSummary> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class ChannelViewModel(
    private val source: VideoSource,
    private val channelUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        _state.value = ChannelUiState(loading = true)
        fetch(page = null, append = false)
    }

    fun loadMore() {
        val next = _state.value.next ?: return
        if (_state.value.loading) return
        fetch(page = next, append = true)
    }

    /** Pull to refresh: the channel page is a list like any other and can go stale. */
    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            when (val result = source.channelUploads(channelUrl, null)) {
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

    private fun fetch(page: String?, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = source.channelUploads(channelUrl, page)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        items = if (append) it.items + result.value.items else result.value.items,
                        next = result.value.next,
                        loading = false,
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
