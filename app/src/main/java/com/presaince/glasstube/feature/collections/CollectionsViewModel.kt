/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.toVideo
import com.presaince.glasstube.data.model.VideoSummary
import com.presaince.glasstube.data.model.toSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A collection is a rule over your own library, not a folder you fill. Nothing is stored: each one
 * is answered from what is already on disk, so it can never go stale or disagree with the library.
 */
enum class CollectionKind(val label: String, val explanation: String) {
    GATHERING_DUST("Gathering dust", "You started these and never came back."),
    NEARLY_DONE("Almost", "Within the last third of the video."),
    DORMANT("They're back", "A channel that had gone quiet has posted again."),
    UNSEEN("Never opened", "In your feed, never played."),
}

data class CollectionsUiState(
    val selected: CollectionKind = CollectionKind.GATHERING_DUST,
    val lists: Map<CollectionKind, List<VideoSummary>> = emptyMap(),
    val loading: Boolean = true,
)

/**
 * Collections: what your own library already knows, without a model. This is the shape an AI panel
 * would fill later — the same four questions, smarter answers - so the UI does not change when the
 * intelligence does.
 */
class CollectionsViewModel(private val db: GlassTubeDb) : ViewModel() {

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun select(collection: CollectionKind) {
        _state.value = _state.value.copy(selected = collection)
    }

    private fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val computed = withContext(Dispatchers.IO) { compute(now) }
            _state.value = _state.value.copy(lists = computed, loading = false)
        }
    }

    private suspend fun compute(now: Long): Map<CollectionKind, List<VideoSummary>> {
        val videos = db.videos().recent(400).map { it.toVideo() }
        val history = db.history().recent(400).map { it.videoId }.toSet()
        val progress = db.progress().allOnce()

        val dust = progress
            .filter { it.durationMs > 0 && it.positionMs > 30_000 }
            .filter { now - it.updatedAt > 30L * 24 * 60 * 60 * 1000 }
            .sortedBy { it.updatedAt }
            .mapNotNull { saved -> videos.find { it.id == saved.videoId }?.toSummary() }

        val nearlyDone = progress
            .filter { it.durationMs > 0 }
            .filter { val f = it.positionMs.toFloat() / it.durationMs; f in 0.7f..0.96f }
            .sortedByDescending { it.updatedAt }
            .mapNotNull { saved -> videos.find { it.id == saved.videoId }?.toSummary() }

        val byChannel = videos.groupBy { it.channelUrl ?: it.channelName }
        val dormant = byChannel.values.mapNotNull { list ->
            val sorted = list.filter { it.publishedAt != null }.sortedByDescending { it.publishedAt }
            val newest = sorted.firstOrNull() ?: return@mapNotNull null
            val previous = sorted.getOrNull(1) ?: return@mapNotNull null
            val gap = (newest.publishedAt ?: 0) - (previous.publishedAt ?: 0)
            val fresh = now - (newest.publishedAt ?: 0) < 7L * 24 * 60 * 60 * 1000
            if (fresh && gap > 60L * 24 * 60 * 60 * 1000) newest.toSummary() else null
        }

        val unseen = videos
            .filter { it.id !in history }
            .sortedByDescending { it.publishedAt ?: it.lastSeenAt }
            .take(50)
            .map { it.toSummary() }

        return mapOf(
            CollectionKind.GATHERING_DUST to dust,
            CollectionKind.NEARLY_DONE to nearlyDone,
            CollectionKind.DORMANT to dormant,
            CollectionKind.UNSEEN to unseen,
        )
    }
}
