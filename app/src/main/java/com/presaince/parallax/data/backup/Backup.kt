/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import com.presaince.parallax.core.plural
import com.presaince.parallax.data.importer.ImportedSubscription
import com.presaince.parallax.data.importer.SubscriptionExporter
import org.json.JSONArray
import org.json.JSONObject

/**
 * One file that carries a phone: subscriptions, settings and the reader's own Bored recipes.
 *
 * The subscription half is the exact NewPipe-shaped object the exporter already writes, so a
 * restore goes back through the importer that is already tested instead of a second reader that
 * could drift from it. Settings keep their own object, and recipes keep their raw JSON, because a
 * recipe is data — reformatting one on the way out would be a second place to get the schema wrong.
 */
object Backup {

    const val KIND = "backup"
    const val VERSION = 1

    fun json(
        settings: JSONObject,
        subscriptions: List<ImportedSubscription>,
        recipes: List<String>,
    ): String = JSONObject()
        .put("app", "parallax")
        .put("kind", KIND)
        .put("version", VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("subscriptions", JSONObject(SubscriptionExporter.newPipeJson(subscriptions)))
        .put("settings", settings)
        .put("recipes", JSONArray(recipes))
        .toString(2)

    /**
     * The receipt under an export: what the file actually carries, counted from the same three
     * things that were written. Empty parts are left out, so a fresh install says "Saved 1 setting"
     * instead of "0 subscriptions, 1 setting and 0 recipes".
     */
    fun receipt(subscriptions: Int, settings: Int, recipes: Int): String {
        val parts = listOfNotNull(
            subscriptions.takeIf { it > 0 }?.let { plural(it.toLong(), "subscription") },
            settings.takeIf { it > 0 }?.let { plural(it.toLong(), "setting") },
            recipes.takeIf { it > 0 }?.let { plural(it.toLong(), "recipe") },
        )
        return when (parts.size) {
            0 -> "Backup saved"
            1 -> "Saved ${parts.first()}"
            else -> "Saved " + parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }
}
