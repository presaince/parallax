/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file Data can offer: found by listing the chosen folder, or remembered from this app. */
data class FoundFile(
    val uri: Uri,
    val name: String,
    val modified: Long,
    val inFolder: Boolean,
)

/**
 * The backups a phone can see, without asking for storage permission.
 *
 * Two sources, one list. A folder the reader picked once through the system picker is listed on
 * each visit — that is where a NewPipe export downloaded elsewhere lands. On top of it sit the
 * files this app wrote itself, remembered by URI so a backup appears the moment it is made even
 * before a folder is chosen. Discovery is SAF: a tree grant, never a blanket read.
 */
object BackupFiles {

    private const val PREFS = "parallax"
    private const val KEY_FOLDER = "backup_folder"
    private const val KEY_KNOWN = "backup_known"
    private const val MAX_ROWS = 8
    private const val MAX_KNOWN = 5

    /** The folder the reader picked, or null when they never did. */
    fun folder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)
        ?.let(Uri::parse)

    fun rememberFolder(context: Context, folder: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs(context).edit().putString(KEY_FOLDER, folder.toString()).apply()
    }

    /** A file this app just wrote, so it is offered even with no folder chosen. */
    fun remember(context: Context, uri: Uri, name: String) {
        val entries = knownRaw(context)
            .filterNot { it.substringBefore('|') == uri.toString() }
            .take(MAX_KNOWN - 1)
            .toMutableList()
        entries.add(0, "$uri|$name")
        prefs(context).edit().putString(KEY_KNOWN, entries.joinToString("\n")).apply()
    }

    /** Everything worth offering. The folder's files first, then this app's own, newest first. */
    suspend fun load(context: Context): List<FoundFile> = withContext(Dispatchers.IO) {
        val inFolder = folder(context)?.let { listFolder(context, it) }.orEmpty()
        merge(inFolder, known(context))
    }

    internal fun merge(folder: List<FoundFile>, known: List<FoundFile>): List<FoundFile> {
        val seen = mutableSetOf<String>()
        return (folder.sortedByDescending { it.modified } + known)
            .filter { visible(it.name) && seen.add(it.uri.toString()) }
            .take(MAX_ROWS)
    }

    /** Only the three shapes the import can read. A folder full of photos stays a folder. */
    internal fun visible(name: String): Boolean = name.lowercase().let { lower ->
        lower.endsWith(".json") || lower.endsWith(".zip") || lower.endsWith(".csv")
    }

    private fun known(context: Context): List<FoundFile> = knownRaw(context).mapNotNull { line ->
        val cut = line.indexOf('|')
        if (cut <= 0) return@mapNotNull null
        val uri = runCatching { Uri.parse(line.substring(0, cut)) }.getOrNull()
            ?: return@mapNotNull null
        FoundFile(uri = uri, name = line.substring(cut + 1), modified = 0L, inFolder = false)
    }

    private fun listFolder(context: Context, tree: Uri): List<FoundFile> = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val rows = mutableListOf<FoundFile>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (!visible(name)) continue
                rows += FoundFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = name,
                    modified = cursor.getLong(2),
                    inFolder = true,
                )
            }
        }
        rows
    }.getOrElse { emptyList() }

    private fun knownRaw(context: Context): List<String> = prefs(context)
        .getString(KEY_KNOWN, null)
        ?.lineSequence()
        ?.filter { it.isNotBlank() }
        ?.toList()
        .orEmpty()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
