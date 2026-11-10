/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.importer

import org.json.JSONArray
import org.json.JSONObject

data class ImportedSubscription(val url: String, val name: String)

sealed interface ImportResult {
    data class Ok(
        val format: String,
        val subscriptions: List<ImportedSubscription>,
        /** Entries the file held but that name no usable URL. A 39-entry file landing 38 rows. */
        val skipped: Int = 0,
    ) : ImportResult

    data class Err(val message: String) : ImportResult
}

/**
 * Subscription import. Every app in this space already exports its own list, so the honest move is
 * to read their formats rather than ask the user to rebuild a list by hand.
 *
 * Supported: NewPipe `subscriptions.json`, LibreTube JSON, Google Takeout `subscriptions.csv`.
 * A NewPipe `.zip` backup is not handled yet.
 */
object SubscriptionImporter {

    fun parse(content: String): ImportResult {
        val text = content.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        return when {
            text.startsWith("{") || text.startsWith("[") -> parseJson(text)
            text.lineSequence().firstOrNull()?.contains("Channel Id") == true -> parseTakeout(text)
            else -> ImportResult.Err("Unrecognised file: no JSON and no Takeout header")
        }
    }

    private fun parseJson(text: String): ImportResult = runCatching {
        val root = if (text.startsWith("[")) {
            JSONObject().put("subscriptions", JSONArray(text))
        } else {
            JSONObject(text)
        }
        val array = root.optJSONArray("subscriptions")
            ?: return ImportResult.Err("JSON has no subscriptions array")

        var skipped = 0
        val items = buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: run {
                    skipped++
                    continue
                }
                val url = entry.optString("url").ifBlank {
                    entry.optString("channelUrl").ifBlank {
                        entry.optString("id").takeIf { it.contains("/") }.orEmpty()
                    }
                }
                if (url.isBlank()) {
                    skipped++
                    continue
                }
                add(ImportedSubscription(url = url, name = entry.optString("name").ifBlank { url }))
            }
        }.distinctBy { it.url }

        if (items.isEmpty()) {
            ImportResult.Err("No subscriptions found in the file")
        } else {
            ImportResult.Ok(format = "JSON", subscriptions = items, skipped = skipped)
        }
    }.getOrElse { ImportResult.Err(it.message ?: "Malformed JSON") }

    private fun parseTakeout(text: String): ImportResult {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return ImportResult.Err("Takeout CSV has no rows")

        // Takeout CSV: Channel Id,Channel Url,Channel Title
        var skipped = 0
        val items = buildList {
            for (line in lines.drop(1)) {
                val cells = splitCsv(line)
                val url = cells.getOrNull(1)?.trim().orEmpty()
                if (!url.startsWith("http")) {
                    skipped++
                    continue
                }
                val name = cells.getOrNull(2)?.trim().orEmpty().ifBlank { url }
                add(ImportedSubscription(url = url, name = name))
            }
        }.distinctBy { it.url }

        return if (items.isEmpty()) {
            ImportResult.Err("No channels found in the CSV")
        } else {
            ImportResult.Ok(format = "Takeout CSV", subscriptions = items, skipped = skipped)
        }
    }

    /** Just enough CSV for Takeout: quoted cells, doubled quotes, no embedded newlines. */
    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells.add(cell.toString())
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        cells.add(cell.toString())
        return cells
    }
}
