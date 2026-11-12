/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.presaince.glasstube.R
import com.presaince.glasstube.core.LEVEL_BAND_TOP_DP

/**
 * How far a level travels per pixel of drag: the full range across half the picture's height.
 *
 * The rate is deliberately unchanged, because it is the part that felt right. What was missing was
 * somewhere to put the last few percent: a drag that began near the top of the picture ran out of
 * screen before it ran out of level. `core/LevelBand.kt` owns that answer — the band, and a magnet
 * at each end — and this factor is only the middle of the gesture.
 */
internal const val LevelTravel = 2f

/**
 * What the gesture HUD is saying: a time, a speed, or a level's percentage.
 *
 * Shared by the player and the gesture lesson. The lesson keeps its own wireframe picture, but the
 * moment a finger lands the readout is the player's, so what is learnt there is what the player
 * draws.
 */
data class Hud(
    val label: String? = null,
    /** A smaller line under the primary one, such as the position a scrub is heading for. */
    val secondary: String? = null,
    /** A level, volume or brightness, drawn as a pill above the finger. */
    val fraction: Float? = null,
    /** The level's mark, so the readout says which level it is. */
    val icon: Int? = null,
    /** Scrub progress through the video, 0..1, drawn as a bar inside the pill. */
    val progress: Float? = null,
    /** Finger position as a fraction of the picture. The readout follows it. */
    val anchor: Offset? = null,
    /** Speed readout: a vertical pill on the left edge at the finger's height. */
    val leftEdge: Boolean = false,
)

/**
 * The gesture readout. Volume and brightness get one bare percentage above the finger; a scrub
 * or a skip gets a wide pill clear above the finger that asked for it, or below when the
 * finger is too near the top, carrying the whole video's progress in its bottom edge. Speed
 * sticks to the left edge at the finger's height, out of the way of the picture.
 */
@Composable
fun HudPill(hud: Hud) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val level = hud.fraction
        val progress = hud.progress
        val anchorY = (hud.anchor?.y ?: 0.5f).coerceIn(0f, 1f)
        val anchorX = (hud.anchor?.x ?: 0.5f).coerceIn(0f, 1f)
        when {
            progress != null -> {
                val pillHeight = 48.dp
                val below = anchorY < 0.25f
                // 64dp of air, not 28: at 28 the bottom line sat half under the thumb, and the
                // pill's bottom edge carries the progress bar.
                val raw = if (below) {
                    maxHeight * anchorY + 56.dp
                } else {
                    maxHeight * anchorY - pillHeight - 64.dp
                }
                val y = raw.coerceIn(8.dp, (maxHeight - pillHeight - 8.dp).coerceAtLeast(8.dp))
                ScrubPill(
                    label = hud.label.orEmpty(),
                    secondary = hud.secondary,
                    progress = progress,
                    // The pill follows the finger instead of owning the middle of the picture
                    // (`111834`): the same placement rule the level readouts use, clamped inside.
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = readoutLeft(maxWidth * anchorX, maxWidth, 176.dp),
                            y = y,
                        ),
                )
            }
            level != null -> {
                // The seek pill's capsule, carrying the level's icon and its whole percent,
                // 64dp clear above the finger and travelling with it. Above is the safe side
                // for either hand: the finger's mass lies below the contact point, which is why
                // there is no handedness setting to consult.
                LevelReadout(
                    icon = hud.icon ?: R.drawable.ic_volume,
                    level = level,
                    modifier = Modifier.offset(
                        x = readoutLeft(maxWidth * anchorX, maxWidth),
                        y = readoutTop(
                            fingerY = maxHeight * anchorY,
                            pictureHeight = maxHeight,
                            bandTop = LEVEL_BAND_TOP_DP.dp,
                        ),
                    ),
                )
            }
            hud.leftEdge -> {
                val y = (maxHeight * anchorY - 24.dp)
                    .coerceIn(8.dp, (maxHeight - 56.dp).coerceAtLeast(8.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 12.dp, y = y)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xE6101014))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = hud.label.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            hud.label != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = hud.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xCC101014), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The scrub readout: the distance as the headline, the target underneath, progress inside. */
@Composable
fun ScrubPill(
    label: String,
    secondary: String?,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(176.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xE6101014)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(5.dp)
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
