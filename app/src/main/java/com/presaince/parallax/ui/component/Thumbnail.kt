/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.data.model.StreamState

/**
 * A 16:9 thumbnail with the length in the corner, and a watch-progress line along the bottom when
 * the video is part-played. One shape everywhere: lists, cards, mini-player.
 */
@Composable
fun Thumbnail(
    url: String,
    durationSec: Long,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    state: StreamState = StreamState.NORMAL,
    /** How far the corner badges sit from the edge. Small pictures need a smaller inset. */
    badgePadding: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            // A soft field rather than a flat swatch while the picture is on its way: the list
            // reads as loading, not as a wall of grey boxes.
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (state != StreamState.NORMAL) {
            Text(
                text = if (state == StreamState.LIVE) "LIVE" else "UPCOMING",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(badgePadding)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (state == StreamState.LIVE) Color(0xFFD32F2F) else Color(0xCC000000),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        if (durationSec > 0 && state == StreamState.NORMAL) {
            Text(
                text = formatDuration(durationSec),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(badgePadding)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x66000000)),
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
}
