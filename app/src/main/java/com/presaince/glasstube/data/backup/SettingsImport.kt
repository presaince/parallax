/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import android.content.Context
import com.presaince.glasstube.core.plural
import com.presaince.glasstube.data.source.writeContentRegions
import org.json.JSONArray
import org.json.JSONObject

/** What a restore did, in the reader's words rather than the file's. */
data class RestoreSummary(
    val settings: Int,
    val notes: List<String> = emptyList(),
) {
    val message: String
        get() = if (settings == 0) {
            "That file carries no settings this install can use"
        } else {
            buildString {
                append("Restored ")
                append(plural(settings.toLong(), "setting"))
                notes.forEach { note ->
                    append(". ")
                    append(note)
                }
            }
        }
}

/**
 * The other half of [SettingsExport]: write back exactly the fields the file names.
 *
 * Additive, never a wipe. A missing group changes nothing, a key the table does not know is never
 * written, and the AI switch is the one field with a rule of its own — it comes back on only when a
 * key is already on this device, because an enabled switch with no key is a dead control.
 *
 * Callers reload the [com.presaince.glasstube.ui.theme.ThemeController] afterwards; these writes go
 * under it rather than through it.
 */
object SettingsImport {

    const val PREFS = SettingsExport.PREFS

    fun apply(context: Context, settings: JSONObject, apiKeyPresent: Boolean): RestoreSummary {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var written = 0
        val notes = mutableListOf<String>()
        var regions: List<String>? = null

        SettingsSchema.groups.forEach groups@ { (group, fields) ->
            val from = settings.optJSONObject(group) ?: return@groups
            for (field in fields) {
                if (!from.has(field.json)) continue
                when (field.kind) {
                    PrefKind.BOOL -> {
                        edit.putBoolean(field.pref, from.optBoolean(field.json))
                        written++
                    }
                    PrefKind.INT -> {
                        edit.putInt(field.pref, from.optInt(field.json))
                        written++
                    }
                    PrefKind.FLOAT -> {
                        edit.putFloat(field.pref, from.optDouble(field.json).toFloat())
                        written++
                    }
                    PrefKind.STRING -> {
                        val text = from.opt(field.json) as? String ?: continue
                        if (text.isBlank()) continue
                        edit.putString(field.pref, text)
                        written++
                    }
                    PrefKind.STRING_SET -> {
                        val array = from.optJSONArray(field.json) ?: continue
                        edit.putStringSet(field.pref, array.strings().toMutableSet())
                        written++
                    }
                    PrefKind.LINES -> {
                        val array = from.optJSONArray(field.json) ?: continue
                        edit.putString(field.pref, array.strings().joinToString("\n"))
                        written++
                    }
                    PrefKind.REGIONS -> {
                        val array = from.optJSONArray(field.json) ?: continue
                        regions = array.strings()
                        written++
                    }
                }
            }
        }

        settings.optJSONObject("resync")?.let { resync ->
            if (resync.has("enabled")) {
                val wants = resync.optBoolean("enabled")
                edit.putBoolean("resync_enabled", wants && apiKeyPresent)
                written++
                if (wants && !apiKeyPresent) {
                    notes += "AI is off until you add your key"
                }
            }
        }

        edit.apply()
        // After the batch: this one validates the codes and keeps the legacy single-region key
        // in step, which a raw write cannot do.
        regions?.let { writeContentRegions(context, it) }
        return RestoreSummary(written, notes)
    }
}

private fun JSONArray.strings(): List<String> =
    (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
