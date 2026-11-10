/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.db.GlassTubeDb
import com.presaince.glasstube.data.db.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one import did, in the only numbers a person needs: added, already there, skipped. */
data class SubscriptionImportSummary(
    val format: String,
    val added: Int,
    val total: Int,
    val skipped: Int,
) {
    val message: String
        get() = buildString {
            append(format)
            append(": ")
            append(added)
            append(" added, ")
            append(total - added)
            append(" already there")
            if (skipped > 0) {
                append(", ")
                append(skipped)
                append(" skipped (no URL)")
            }
        }
}

/**
 * Read a NewPipe zip/JSON, LibreTube JSON or Takeout CSV and insert its channels. One path for
 * Settings and the wizard, so the two cannot disagree about what an import is.
 *
 * A missing file and an unrecognised file are expected failures and carry a sentence. A database
 * failure is not caught: Room failing is not the reader's typo, and pretending otherwise hides it.
 */
suspend fun importSubscriptions(
    db: GlassTubeDb,
    resolver: ContentResolver,
    uri: Uri,
): AppResult<SubscriptionImportSummary> {
    val text = readImportText(resolver, uri)
        ?: return AppResult.Err("Could not read that file")
    return importSubscriptionText(db, text)
}

/**
 * The file as text, with a NewPipe backup zip unwrapped. One read for every caller: the backup
 * restore needs the same text to decide what the file is before it hands the list on.
 */
suspend fun readImportText(resolver: ContentResolver, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            when {
                bytes == null -> null
                bytes.size > 2 &&
                    bytes[0] == 'P'.code.toByte() &&
                    bytes[1] == 'K'.code.toByte() -> SubscriptionZip.findSubscriptionsJson(bytes)
                else -> bytes.toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

/** Insert a list that is already text. The same parser as [importSubscriptions], one layer down. */
suspend fun importSubscriptionText(
    db: GlassTubeDb,
    text: String,
): AppResult<SubscriptionImportSummary> = withContext(Dispatchers.IO) {
    if (text.isBlank()) return@withContext AppResult.Err("Could not read that file")

    when (val parsed = SubscriptionImporter.parse(text)) {
        is ImportResult.Err -> AppResult.Err(parsed.message)
        is ImportResult.Ok -> {
            val now = System.currentTimeMillis()
            val rows = parsed.subscriptions.map {
                SubscriptionEntity(url = it.url, name = it.name, addedAt = now)
            }
            val inserted = db.subscriptions().insertAll(rows)
            val added = inserted.count { it != -1L }
            AppResult.Ok(
                SubscriptionImportSummary(
                    format = parsed.format,
                    added = added,
                    total = rows.size,
                    skipped = parsed.skipped,
                ),
            )
        }
    }
}
