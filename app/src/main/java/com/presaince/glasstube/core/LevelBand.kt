/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

/**
 * The vertical strip of the picture a volume or brightness drag may act on.
 *
 * The old rule was "half the picture's height for the whole range", measured from where the drag
 * happened to start. A finger that started at 95% of the height had 5% of room left, so it could
 * raise a level by a tenth of what it was already at and no more — the top of the screen was a
 * dead end that the readout could not explain. Three numbers fix that:
 *
 * - the band stops below the top chrome, so the last 44dp is never a level surface;
 * - it stops 12dp above the bottom edge, so neither end needs the screen's very edge;
 * - the last 12dp at either end is a magnet: entering it means the end, whatever the level was.
 */
const val LEVEL_BAND_TOP_DP = 44f
const val LEVEL_BAND_BOTTOM_DP = 12f
const val LEVEL_MAGNET_DP = 12f

/** The band in pixels, for one picture of [heightPx]. */
data class LevelBand(val topPx: Float, val bottomPx: Float, val magnetPx: Float) {

    /** Whether a finger at [yPx] may move a level at all. */
    fun contains(yPx: Float): Boolean = yPx >= topPx && yPx <= bottomPx

    fun inTopZone(yPx: Float): Boolean = yPx <= topPx + magnetPx

    fun inBottomZone(yPx: Float): Boolean = yPx >= bottomPx - magnetPx
}

fun levelBandOf(
    heightPx: Float,
    topPx: Float = 0f,
    bottomInsetPx: Float = 0f,
    magnetPx: Float = 0f,
): LevelBand {
    val top = topPx.coerceIn(0f, heightPx)
    val bottom = (heightPx - bottomInsetPx).coerceAtLeast(top)
    // A magnet wider than half the band would make the middle of it an end. On a picture too
    // short for the full band the zones shrink with it instead of swallowing each other.
    val magnet = magnetPx.coerceIn(0f, (bottom - top) / 2f)
    return LevelBand(top, bottom, magnet)
}

/**
 * How much of a drag's travel this finger position is worth: 1 while the finger is on the band,
 * 0 while it is over the top chrome or below the picture's bottom inset. **A level that is
 * frozen up there is the honest answer**: there is no room to measure, so the level does not
 * creep by two points while the thumb grinds against the status bar.
 */
fun levelTravelFactor(yPx: Float, band: LevelBand): Float = if (band.contains(yPx)) 1f else 0f

/**
 * +1 or -1 when this drag has just asked for an end of the range, 0 when it has not.
 *
 * The magnet only arms for a drag that began outside the end zone. A drag that *starts* at 95% of
 * the height is already inside the top zone, and treating it as "the end" would jump the level to
 * full on the first pixel of movement. Starting there can only lower the level; starting anywhere
 * on the band can reach both ends, because the last 12dp of travel finishes the last 6%.
 */
fun levelEndReach(yPx: Float, startYPx: Float, band: LevelBand): Int = when {
    band.inTopZone(yPx) && !band.inTopZone(startYPx) -> 1
    band.inBottomZone(yPx) && !band.inBottomZone(startYPx) -> -1
    else -> 0
}
