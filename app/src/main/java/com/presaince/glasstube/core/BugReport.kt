/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.presaince.glasstube.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * The one thing a bug report needs: what the phone is, what the build is, and what the app was
 * doing. Adapted from oko's bundle idea, without its location and notification fields.
 *
 * Everything is redacted on the way out: a key or a token that reached a log line never reaches
 * the clipboard.
 */
object BugReport {

    private val SECRET = Regex(
        "(?i)bearer\\s+\\S+" +
            "|(?:api[_-]?key|token|password|passwd|secret)\\s*[:=]\\s*\\S+",
    )

    fun text(context: Context, log: AppLog, includeLogs: Boolean = true): String {
        val entries = log.recent().take(300)
        val lines = buildString {
            appendLine("GlassTube bug report")
            appendLine("version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android ${Build.VERSION.RELEASE} (api ${Build.VERSION.SDK_INT})")
            appendLine("device ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("package ${context.packageName}")
            appendLine("time ${java.util.Date()}")
            if (includeLogs) {
                appendLine()
                appendLine(settingsBlock(context))
                appendLine("log (${entries.size} entries, newest first, 48h)")
                entries.forEach { entry ->
                    appendLine("${entry.time()} ${entry.level.name.first()} ${entry.tag}: ${entry.message}")
                }
            } else {
                appendLine()
                appendLine("log: not included")
            }
        }
        return SECRET.replace(lines, "[redacted]")
    }

    /** JSON shape, for a file or a bug tracker that prefers structure. */
    fun json(context: Context, log: AppLog, includeLogs: Boolean = true): String {
        val entries = JSONArray()
        if (includeLogs) {
            log.recent().take(300).forEach { entry ->
                entries.put(
                    JSONObject()
                        .put("at", entry.at)
                        .put("level", entry.level.name)
                        .put("tag", entry.tag)
                        .put("message", SECRET.replace(entry.message, "[redacted]")),
                )
            }
        }
        val root = JSONObject()
            .put("app", "glasstube")
            .put("version", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("android", Build.VERSION.RELEASE)
            .put("api", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("entries", entries)
        if (includeLogs) root.put("settings", settingsJson(context))
        return root.toString(2)
    }

    /**
     * Every preference, redacted by key name. It rides with the log rather than being a separate
     * switch: a report with the log off is a note from a person, not a diagnostics bundle.
     */
    private fun settingsBlock(context: Context): String = buildString {
        val all = context.getSharedPreferences("glasstube", Context.MODE_PRIVATE).all
        appendLine("settings (${all.size} keys)")
        all.toSortedMap().forEach { (key, value) ->
            appendLine("$key = ${if (isSecretName(key)) "[redacted]" else value}")
        }
    }

    private fun settingsJson(context: Context): JSONObject = JSONObject().also { out ->
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE).all
            .toSortedMap()
            .forEach { (key, value) ->
                out.put(key, if (isSecretName(key)) "[redacted]" else value)
            }
    }

    private fun isSecretName(key: String): Boolean {
        val lower = key.lowercase()
        return "token" in lower || "secret" in lower || "password" in lower || "api_key" in lower
    }

    /**
     * The upload payload. The same facts the text carries, in a shape the drop box can validate:
     * a schema marker, the note the person typed, and the screenshot when they left it on.
     */
    fun bundle(
        context: Context,
        log: AppLog,
        note: String,
        includeShot: Boolean,
        includeLogs: Boolean = true,
    ): String {
        val root = JSONObject(json(context, log, includeLogs))
            .put("schema", "glasstube.bug/1")
            .put("package", context.packageName)
            .put("at", System.currentTimeMillis())
            .put("logsIncluded", includeLogs)
            .put("note", SECRET.replace(note.trim(), "[redacted]"))
        val shot = if (includeShot) BugShot.bitmap else null
        if (shot != null) {
            encodeShot(shot)?.let { root.put("shot", it) }
        }
        return root.toString()
    }

    /** Flat and sortable: one file per report, named so the newest sits last on an FTP listing. */
    fun uploadName(): String {
        val device = "${Build.MANUFACTURER}_${Build.MODEL}"
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "glasstube_${device}_${BuildConfig.VERSION_CODE}_$stamp.json"
    }

    /** Downscaled JPEG, because a full-resolution PNG is several megabytes of a log bundle. */
    private fun encodeShot(source: android.graphics.Bitmap): JSONObject? = runCatching {
        val maxDim = 1080
        val scale = (maxDim / maxOf(source.width, source.height).toFloat()).coerceAtMost(1f)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(source, width, height, true)
        } else {
            source
        }
        val bytes = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, bytes)
        if (scaled !== source) scaled.recycle()
        JSONObject()
            .put("width", width)
            .put("height", height)
            .put("jpegBase64", android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
    }.getOrNull()

    fun shareIntent(
        context: Context,
        log: AppLog,
        includeShot: Boolean,
        includeLogs: Boolean = true,
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GlassTube bug report")
            putExtra(Intent.EXTRA_TEXT, text(context, log, includeLogs))
        }

        val shot = BugShot.bitmap
        if (includeShot && shot != null) {
            val file = runCatching {
                val dir = java.io.File(context.cacheDir, "shots").apply { mkdirs() }
                val png = java.io.File(dir, "glasstube-shot.png")
                png.outputStream().use { stream ->
                    shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                }
                png
            }.getOrNull()

            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.files",
                    file,
                )
                send.type = "image/png"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(send, "Share bug report")
    }
}
