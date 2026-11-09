/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.playback

/**
 * Where one play is in history: Fresh until its first frame plays (opened), Open until it has
 * been watched long enough, then Watched. One row per play lives in the database; this only
 * decides when the two moments are written.
 */
enum class HistoryPhase { Fresh, Open, Watched }

/** The two moments a play can write, in order. */
enum class HistoryWrite { Open, Watch }

/**
 * Advances the phase for one tick. `readyPlaying` is "past Ready and actually playing",
 * `watchedMs` is real playing time under that condition. Pure, so the rules are testable
 * without a player or a database.
 */
fun advanceHistory(
    phase: HistoryPhase,
    readyPlaying: Boolean,
    watchedMs: Long,
    thresholdMs: Long,
): Pair<HistoryPhase, HistoryWrite?> = when (phase) {
    HistoryPhase.Fresh ->
        if (readyPlaying) HistoryPhase.Open to HistoryWrite.Open else phase to null
    HistoryPhase.Open ->
        if (watchedMs >= thresholdMs) HistoryPhase.Watched to HistoryWrite.Watch else phase to null
    HistoryPhase.Watched -> phase to null
}
