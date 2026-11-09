/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.presaince.parallax.data.source.readContentRegions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export: what a person would want to carry to a new install. No secrets — the Resync key
 * and the OAuth tokens stay on the device, because an export file is not a safe place for them.
 *
 * Only values this install actually stored are written. A fresh install's file says which groups
 * exist and nothing else, which is honest: there are no choices to carry yet.
 */
object SettingsExport {

    const val PREFS = "parallax"
    const val VERSION = 2

    fun json(context: Context): String = values(context).toString(2)

    /** The settings half of a backup, as one object. */
    fun values(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = JSONObject()
            .put("app", "parallax")
            .put("version", VERSION)

        SettingsSchema.groups.forEach { (group, fields) ->
            val out = JSONObject()
            fields.forEach { field ->
                value(prefs, context, field)?.let { out.put(field.json, it) }
            }
            // The one switch with a rule the table cannot express: it is restored only when a key
            // is already on the device, which import decides rather than the file.
            if (group == "resync") {
                out.put("enabled", prefs.getBoolean("resync_enabled", false))
            }
            root.put(group, out)
        }
        return root
    }

    /** How many values the object carries, for a receipt that can be checked against the file. */
    fun count(settings: JSONObject): Int = settings.keys().asSequence()
        .mapNotNull { settings.optJSONObject(it) }
        .sumOf { it.length() }

    private fun value(
        prefs: SharedPreferences,
        context: Context,
        field: PrefField,
    ): Any? = when (field.kind) {
        PrefKind.BOOL -> if (prefs.contains(field.pref)) prefs.getBoolean(field.pref, false) else null
        PrefKind.INT -> if (prefs.contains(field.pref)) prefs.getInt(field.pref, 0) else null
        PrefKind.FLOAT -> if (prefs.contains(field.pref)) prefs.getFloat(field.pref, 0f) else null
        PrefKind.STRING -> prefs.getString(field.pref, null)?.takeIf { it.isNotBlank() }
        PrefKind.STRING_SET -> prefs.getStringSet(field.pref, null)
            ?.takeIf { it.isNotEmpty() }
            ?.sorted()
            ?.let { JSONArray(it) }
        PrefKind.LINES -> prefs.getString(field.pref, null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { JSONArray(it) }
        PrefKind.REGIONS -> JSONArray(readContentRegions(context))
    }
}
