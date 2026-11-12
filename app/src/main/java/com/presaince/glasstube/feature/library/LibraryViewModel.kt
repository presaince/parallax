/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.library

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.data.db.ChannelEntity
import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.db.SubscriptionEntity
import com.presaince.glasstube.data.db.toVideo
import com.presaince.glasstube.data.model.Video
import com.presaince.glasstube.data.importer.ImportedSubscription
import com.presaince.glasstube.data.importer.ImportResult
import com.presaince.glasstube.data.importer.SubscriptionExporter
import com.presaince.glasstube.data.importer.SubscriptionImporter
import com.presaince.glasstube.data.importer.SubscriptionZip
import com.presaince.glasstube.data.library.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryMode { GRID, SECTIONS, COLLECTIONS }

data class LibraryUiState(
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val channels: Map<String, ChannelEntity> = emptyMap(),
    val videos: List<Video> = emptyList(),
    val continueWatching: List<ProgressEntity> = emptyList(),
    val progress: Map<String, ProgressEntity> = emptyMap(),
    val mode: LibraryMode = LibraryMode.GRID,
    val busy: Boolean = false,
    val message: String? = null,
) {
    fun avatarFor(url: String): String? = channels[url]?.avatarUrl

    fun videosFor(subscription: SubscriptionEntity): List<Video> =
        videos.filter { video ->
            video.channelUrl == subscription.url || video.channelName == subscription.name
        }.take(6)

    fun watchedFraction(videoId: String): Float? {
        val entry = progress[videoId] ?: return null
        if (entry.durationMs <= 0) return null
        return (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    }
}

class LibraryViewModel(
    private val db: GlassTubeDb,
    private val resolver: ContentResolver,
    private val content: ContentRepository,
) : ViewModel() {

    private val status = MutableStateFlow(LibraryUiState())

    fun refresh() {
        if (status.value.busy) return
        viewModelScope.launch {
            status.value = status.value.copy(busy = true, message = null)
            val result = content.refreshFeed()
            status.value = status.value.copy(
                busy = false,
                message = if (result.failures > 0) {
                    "${result.failures} of ${result.channels} channels failed"
                } else {
                    "${result.videos} videos from ${result.channels} channels"
                },
            )
        }
    }

    private val data = combine(
        db.subscriptions().observeAll(),
        db.channels().observeAll(),
        db.videos().observeFeed().map { rows -> rows.map { it.toVideo() } },
        db.progress().observeAll(),
    ) { subscriptions, channels, videos, progress ->
        LibraryUiState(
            subscriptions = subscriptions,
            channels = channels.associateBy { it.url },
            videos = videos,
            continueWatching = progress.sortedByDescending { it.updatedAt }.take(10),
            progress = progress.associateBy { it.videoId },
        )
    }

    val state: StateFlow<LibraryUiState> = combine(data, status) { loaded, current ->
        loaded.copy(mode = current.mode, busy = current.busy, message = current.message)
    }.stateIn(        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setMode(mode: LibraryMode) {
        status.value = status.value.copy(mode = mode)
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            status.value = status.value.copy(busy = true, message = null)

            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                        SubscriptionZip.findSubscriptionsJson(bytes)
                    } else {
                        bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }

            if (text.isNullOrBlank()) {
                status.value = status.value.copy(busy = false, message = "Could not read that file")
                return@launch
            }

            when (val parsed = SubscriptionImporter.parse(text)) {
                is ImportResult.Ok -> {
                    val now = System.currentTimeMillis()
                    val rows = parsed.subscriptions.map {
                        SubscriptionEntity(url = it.url, name = it.name, addedAt = now)
                    }
                    val inserted = withContext(Dispatchers.IO) {
                        db.subscriptions().insertAll(rows)
                    }
                    val added = inserted.count { it != -1L }
                    val skipped = if (parsed.skipped > 0) ", ${parsed.skipped} skipped (no URL)" else ""
                    status.value = status.value.copy(
                        busy = false,
                        message = "${parsed.format}: $added added, ${rows.size - added} already there$skipped",
                    )
                }
                is ImportResult.Err -> status.value = status.value.copy(
                    busy = false,
                    message = parsed.message,
                )
            }
        }
    }

    fun dismissMessage() {
        status.value = status.value.copy(message = null)
    }

    /** The subscription list as a NewPipe-style JSON string, for the export picker to write. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val subs = db.subscriptions().all().map { ImportedSubscription(url = it.url, name = it.name) }
        SubscriptionExporter.newPipeJson(subs)
    }
}
