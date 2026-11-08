/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.R
import com.presaince.parallax.data.db.ParallaxDb

/** Voice-note counts per video, shared by every screen that renders a video row. */
@Composable
fun noteCounts(db: ParallaxDb): Map<String, Int> {
    val all by db.notes().counts("deck").collectAsStateWithLifecycle(initialValue = emptyList())
    return all.associate { it.videoId to it.n }
}

/**
 * The voice-note count on a card: mic and number on a dark pill, top corner of the thumbnail,
 * away from the watermark/timestamp corner. Absent when the video has no notes.
 */
@Composable
fun NoteCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC000000))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
