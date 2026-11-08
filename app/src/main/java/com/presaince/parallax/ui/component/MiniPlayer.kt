/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.presaince.parallax.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The playing video stays reachable when you leave the player screen: a small live surface above
 * the navigation bar, not a thumbnail. Three sizes, one gesture: full, swipe down to mini, swipe
 * down again to super-min; swipe up to step back, swipe up again to full. Tap opens the full player;
 * the X stops. Nothing hides the video entirely except the X.
 *
 * One surface is composed at a time: this card while the player screen is closed, that screen's own
 * surface while it is open. The player is shared, so playback never restarts.
 *
 * [level] is 0 (full), 1 (mini strip) or 2 (super-min hairline).
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
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    // The vertical thresholds are in dp and converted once, so a swipe means the same on any
    // density without the gesture code carrying a Context.
    val dragPx = with(LocalDensity.current) { DRAG_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                // Super-minimised is translucent: the content underneath shows through, with a
                // hairline above the navigation bar marking the edge.
                .background(
                    if (level >= 2) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
        ) {
            if (level >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // One axis, three sizes: down shrinks a step, up grows a step. Nothing else
                    // owns the vertical direction here.
                    .pointerInput(widthPx, level) {
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dy = 0f },
                            onDrag = { change, drag ->
                                change.consume()
                                dy += drag.y
                            },
                            onDragEnd = {
                                when {
                                    dy > dragPx -> onLevel((level + 1).coerceAtMost(2))
                                    dy < -dragPx -> onLevel((level - 1).coerceAtLeast(0))
                                }
                            },
                            onDragCancel = { },
                        )
                    }
                    .clickable(onClick = onExpand)
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
                                    .inflate(com.presaince.parallax.R.layout.view_player, null) as PlayerView)
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
                    style = MaterialTheme.typography.bodySmall,
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
                if (level >= 2) {
                    // Super-minimised: the X is a restore, not a stop. Stopping lives only on the
                    // larger states, so a waved-away strip never destroys playback with one tap.
                    IconButton(onClick = { onLevel(1) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Restore the player card",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop and close",
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
}

private const val DRAG_DP = 48f
