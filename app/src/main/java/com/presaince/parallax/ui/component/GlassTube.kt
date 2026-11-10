/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** The mark's own canvas: a 22 x 54 capsule, the geometry fixed in the GlassTube SVG. */
private const val TubeWidth = 22f
private const val TubeHeight = 54f

/** The play hole, on its centroid: apex (61.3, 54) with the base 11 x 13.8, shifted to 0,0. */
private val PlayHole = listOf(
    Offset(7.3f, 20.1f),
    Offset(18.3f, 27f),
    Offset(7.3f, 33.9f),
)

/**
 * GlassTube, the mark: a glass capsule with the play punched out of it, so the picture behind is
 * the glyph.
 *
 * The geometry and the layer order are copied from `assets/glasstube/glasstube-icon.svg`; the
 * colours are the brand's, not the theme's, because this is the company mark. The aurora that sits
 * behind it on the launcher tile is left to whatever the mark is drawn on, which on the player is
 * the video itself.
 *
 * Sized by height; the width follows the mark's ratio. It is decoration, so it has no semantics of
 * its own.
 */
@Composable
fun GlassTube(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(TubeWidth / TubeHeight)) {
        val unit = size.height / TubeHeight
        // A 1.2-unit rim is under half a dp at handle size; the floor keeps the edge legible.
        val rim = maxOf(1.2f, 1.dp.toPx() / unit)
        translate(left = (size.width - TubeWidth * unit) / 2f) {
            scale(scale = unit, pivot = Offset.Zero) {
                // Body with the play as a hole in the same path, so nothing is painted there.
                val body = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, TubeWidth, TubeHeight),
                            cornerRadius = CornerRadius(11f, 11f),
                        ),
                    )
                    moveTo(PlayHole[0].x, PlayHole[0].y)
                    lineTo(PlayHole[1].x, PlayHole[1].y)
                    lineTo(PlayHole[2].x, PlayHole[2].y)
                    close()
                }
                drawPath(
                    path = body,
                    brush = Brush.verticalGradient(
                        0f to Color(0xFFA5E4F5).copy(alpha = 0.55f),
                        1f to Color(0xFF4CC2E8).copy(alpha = 0.30f),
                        startY = 0f,
                        endY = TubeHeight,
                    ),
                )
                // The light runs down the left edge, as it does in the mark.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.55f),
                        1f to Color.White.copy(alpha = 0.06f),
                        startY = 3f,
                        endY = 49f,
                    ),
                    topLeft = Offset(3f, 3f),
                    size = Size(3.4f, 46f),
                    cornerRadius = CornerRadius(1.7f, 1.7f),
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color(0xFFF8FAFC).copy(alpha = 0.62f),
                        0.55f to Color(0xFFA5E4F5).copy(alpha = 0.22f),
                        1f to Color.White.copy(alpha = 0.12f),
                        start = Offset(0.6f, 0.6f),
                        end = Offset(21.4f, 53.4f),
                    ),
                    topLeft = Offset(0.6f, 0.6f),
                    size = Size(20.8f, 52.8f),
                    cornerRadius = CornerRadius(10.4f, 10.4f),
                    style = Stroke(width = rim),
                )
            }
        }
    }
}
