/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

/**
 * The gesture lesson's ladder, with no Compose in it.
 *
 * The player has three stops on this stage — normal, mini, super-mini — and the lesson has to
 * teach the way down and the way back. Keeping the thresholds and the round-trip completion here
 * means the pointer loop cannot quietly count a drag that never reached the bar.
 */
internal object GestureLesson {

    const val NORMAL = 0
    const val MINI = 1
    const val BAR = 2

    /** The level after one vertical travel in the centre third. */
    fun nextLevel(level: Int, travelPx: Float, thresholdPx: Float): Int = when {
        travelPx > thresholdPx -> (level + 1).coerceAtMost(BAR)
        travelPx < -thresholdPx -> (level - 1).coerceAtLeast(NORMAL)
        else -> level
    }

    /** The fourth step is done only when the bar was reached and the card came back up. */
    fun isComplete(step: Int, level: Int, reachedBar: Boolean): Boolean =
        step == 3 && reachedBar && level == NORMAL
}
