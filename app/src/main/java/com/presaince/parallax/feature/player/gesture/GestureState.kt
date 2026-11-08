/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player.gesture

import kotlin.math.abs

enum class Zone { Edge, Left, Centre, Right }

enum class Axis { None, Vertical, Horizontal }

/**
 * Where a finger landed. The outer [edgePx] on each side is left to the system: that band is where
 * Android's back gesture and the notification shade start, and stealing it makes the phone feel
 * broken.
 */
fun zoneFor(x: Float, widthPx: Float, edgePx: Float): Zone = when {
    x < edgePx || x > widthPx - edgePx -> Zone.Edge
    x < widthPx / 3f -> Zone.Left
    x > widthPx * 2f / 3f -> Zone.Right
    else -> Zone.Centre
}

/**
 * Axis locks once total movement passes the touch slop and stays locked for the rest of the
 * gesture. Vertical means something in every zone: volume on the left, brightness on the right,
 * and the minimise drag in the middle. A vertical drag in the centre used to resolve to `None`,
 * which is why the minimise gesture never actually started.
 */
fun resolveAxis(@Suppress("UNUSED_PARAMETER") zone: Zone, dx: Float, dy: Float, slopPx: Float): Axis =
    when {
        abs(dx) < slopPx && abs(dy) < slopPx -> Axis.None
        abs(dx) > abs(dy) -> Axis.Horizontal
        else -> Axis.Vertical
    }

/**
 * A dead region on the video surface, in fractions of its width and height. Named so more can be
 * added as controls appear; a tap inside any of them is not a picture tap.
 */
data class TapZone(
    val name: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/**
 * The zones that already own a press. The back arrow and the bug button sit in these corners, so a
 * tap there must not also toggle the controls behind them. Add to this list as the overlay grows.
 */
object SurfaceTapZones {
    val back = TapZone("back", left = 0f, top = 0f, right = 0.17f, bottom = 0.24f)
    val bug = TapZone("bug", left = 0.83f, top = 0f, right = 1f, bottom = 0.24f)

    fun defaults(): List<TapZone> = listOf(back, bug)
}

/** -1 for the left half, +1 for the right half. */
fun seekDirection(x: Float, widthPx: Float): Int = if (x < widthPx / 2f) -1 else 1
