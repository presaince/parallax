/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.player.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Pixels per millisecond. About 450 dp/s: a deliberate flick, not a slow drag. */
private const val FLING_PX_PER_MS = 1.0f

/** Height of the progress bar's touch band, measured up from the bottom of the picture. */
private const val BAR_BAND_DP = 44f

/**
 * One pointer stream, one state machine. Compose gives every pointer event to this loop, so the
 * axis decision is explicit instead of each detector fighting the others. There is no double tap
 * and no long press: a tap is a tap, seeking is a slide, and the speed has its own control.
 *
 * Deltas are normalised: vertical as a fraction of height (positive = down), horizontal as a
 * fraction of width.
 */
fun Modifier.playbackGestures(
    enabled: Boolean,
    onVerticalStart: (Zone, Offset) -> Unit,
    /** Zone, the level's fraction of travel, and where the finger is now. */
    onVerticalDelta: (Zone, Float, Offset) -> Unit,
    onVerticalEnd: () -> Unit,
    onHorizontalStart: (Float) -> Unit,
    onHorizontalDelta: (Float, Float) -> Unit,
    onHorizontalEnd: () -> Unit,
    onHorizontalCancel: () -> Unit = {},
    onBarDrag: (Float) -> Unit = {},
    onBarEnd: () -> Unit = {},
    tapZones: List<TapZone> = SurfaceTapZones.defaults(),
    onTap: (Offset) -> Unit,
): Modifier = pointerInput(enabled, tapZones) {
    if (!enabled) return@pointerInput
    detectPlaybackGestures(
        onVerticalStart = onVerticalStart,
        onVerticalDelta = onVerticalDelta,
        onVerticalEnd = onVerticalEnd,
        onHorizontalStart = onHorizontalStart,
        onHorizontalDelta = onHorizontalDelta,
        onHorizontalEnd = onHorizontalEnd,
        onHorizontalCancel = onHorizontalCancel,
        onBarDrag = onBarDrag,
        onBarEnd = onBarEnd,
        tapZones = tapZones,
        onTap = onTap,
    )
}

private suspend fun PointerInputScope.detectPlaybackGestures(
    onVerticalStart: (Zone, Offset) -> Unit,
    onVerticalDelta: (Zone, Float, Offset) -> Unit,
    onVerticalEnd: () -> Unit,
    onHorizontalStart: (Float) -> Unit,
    onHorizontalDelta: (Float, Float) -> Unit,
    onHorizontalEnd: () -> Unit,
    onHorizontalCancel: () -> Unit,
    onBarDrag: (Float) -> Unit,
    onBarEnd: () -> Unit,
    tapZones: List<TapZone>,
    onTap: (Offset) -> Unit,
) {
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val zone = zoneFor(down.position.x, width, 24.dp.toPx())
        // The progress bar: a band along the bottom edge. A sideways slide here puts the playhead
        // under the finger directly, with no pill; a tap still reaches the controls.
        val inBar = down.position.y >= height - BAR_BAND_DP.dp.toPx()
        var barDragging = false

        var axis = Axis.None
        // A fast downward flick minimises even when it starts on a volume or brightness band.
        var handsOff = false
        var totalX = 0f
        var totalY = 0f
        var lastX = down.position.x
        var lastY = down.position.y

        // The seek gesture lives in the picture, not below it. A swipe that starts in the bottom
        // band, or leaves the video, is not a seek: it is a tap or an abandoned gesture.
        val canSeek = down.position.y <= height * 0.80f

        fun downSpeed(now: Long): Float {
            val elapsed = (now - down.uptimeMillis).coerceAtLeast(1L)
            return totalY / elapsed
        }

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                if (barDragging) onBarEnd() else when (axis) {
                    Axis.Vertical -> onVerticalEnd()
                    Axis.Horizontal -> onHorizontalEnd()
                    Axis.None -> {
                        // A child that handled the press (a button, the speed panel) owns the tap.
                        if (change.isConsumed) break
                        // Fractions, so the caller can place a control without knowing sizes.
                        val fx = (change.position.x / width).coerceIn(0f, 1f)
                        val fy = (change.position.y / height).coerceIn(0f, 1f)
                        // The corners belong to the back arrow and the bug button. A tap there is
                        // not a picture tap, so it cannot toggle the controls behind them.
                        if (tapZones.none { it.contains(fx, fy) }) {
                            onTap(Offset(fx, fy))
                        }
                    }
                }
                break
            }

            // A finger that leaves the video abandons a seek, but the vertical gestures must
            // survive it: the minimise drag moves the surface with the finger, which used to
            // read as "left the video" and froze the gesture a few percent in.
            val outside = change.position.x < 0f || change.position.x > width ||
                change.position.y < 0f || change.position.y > height
            if (outside && (barDragging || axis == Axis.Horizontal)) {
                if (barDragging) onBarEnd() else onHorizontalCancel()
                break
            }

            val dx = change.position.x - lastX
            val dy = change.position.y - lastY
            lastX = change.position.x
            lastY = change.position.y
            totalX += dx
            totalY += dy

            // A child that owns this drag (the speed control) has consumed it. The picture must
            // not read the same movement as a brightness swipe.
            if (change.isConsumed) continue

            if (barDragging) {
                onBarDrag((change.position.x / width).coerceIn(0f, 1f))
                continue
            }

            if (axis == Axis.None) {
                val moved = kotlin.math.abs(totalX) > slop || kotlin.math.abs(totalY) > slop
                if (moved && inBar) {
                    if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                        barDragging = true
                        onBarDrag((change.position.x / width).coerceIn(0f, 1f))
                    }
                } else if (moved) {
                    // The bottom band cannot seek, but it can still minimise: a downward drag
                    // anywhere is the way out, not only in the top four fifths.
                    axis = resolveAxis(zone, totalX, totalY, slop)
                    if (axis == Axis.Horizontal && !canSeek) axis = Axis.None
                    when (axis) {
                        Axis.Vertical -> {
                            handsOff = flickBelongsToLadder(
                                zone = zone,
                                totalDy = totalY,
                                pxPerMs = downSpeed(change.uptimeMillis),
                                threshold = FLING_PX_PER_MS,
                            )
                            onVerticalStart(
                                if (handsOff) Zone.Centre else zone,
                                Offset(
                                    down.position.x / width,
                                    down.position.y / height,
                                ),
                            )
                        }
                        Axis.Horizontal -> onHorizontalStart(
                            (change.position.y / height).coerceIn(0f, 1f),
                        )
                        Axis.None -> Unit
                    }
                }
            } else {
                // A side band stops owning the gesture the moment it turns into a flick, either
                // way: a fast swipe up is how you ask for fullscreen, and it must not leave the
                // volume somewhere new on the way there.
                if (axis == Axis.Vertical && !handsOff &&
                    flickBelongsToLadder(
                        zone = zone,
                        totalDy = totalY,
                        pxPerMs = downSpeed(change.uptimeMillis),
                        threshold = FLING_PX_PER_MS,
                    )
                ) {
                    handsOff = true
                    onVerticalStart(
                        Zone.Centre,
                        Offset(
                            down.position.x / width,
                            down.position.y / height,
                        ),
                    )
                }
                when (axis) {
                    Axis.Vertical -> {
                        // A side drag follows the finger across the middle, so volume and
                        // brightness can be swapped without lifting. A drag that started in the
                        // centre stays on the ladder; it does not turn into a level control.
                        val active = when {
                            handsOff || zone == Zone.Centre -> Zone.Centre
                            change.position.x < width / 2f -> Zone.Left
                            else -> Zone.Right
                        }
                        onVerticalDelta(
                            active,
                            -dy / height,
                            Offset(
                                (change.position.x / width).coerceIn(0f, 1f),
                                (change.position.y / height).coerceIn(0f, 1f),
                            ),
                        )
                    }
                    Axis.Horizontal -> onHorizontalDelta(
                        dx / width,
                        (change.position.y / height).coerceIn(0f, 1f),
                    )
                    Axis.None -> Unit
                }
            }
        }
    }
}
