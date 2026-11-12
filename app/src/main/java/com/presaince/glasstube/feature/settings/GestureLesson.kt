/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import com.presaince.glasstube.feature.player.gesture.LadderStep

/**
 * The gesture lesson's ladder, with no Compose in it.
 *
 * The player has three stops on this stage — normal, mini, super-mini — and the lesson has to
 * teach the way down and the way back. Which drag counts as a step is the player's own rule
 * (`ladderStep`); what is left here is the stop it lands on, and when the lesson is finished.
 */
internal object GestureLesson {

    const val NORMAL = 0
    const val MINI = 1
    const val BAR = 2

    /** The stop one step in [step]'s direction, clamped at both ends. */
    fun stepped(level: Int, step: LadderStep): Int = when (step) {
        LadderStep.Down -> (level + 1).coerceAtMost(BAR)
        LadderStep.Up -> (level - 1).coerceAtLeast(NORMAL)
        LadderStep.None -> level
    }

    /** The fourth step is done only when the bar was reached and the card came back up. */
    fun isComplete(step: Int, level: Int, reachedBar: Boolean): Boolean =
        step == 3 && reachedBar && level == NORMAL
}
