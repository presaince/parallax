/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import org.json.JSONObject

/** What a file turned out to be, decided by its content rather than its name. */
sealed interface BackupFile {
    /** Subscriptions, settings and recipes in one file: what this app writes. */
    data class Combined(val json: JSONObject) : BackupFile

    /** A settings object on its own, including files written before the combined backup. */
    data class Settings(val json: JSONObject) : BackupFile

    /** A subscription list in any shape the importer already reads, as text. */
    data class Subscriptions(val text: String) : BackupFile

    /** One recipe in Bored's own JSON. */
    data class Recipe(val json: String) : BackupFile

    data class Unknown(val reason: String) : BackupFile
}

/**
 * Reads a file by what is in it. People rename files, rename their extensions and pick the wrong
 * one; a picker that trusts the name turns all three into "could not read that file".
 *
 * The order matters: a combined backup also has a `subscriptions` key, and a recipe also has a
 * `version`, so the explicit markers are read before the shapes.
 */
object BackupReader {

    fun read(text: String): BackupFile {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return BackupFile.Unknown("That file is empty")

        // A bare array only ever holds channels: it is the LibreTube-shaped list without a wrapper.
        if (trimmed.startsWith("[")) return BackupFile.Subscriptions(trimmed)

        val root = runCatching { JSONObject(trimmed) }.getOrNull()
            ?: return if (looksLikeCsv(trimmed)) {
                BackupFile.Subscriptions(trimmed)
            } else {
                BackupFile.Unknown("That file is not JSON Parallax can read")
            }

        return when {
            root.optString("kind") == Backup.KIND && root.has("settings") ->
                BackupFile.Combined(root)
            root.optString("app") == "parallax" -> BackupFile.Settings(root)
            root.has("subscriptions") || root.has("app_version") ->
                BackupFile.Subscriptions(trimmed)
            root.has("from") && root.has("walk") -> BackupFile.Recipe(trimmed)
            else -> BackupFile.Unknown(
                "That JSON is not a backup, a subscription list or a recipe",
            )
        }
    }

    /** A Takeout export is a spreadsheet: a header, commas and channel URLs. */
    private fun looksLikeCsv(text: String): Boolean =
        text.contains(',') && text.contains("youtube.com", ignoreCase = true)
}
