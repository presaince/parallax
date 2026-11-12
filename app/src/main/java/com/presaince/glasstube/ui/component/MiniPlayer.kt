/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.presaince.glasstube.R

/**
 * The playing video stays reachable when you leave the player screen: a small live surface above
 * the navigation bar, not a thumbnail. Two sizes on one ladder with the player screen:
 * minimized with the live surface, super minimized as a plain bar.
 * Swipe down steps one level toward super minimized, swipe up steps one level back.
 * Swipe up from minimized returns to the player screen.
 * A tap toggles play or pause only. The X stops.
 *
 * One surface is composed at a time: this card while the player screen is closed, that screen's own
 * surface while it is open. The player is shared, so playback never restarts.
 *
 * [level] is 0 (minimized with the live surface) or 1 (super minimized plain bar).
 */
@Composable
fun MiniPlayer(
    player: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    level: Int,
    onLevel: (Int) -> Unit,
) {
    // The vertical thresholds are in dp and converted once, so a swipe means the same on any
    // density without the gesture code carrying a Context.
    val dragPx = with(LocalDensity.current) { DRAG_DP.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // This card is part of the same stack as the top and bottom bars, so it wears the same
        // seam on its top edge. The bar below supplies the one on the other side.
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Down shrinks a step, up grows a step. The level is part of the key so the
                // end-of-drag lambda reads the current size.
                .pointerInput(dragPx, level) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { change, drag ->
                            change.consume()
                            dy += drag
                        },
                        onDragEnd = {
                            when {
                                dy > dragPx -> onLevel((level + 1).coerceAtMost(1))
                                // Swipe up steps back one level, and up from minimized
                                // returns to the player screen.
                                dy < -dragPx -> if (level > 0) onLevel(level - 1) else onExpand()
                            }
                        },
                    )
                }
                .clickable(onClick = onTogglePlay)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (level == 0) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { context ->
                            (android.view.LayoutInflater.from(context)
                                .inflate(com.presaince.glasstube.R.layout.view_player, null) as PlayerView)
                                .apply {
                                    this.player = player
                                    useController = false
                                }
                        },
                        modifier = Modifier.width(160.dp).height(90.dp),
                    )
                }
            }

            Text(
                text = title,
                style = CardText.title,
                maxLines = if (level == 0) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Pause is back, and it sits before the X rather than after it: stopping a video
            // is the destructive action, so it should never be the control nearest the thumb.
            // The card's outer tap target used to swallow this button; the row's z-order and
            // the button's own clickable keep the two apart.
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pause",
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop and close",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // A full-length, hairline progress line: the card should show where you are without a
        // second row of text.
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

private const val DRAG_DP = 48f
