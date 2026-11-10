/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.presaince.glasstube.data.source.readContentRegions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export: what a person would want to carry to a new install. No secrets — the Resync key
 * and the OAuth tokens stay on the device, because an export file is not a safe place for them.
 *
 * Every scalar choice is written, including the ones this phone never touched: a backup is a
 * snapshot of the phone, not of the settings someone happened to move. Without the defaults a
 * restore could not reproduce a layout or a switch that was left alone, and the new phone's own
 * value (a wizard pick, say) survived instead.
 *
 * Lists stay additive: an empty search history is left out rather than clearing the terms on the
 * phone being restored into, the same rule notes and plays follow.
 */
object SettingsExport {

    const val PREFS = "glasstube"

    /** 3: defaults are carried, so a file restores the phone rather than the choices it stored. */
    const val VERSION = 3

    fun json(context: Context): String = values(context).toString(2)

    /** The settings half of a backup, as one object. */
    fun values(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = JSONObject()
            .put("app", "glasstube")
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
        PrefKind.BOOL -> prefs.getBoolean(field.pref, field.default as Boolean)
        PrefKind.INT -> prefs.getInt(field.pref, field.default as Int)
        PrefKind.FLOAT -> prefs.getFloat(field.pref, field.default as Float)
        PrefKind.STRING -> prefs.getString(field.pref, field.default as String)
        PrefKind.STRING_SET -> {
            val stored = prefs.getStringSet(field.pref, null)?.takeIf { it.isNotEmpty() }
            val values = stored ?: (field.default as Set<*>).filterIsInstance<String>().toSet()
            if (values.isEmpty()) null else JSONArray(values.sorted())
        }
        PrefKind.LINES -> prefs.getString(field.pref, null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { JSONArray(it) }
        PrefKind.REGIONS -> JSONArray(readContentRegions(context))
    }
}
