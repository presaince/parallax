/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.content.Context
import android.view.WindowManager
import kotlin.math.min

/**
 * The short side of the largest window this display can give, in pixels. A landscape video fills
 * the long side with picture and the short side with lines, so this is the most detail the screen
 * can show. Zero when the display cannot be measured, which callers read as "no cap".
 */
fun panelShortSidePx(context: Context?): Int {
    if (context == null) return 0
    return runCatching {
        val manager = context.getSystemService(WindowManager::class.java)
            ?: return@runCatching 0
        val bounds = manager.maximumWindowMetrics.bounds
        min(bounds.width(), bounds.height())
    }.getOrDefault(0)
}
