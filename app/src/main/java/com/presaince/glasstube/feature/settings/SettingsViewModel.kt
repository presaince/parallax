/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.core.plural
import com.presaince.glasstube.data.backup.Backup
import com.presaince.glasstube.data.backup.BackupFile
import com.presaince.glasstube.data.backup.BackupFiles
import com.presaince.glasstube.data.backup.BackupReader
import com.presaince.glasstube.data.backup.SettingsExport
import com.presaince.glasstube.data.backup.SettingsImport
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.NoteEntity
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.discover.RecipeRepository
import com.presaince.glasstube.data.discover.RecipeResult
import com.presaince.glasstube.data.importer.ImportedSubscription
import com.presaince.glasstube.data.importer.importSubscriptionText
import com.presaince.glasstube.data.importer.readImportText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    /** Bumped when a restore writes preferences, so the live theme can re-read them. */
    val settingsApplied: Int = 0,
)

/**
 * One import and one export. The file decides what it is: a combined backup, a settings object, a
 * subscription list from another client, or a single recipe. Four buttons used to ask the reader
 * to know which of those they were holding.
 */
class SettingsViewModel(
    private val db: GlassTubeDb,
    private val context: Context,
    private val resolver: ContentResolver,
    private val recipes: RecipeRepository,
    /** Asks the feed to fetch the channels a restore just added. */
    private val requestFeedRefresh: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun import(uri: Uri, apiKeyPresent: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val text = readImportText(resolver, uri)
            if (text.isNullOrBlank()) {
                _state.update { it.copy(busy = false, message = "Could not read that file") }
            } else {
                import(text, apiKeyPresent)
            }
        }
    }

    /**
     * The file as text, already read. Separated from the picker so a test can hand it a backup
     * without a ContentResolver, and so the feed request has one place to fire from: a restore
     * that adds channels must make the feed fetch them, or the list stays empty until a pull.
     */
    internal suspend fun import(text: String, apiKeyPresent: Boolean) {
        _state.update { it.copy(busy = true, message = null) }
        val outcome = restore(BackupReader.read(text), apiKeyPresent)
        if (outcome.subscriptionsAdded > 0) requestFeedRefresh()
        _state.update {
            it.copy(
                busy = false,
                message = outcome.message,
                settingsApplied = it.settingsApplied + if (outcome.settingsApplied) 1 else 0,
            )
        }
    }

    /** Write the whole phone to the picked file, then say what actually went in. */
    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val subscriptions = db.subscriptions().all()
                        .map { ImportedSubscription(url = it.url, name = it.name) }
                    val settings = SettingsExport.values(context)
                    val userRecipes = recipes.userJsons()
                    val notes = db.notes().allForBackup()
                    val history = db.history().allForBackup()
                    val progress = db.progress().allOnce()
                    val json = Backup.json(
                        settings = settings,
                        subscriptions = subscriptions,
                        recipes = userRecipes,
                        notes = notes,
                        history = history,
                        progress = progress,
                    )
                    val written = resolver.openOutputStream(uri)
                        ?.use { it.write(json.toByteArray()) } != null
                    if (written) {
                        // Offered back on the Data page from the next visit, folder or not.
                        val name = uri.lastPathSegment?.substringAfterLast('/')
                            ?: "glasstube-backup.json"
                        BackupFiles.remember(context, uri, name)
                        Backup.receipt(
                            subscriptions = subscriptions.size,
                            settings = SettingsExport.count(settings),
                            recipes = userRecipes.size,
                            notes = notes.size,
                            history = history.size,
                            progress = progress.size,
                        )
                    } else {
                        "Could not write the file"
                    }
                }.getOrElse { "Could not write the file" }
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private suspend fun restore(file: BackupFile, apiKeyPresent: Boolean): RestoreOutcome =
        when (file) {
            is BackupFile.Combined -> combined(file.json, apiKeyPresent)
            is BackupFile.Settings -> RestoreOutcome(
                message = SettingsImport.apply(context, file.json, apiKeyPresent).message,
                settingsApplied = true,
            )
            is BackupFile.Subscriptions -> when (val result = importSubscriptionText(db, file.text)) {
                is AppResult.Ok -> RestoreOutcome(
                    message = result.value.message,
                    subscriptionsAdded = result.value.added,
                )
                is AppResult.Err -> RestoreOutcome(result.message)
            }
            is BackupFile.Recipe -> when (val result = recipes.import(file.json)) {
                is RecipeResult.Ok -> RestoreOutcome("Imported ${result.recipe.name}")
                is RecipeResult.Err -> RestoreOutcome(result.problems.take(3).joinToString("; "))
            }
            is BackupFile.Unknown -> RestoreOutcome(file.reason)
        }

    /** What one restore did, beyond the sentence the reader sees. */
    private data class RestoreOutcome(
        val message: String,
        /** True when preferences were written, so the live theme, player and cards must re-read. */
        val settingsApplied: Boolean = false,
        /** Channels this file added; when above zero the feed has to fetch them. */
        val subscriptionsAdded: Int = 0,
    )

    private suspend fun combined(json: JSONObject, apiKeyPresent: Boolean): RestoreOutcome {
        val parts = mutableListOf<String>()
        var applied = false
        var added = 0

        json.optJSONObject("subscriptions")?.let { subscriptions ->
            when (val result = importSubscriptionText(db, subscriptions.toString())) {
                is AppResult.Ok -> {
                    parts += result.value.message
                    added = result.value.added
                }
                is AppResult.Err -> parts += result.message
            }
        }
        json.optJSONObject("settings")?.let { settings ->
            parts += SettingsImport.apply(context, settings, apiKeyPresent).message
            applied = true
        }
        parts += restoreRecipes(json.optJSONArray("recipes"))
        parts += restoreNotes(json.optJSONArray("notes"))
        parts += restoreHistory(json.optJSONArray("history"))
        parts += restoreProgress(json.optJSONArray("progress"))

        val message = parts.filter { it.isNotBlank() }.joinToString(". ")
        return RestoreOutcome(
            message = message.ifBlank { "That backup is empty" },
            settingsApplied = applied,
            subscriptionsAdded = added,
        )
    }

    /** Each recipe is validated on its own: one bad file does not cost the other nine. */
    private suspend fun restoreRecipes(array: JSONArray?): String {
        if (array == null || array.length() == 0) return ""
        var imported = 0
        var firstProblem: String? = null
        for (index in 0 until array.length()) {
            when (val result = recipes.import(array.optString(index))) {
                is RecipeResult.Ok -> imported++
                is RecipeResult.Err -> if (firstProblem == null) {
                    firstProblem = result.problems.firstOrNull()
                }
            }
        }
        val summary = if (imported > 0) "${plural(imported.toLong(), "recipe")} imported" else ""
        return listOfNotNull(summary, firstProblem).joinToString(". ")
    }

    /**
     * Notes come back by what they say, not by row id, so restoring the same file twice adds
     * nothing and a note that was deleted here does not return as a duplicate.
     */
    private suspend fun restoreNotes(array: JSONArray?): String {
        if (array == null || array.length() == 0) return ""
        val rows = ArrayList<NoteEntity>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val videoId = row.optString("videoId")
            if (videoId.isBlank()) continue
            rows += NoteEntity(
                videoId = videoId,
                atSec = row.optLong("atSec"),
                text = row.optString("text"),
                createdAt = row.optLong("createdAt"),
                kind = row.optString("kind").ifBlank { "deck" },
            )
        }
        val existing = db.notes().allForBackup().mapTo(HashSet()) { Backup.noteKey(it) }
        val added = Backup.fresh(existing, rows) { Backup.noteKey(it) }
        added.forEach { db.notes().insert(it) }
        return if (added.isEmpty()) "" else "${plural(added.size.toLong(), "note")} imported"
    }

    /** Plays keep their moment, for the same reason: a play is the video plus when it was opened. */
    private suspend fun restoreHistory(array: JSONArray?): String {
        if (array == null || array.length() == 0) return ""
        val rows = ArrayList<HistoryEntity>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val videoId = row.optString("videoId")
            if (videoId.isBlank()) continue
            rows += HistoryEntity(
                videoId = videoId,
                title = row.optString("title"),
                channelName = row.optString("channelName"),
                openedAt = row.optLong("openedAt"),
                watchedAt = if (row.isNull("watchedAt")) null else row.optLong("watchedAt"),
                thumbUrl = if (row.isNull("thumbUrl")) null else row.optString("thumbUrl"),
            )
        }
        val existing = db.history().allForBackup().mapTo(HashSet()) { Backup.historyKey(it) }
        val added = Backup.fresh(existing, rows) { Backup.historyKey(it) }
        added.forEach { db.history().add(it) }
        return if (added.isEmpty()) "" else "${plural(added.size.toLong(), "play")} imported"
    }

    /**
     * One row per video, and the newer position wins. A restore must not rewind a video that has
     * been watched since the file was written, so the file only replaces a row it is ahead of.
     */
    private suspend fun restoreProgress(array: JSONArray?): String {
        if (array == null || array.length() == 0) return ""
        val existing = db.progress().allOnce().associateBy { it.videoId }
        val added = ArrayList<ProgressEntity>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val videoId = row.optString("videoId")
            if (videoId.isBlank()) continue
            val incoming = ProgressEntity(
                videoId = videoId,
                title = row.optString("title"),
                positionMs = row.optLong("positionMs"),
                durationMs = row.optLong("durationMs"),
                updatedAt = row.optLong("updatedAt"),
                thumbUrl = if (row.isNull("thumbUrl")) null else row.optString("thumbUrl"),
            )
            val here = existing[videoId]
            if (here == null || incoming.updatedAt > here.updatedAt) added += incoming
        }
        added.forEach { db.progress().save(it) }
        return if (added.isEmpty()) "" else "${plural(added.size.toLong(), "resume point")} imported"
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
