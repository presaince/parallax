/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

/**
 * The one step a centre drag asked for on release.
 *
 * The player and the gesture lesson both decide this here. A lesson that steps on a slow short
 * drag teaches a gesture the player ignores, which is the whole failure this replaces.
 */
enum class LadderStep { None, Down, Up }

/** Finger travel, as a fraction of the picture's height, that a slow drag needs to count. */
const val LADDER_TRAVEL = 0.22f

/** Finger travel that counts when the drag is fast: a flick does not need the distance. */
const val LADDER_FLICK_TRAVEL = 0.03f

/** A drag shorter than this is a flick. */
const val LADDER_FLICK_MS = 260L

/**
 * Whether a centre drag steps the ladder. Down is the way out, up is the way back; the caller
 * decides whether an up step has anywhere to go.
 */
fun ladderStep(down: Float, up: Float, elapsedMs: Long): LadderStep {
    val flick = elapsedMs < LADDER_FLICK_MS
    val flungDown = flick && down > LADDER_FLICK_TRAVEL
    val flungUp = flick && up > LADDER_FLICK_TRAVEL
    return when {
        down > LADDER_TRAVEL || flungDown -> LadderStep.Down
        up > LADDER_TRAVEL || flungUp -> LadderStep.Up
        else -> LadderStep.None
    }
}
