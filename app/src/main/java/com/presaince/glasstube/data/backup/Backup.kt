/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import com.presaince.glasstube.core.plural
import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.NoteEntity
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.importer.ImportedSubscription
import com.presaince.glasstube.data.importer.SubscriptionExporter
import org.json.JSONArray
import org.json.JSONObject

/**
 * One file that carries a phone: subscriptions, settings, the reader's own Bored recipes, the
 * notes they wrote, every play they left behind, and where each video stopped.
 *
 * The subscription half is the exact NewPipe-shaped object the exporter already writes, so a
 * restore goes back through the importer that is already tested instead of a second reader that
 * could drift from it. Settings keep their own object, and recipes keep their raw JSON, because a
 * recipe is data — reformatting one on the way out would be a second place to get the schema wrong.
 *
 * Notes, plays and resume points carry no row ids. An id is the database's own bookkeeping, and a
 * restore into another phone would either collide or arrive as a number that means nothing there.
 */
object Backup {

    const val KIND = "backup"

    /** 2: resume points joined notes and plays. The reader does not gate on it. */
    const val VERSION = 2

    fun json(
        settings: JSONObject,
        subscriptions: List<ImportedSubscription>,
        recipes: List<String>,
        notes: List<NoteEntity> = emptyList(),
        history: List<HistoryEntity> = emptyList(),
        progress: List<ProgressEntity> = emptyList(),
    ): String = JSONObject()
        .put("app", "glasstube")
        .put("kind", KIND)
        .put("version", VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("subscriptions", JSONObject(SubscriptionExporter.newPipeJson(subscriptions)))
        .put("settings", settings)
        .put("recipes", JSONArray(recipes))
        .put("notes", JSONArray(notes.map(::noteJson)))
        .put("history", JSONArray(history.map(::historyJson)))
        .put("progress", JSONArray(progress.map(::progressJson)))
        .toString(2)

    private fun noteJson(note: NoteEntity): JSONObject = JSONObject()
        .put("videoId", note.videoId)
        .put("atSec", note.atSec)
        .put("text", note.text)
        .put("createdAt", note.createdAt)
        .put("kind", note.kind)

    private fun historyJson(row: HistoryEntity): JSONObject = JSONObject()
        .put("videoId", row.videoId)
        .put("title", row.title)
        .put("channelName", row.channelName)
        .put("openedAt", row.openedAt)
        .put("watchedAt", row.watchedAt ?: JSONObject.NULL)
        .put("thumbUrl", row.thumbUrl ?: JSONObject.NULL)

    /** The one row that makes the feed show a progress line and the player resume. */
    private fun progressJson(row: ProgressEntity): JSONObject = JSONObject()
        .put("videoId", row.videoId)
        .put("title", row.title)
        .put("positionMs", row.positionMs)
        .put("durationMs", row.durationMs)
        .put("updatedAt", row.updatedAt)
        .put("thumbUrl", row.thumbUrl ?: JSONObject.NULL)

    /**
     * The receipt under an export: what the file actually carries, counted from the same things
     * that were written. Empty parts are left out, so a fresh install says "Saved 34 settings"
     * instead of "0 subscriptions, 34 settings and 0 recipes".
     */
    fun receipt(
        subscriptions: Int,
        settings: Int,
        recipes: Int,
        notes: Int = 0,
        history: Int = 0,
        progress: Int = 0,
    ): String {
        val parts = listOfNotNull(
            subscriptions.takeIf { it > 0 }?.let { plural(it.toLong(), "subscription") },
            settings.takeIf { it > 0 }?.let { plural(it.toLong(), "setting") },
            recipes.takeIf { it > 0 }?.let { plural(it.toLong(), "recipe") },
            notes.takeIf { it > 0 }?.let { plural(it.toLong(), "note") },
            history.takeIf { it > 0 }?.let { plural(it.toLong(), "play") },
            progress.takeIf { it > 0 }?.let { plural(it.toLong(), "resume point") },
        )
        return when (parts.size) {
            0 -> "Backup saved"
            1 -> "Saved ${parts.first()}"
            else -> "Saved " + parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    /**
     * The rows of [incoming] that [existing] does not already hold, under [key]. A restore is
     * additive and a second restore of the same file adds nothing, which is the only thing that
     * makes "merges, never wipes" true twice in a row.
     */
    fun <T> fresh(existing: Set<String>, incoming: List<T>, key: (T) -> String): List<T> {
        val seen = existing.toMutableSet()
        val added = ArrayList<T>(incoming.size)
        for (row in incoming) if (seen.add(key(row))) added += row
        return added
    }

    /** Two notes are the same note when they say the same words at the same second. */
    fun noteKey(note: NoteEntity): String =
        "${note.videoId}|${note.atSec}|${note.kind}|${note.text}"

    /** One play is another play when it is the same video opened at the same moment. */
    fun historyKey(row: HistoryEntity): String = "${row.videoId}|${row.openedAt}"
}
