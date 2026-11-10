/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The level readout in the seek pill's language: the same dark capsule, the level's icon and its
 * whole percent, and the same hairline of progress along the bottom. It sits above the finger and
 * travels with it.
 */
@Composable
fun LevelReadout(icon: Int, level: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(ReadoutWidth)
            .height(ReadoutHeight)
            .clip(RoundedCornerShape(ReadoutHeight / 2))
            .background(Color(0xE6101014)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = levelPercent(level),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        // The same progress edge the seek pill carries, so the level reads as a distance as well
        // as a number.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

val ReadoutWidth: Dp = 112.dp
val ReadoutHeight: Dp = 56.dp

/** Air between the finger and the readout's near edge, and air from the picture's own edges. */
private val AboveGap = 24.dp
private val BelowGap = 56.dp
private val Edge = 8.dp

/**
 * The readout's top edge for a finger at [fingerY]: clear above it, or below it when the finger
 * is too near the top of the picture for that. Either way it stays inside [pictureHeight].
 */
fun readoutTop(
    fingerY: Dp,
    pictureHeight: Dp,
    pillHeight: Dp = ReadoutHeight,
): Dp {
    val above = fingerY - pillHeight - AboveGap
    val raw = if (above < Edge) fingerY + BelowGap else above
    val lastTop = (pictureHeight - pillHeight - Edge).coerceAtLeast(Edge)
    return raw.coerceIn(Edge, lastTop)
}

/** The readout's left edge for a finger at [fingerX]: centred on it, inside the picture. */
fun readoutLeft(
    fingerX: Dp,
    pictureWidth: Dp,
    pillWidth: Dp = ReadoutWidth,
): Dp {
    val lastLeft = (pictureWidth - pillWidth).coerceAtLeast(0.dp)
    return (fingerX - pillWidth / 2).coerceIn(0.dp, lastLeft)
}
