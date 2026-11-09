/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.plural
import com.presaince.parallax.data.backup.Backup
import com.presaince.parallax.data.backup.BackupFile
import com.presaince.parallax.data.backup.BackupFiles
import com.presaince.parallax.data.backup.BackupReader
import com.presaince.parallax.data.backup.SettingsExport
import com.presaince.parallax.data.backup.SettingsImport
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.data.discover.RecipeRepository
import com.presaince.parallax.data.discover.RecipeResult
import com.presaince.parallax.data.importer.ImportedSubscription
import com.presaince.parallax.data.importer.importSubscriptionText
import com.presaince.parallax.data.importer.readImportText
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
    private val db: ParallaxDb,
    private val context: Context,
    private val resolver: ContentResolver,
    private val recipes: RecipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun import(uri: Uri, apiKeyPresent: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val text = readImportText(resolver, uri)
            val (message, settingsApplied) = if (text.isNullOrBlank()) {
                "Could not read that file" to false
            } else {
                restore(BackupReader.read(text), apiKeyPresent)
            }
            _state.update {
                it.copy(
                    busy = false,
                    message = message,
                    settingsApplied = it.settingsApplied + if (settingsApplied) 1 else 0,
                )
            }
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
                    val json = Backup.json(settings, subscriptions, userRecipes)
                    val written = resolver.openOutputStream(uri)
                        ?.use { it.write(json.toByteArray()) } != null
                    if (written) {
                        // Offered back on the Data page from the next visit, folder or not.
                        val name = uri.lastPathSegment?.substringAfterLast('/')
                            ?: "parallax-backup.json"
                        BackupFiles.remember(context, uri, name)
                        Backup.receipt(
                            subscriptions = subscriptions.size,
                            settings = SettingsExport.count(settings),
                            recipes = userRecipes.size,
                        )
                    } else {
                        "Could not write the file"
                    }
                }.getOrElse { "Could not write the file" }
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private suspend fun restore(file: BackupFile, apiKeyPresent: Boolean): Pair<String, Boolean> =
        when (file) {
            is BackupFile.Combined -> combined(file.json, apiKeyPresent)
            is BackupFile.Settings ->
                SettingsImport.apply(context, file.json, apiKeyPresent).message to true
            is BackupFile.Subscriptions -> when (val result = importSubscriptionText(db, file.text)) {
                is AppResult.Ok -> result.value.message to false
                is AppResult.Err -> result.message to false
            }
            is BackupFile.Recipe -> when (val result = recipes.import(file.json)) {
                is RecipeResult.Ok -> "Imported ${result.recipe.name}" to false
                is RecipeResult.Err -> result.problems.take(3).joinToString("; ") to false
            }
            is BackupFile.Unknown -> file.reason to false
        }

    private suspend fun combined(json: JSONObject, apiKeyPresent: Boolean): Pair<String, Boolean> {
        val parts = mutableListOf<String>()
        var applied = false

        json.optJSONObject("subscriptions")?.let { subscriptions ->
            when (val result = importSubscriptionText(db, subscriptions.toString())) {
                is AppResult.Ok -> parts += result.value.message
                is AppResult.Err -> parts += result.message
            }
        }
        json.optJSONObject("settings")?.let { settings ->
            parts += SettingsImport.apply(context, settings, apiKeyPresent).message
            applied = true
        }
        parts += restoreRecipes(json.optJSONArray("recipes"))

        val message = parts.filter { it.isNotBlank() }.joinToString(". ")
        return (message.ifBlank { "That backup is empty" }) to applied
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

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
