/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small persistent log, readable from Settings. It exists because "it sometimes doesn't work"
 * is not a bug report: the app should be able to say what it was doing when it failed.
 *
 * Plain text, one line per entry, kept for 48 hours and capped so it cannot grow without bound.
 * Levels are deliberately few: info for the shape of what happened, warn for "it recovered",
 * error for "it did not".
 */
class AppLog(private val dir: File) {

    enum class Level { INFO, WARN, ERROR }

    data class Entry(val at: Long, val level: Level, val tag: String, val message: String) {
        fun time(): String = FORMAT.format(Date(at))
    }

    private val lock = Any()
    private val file = File(dir, "glasstube.log")

    fun info(tag: String, message: String) = append(Level.INFO, tag, message)

    fun warn(tag: String, message: String) = append(Level.WARN, tag, message)

    fun error(tag: String, message: String) = append(Level.ERROR, tag, message)

    /** Newest first, only the last 48 hours. */
    fun recent(now: Long = System.currentTimeMillis()): List<Entry> = synchronized(lock) {
        runCatching {
            if (!file.exists()) return emptyList()
            val cutoff = now - RETAIN_MS
            file.readLines()
                .mapNotNull { parse(it) }
                .filter { it.at >= cutoff }
                .takeLast(MAX_LINES)
                .asReversed()
        }.getOrDefault(emptyList())
    }

    fun clear() = synchronized(lock) {
        runCatching { file.writeText("") }
    }

    private fun append(level: Level, tag: String, message: String) {
        val line = "${System.currentTimeMillis()}\t${level.name}\t$tag\t" +
            message.replace('\n', ' ') + "\n"
        synchronized(lock) {
            runCatching {
                file.appendText(line)
                if (file.length() > MAX_BYTES) prune()
            }
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - RETAIN_MS
        val kept = file.readLines().filter { line ->
            val entry = parse(line)
            entry != null && entry.at >= cutoff
        }.takeLast(MAX_LINES / 2)
        file.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun parse(line: String): Entry? {
        val parts = line.split('\t', limit = 4)
        if (parts.size < 4) return null
        val at = parts[0].toLongOrNull() ?: return null
        // Older rows carried no level; treat them as info rather than dropping them.
        val level = Level.entries.firstOrNull { it.name == parts[1] } ?: Level.INFO
        return Entry(at = at, level = level, tag = parts[2], message = parts[3])
    }

    private companion object {
        const val RETAIN_MS = 48L * 60 * 60 * 1000
        const val MAX_BYTES = 256 * 1024
        const val MAX_LINES = 1000
        val FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
