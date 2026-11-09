/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.data.db.ParallaxDb

/** Local watch counts per video, shared by every screen that renders a video row. */
@Composable
fun watchCounts(db: ParallaxDb): Map<String, Int> {
    val all by db.history().observeGrouped().collectAsStateWithLifecycle(initialValue = emptyList())
    return all.associate { it.videoId to it.watches }
}
