/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player.gesture

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
 *
 * Horizontal has to *dominate*, not merely win: a swipe up with a millimetre of sideways drift
 * used to lock horizontal on its first frame past the slop and raise the seek pill, which is what
 * `222730` reported as "swiping up brings up seek pill". A finger has to travel [AXIS_BIAS] times
 * further sideways than vertically before the gesture is a seek.
 */
const val AXIS_BIAS = 1.5f

fun resolveAxis(@Suppress("UNUSED_PARAMETER") zone: Zone, dx: Float, dy: Float, slopPx: Float): Axis =
    when {
        abs(dx) < slopPx && abs(dy) < slopPx -> Axis.None
        abs(dx) > abs(dy) * AXIS_BIAS -> Axis.Horizontal
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
 * The zones that already own a press. The back arrow sits in the top-left corner, so a tap there
 * must not also toggle the controls behind it. Add to this list as the overlay grows.
 *
 * The bug button used to have the top-right corner. It lives in the page's action row now, which
 * is why there is one zone and not two.
 */
object SurfaceTapZones {
    val back = TapZone("back", left = 0f, top = 0f, right = 0.17f, bottom = 0.24f)

    fun defaults(): List<TapZone> = listOf(back)
}

/** -1 for the left half, +1 for the right half. */
fun seekDirection(x: Float, widthPx: Float): Int = if (x < widthPx / 2f) -1 else 1

/**
 * Whether a vertical drag belongs to the ladder rather than to the level under the finger.
 *
 * A flick either way is the ladder: down means one step out, up means fullscreen. Only the flick
 * hands off, because a slow drag up a side band is still "make it louder". Without the upward
 * case a fast swipe up from the left third changed the volume on its way to fullscreen, which is
 * the one thing a person swiping for fullscreen did not ask for.
 */
fun flickBelongsToLadder(zone: Zone, totalDy: Float, pxPerMs: Float, threshold: Float): Boolean {
    if (zone == Zone.Centre) return false
    if (abs(totalDy) <= 0.001f) return false
    return if (totalDy > 0f) pxPerMs > threshold else -pxPerMs > threshold
}
