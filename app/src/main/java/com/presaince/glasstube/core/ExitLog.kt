/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import java.io.InputStreamReader

/**
 * Why the last process ended, asked of the OS instead of guessed from a gap in the log.
 *
 * Reports 163203 and 163241 are a "force close" with nothing in the app's log: the process died
 * without an exception reaching our code, which is what a native crash, an ANR and a low-memory
 * kill all look like from inside. Android remembers those, with a trace for the two that have one,
 * and this writes the newest unrecorded one as a single error line on the next start. So the next
 * such report can say what happened instead of "did we just force close crash?".
 *
 * One line per exit, never replayed: the timestamp of the last recorded exit is kept in prefs.
 */
class ExitLog(
    private val context: Context,
    private val log: AppLog,
    private val prefs: SharedPreferences,
) {

    fun record() {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = runCatching {
            // Newest first, a handful at most: the one that matters is the last one. Only this
            // app's own process counts; a worker in another process is a different story.
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 4)
        }.getOrNull().orEmpty()
        val last = exits.firstOrNull { it.processName == context.packageName } ?: return
        val previous = prefs.getLong(KEY_LAST_RECORDED, 0L)
        if (!isNewer(last.timestamp, previous)) return
        prefs.edit().putLong(KEY_LAST_RECORDED, last.timestamp).apply()

        val trace = if (last.reason in tracedReasons) readTrace(last) else null
        log.error(
            "exit",
            buildString {
                append("process ended: ")
                append(reasonName(last.reason))
                append(" status=")
                append(last.status)
                last.description?.takeIf { it.isNotBlank() }?.let {
                    append(" description=")
                    append(it)
                }
                append(" importance=")
                append(last.importance)
                append(" rss=")
                append(last.rss)
                append("KB")
                if (trace != null) {
                    append(" trace=")
                    append(trace)
                }
            },
        )
    }

    /** The trace a crash or an ANR leaves behind. Bounded: a native dump is hundreds of lines. */
    private fun readTrace(info: ApplicationExitInfo): String? = runCatching {
        info.traceInputStream?.use { stream ->
            InputStreamReader(stream).readLines()
                .filter { it.isNotBlank() }
                .take(TRACE_LINES)
                .joinToString(" | ")
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY_LAST_RECORDED = "exit_last_recorded"
        const val TRACE_LINES = 12

        /** Only these carry a trace worth reading; the rest would be an empty stream. */
        val tracedReasons = setOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
        )

        fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native-crash"
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory"
            ApplicationExitInfo.REASON_SIGNALED -> "signalled"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource-limit"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "init-failure"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "user-requested"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission-change"
            ApplicationExitInfo.REASON_FREEZER -> "frozen"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package-state-change"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package-updated"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency-died"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exit-self"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user-stopped"
            ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
            ApplicationExitInfo.REASON_OTHER -> "other"
            else -> "reason-$reason"
        }
    }
}

/**
 * Whether an exit at [recordedAt] has not been written yet. Its own function because it is the
 * rule that keeps one death to one line, and because a clock that stepped backwards must not make
 * an old exit look new.
 */
internal fun isNewer(recordedAt: Long, lastRecordedAt: Long): Boolean = recordedAt > lastRecordedAt
