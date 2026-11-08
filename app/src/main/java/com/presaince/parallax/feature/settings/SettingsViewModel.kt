/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.db.SubscriptionEntity
import com.presaince.parallax.data.importer.ImportResult
import com.presaince.parallax.data.importer.ImportedSubscription
import com.presaince.parallax.data.importer.SubscriptionExporter
import com.presaince.parallax.data.importer.SubscriptionImporter
import com.presaince.parallax.data.importer.SubscriptionZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
)

/** Import and export, which belong with the rest of the settings rather than in a header. */
class SettingsViewModel(
    private val db: ParallaxDb,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }

            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() &&
                        bytes[1] == 'K'.code.toByte()
                    ) {
                        SubscriptionZip.findSubscriptionsJson(bytes)
                    } else {
                        bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }

            if (text.isNullOrBlank()) {
                _state.update { it.copy(busy = false, message = "Could not read that file") }
                return@launch
            }

            when (val parsed = SubscriptionImporter.parse(text)) {
                is ImportResult.Ok -> {
                    val now = System.currentTimeMillis()
                    val rows = parsed.subscriptions.map {
                        SubscriptionEntity(url = it.url, name = it.name, addedAt = now)
                    }
                    val inserted = withContext(Dispatchers.IO) {
                        withContext(Dispatchers.IO) { db.subscriptions().insertAll(rows) }
                    }
                    val added = inserted.count { it != -1L }
                    val skipped = if (parsed.skipped > 0) ", ${parsed.skipped} skipped (no URL)" else ""
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "${parsed.format}: $added added, ${rows.size - added} already there$skipped",
                        )
                    }
                }
                is ImportResult.Err -> _state.update {
                    it.copy(busy = false, message = parsed.message)
                }
            }
        }
    }

    /** The subscription list as NewPipe-style JSON, for the export picker to write. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val subs = db.subscriptions().all().map { ImportedSubscription(url = it.url, name = it.name) }
        SubscriptionExporter.newPipeJson(subs)
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
