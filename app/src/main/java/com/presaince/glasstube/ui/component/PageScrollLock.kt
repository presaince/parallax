/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A page's own scroll, which the gesture lesson freezes while a finger is on its stage.
 *
 * Consuming the drag is not enough: an enclosing `verticalScroll` sees the event before the child
 * and starts scrolling anyway, so the page slid under the finger. Switching the scroll off for the
 * length of the press is the only version that holds.
 */
class PageScrollLock {
    var locked by mutableStateOf(false)
}

/** Provided by every page that can contain the lesson; defaults to an unused lock. */
val LocalPageScrollLock = compositionLocalOf { PageScrollLock() }
