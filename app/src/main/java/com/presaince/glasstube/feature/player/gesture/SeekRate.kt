/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

/**
 * The sideways drag's rate, shared by the player and the gesture lesson so the tutorial cannot
 * promise a speed the player does not have.
 *
 * The first tenth of the width moves the base (Settings → Playback, default 5 s). The rate climbs
 * to eight times that as the finger travels 40% of the width, which keeps a two-hour video a few
 * swipes away. Travel is passed before the current event, so the first tenth is exactly the base
 * rather than already ramped.
 */
internal object SeekRate {

    const val FULL_RAMP_TRAVEL = 0.40f
    const val MAX_RAMP = 8f

    fun ramp(travelBefore: Float): Float =
        1f + (travelBefore.coerceIn(0f, FULL_RAMP_TRAVEL) / FULL_RAMP_TRAVEL) * (MAX_RAMP - 1f)

    fun movedMs(fraction: Float, travelBefore: Float, baseSeconds: Float): Long =
        (fraction * 10f * baseSeconds * ramp(travelBefore) * 1000f).toLong()
}
