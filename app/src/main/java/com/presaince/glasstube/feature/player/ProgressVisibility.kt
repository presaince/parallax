/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

/**
 * The progress bar belongs to the controls, so it fades with them: the picture has no bar until a
 * tap asks for one. It steps aside for the one thing that carries the same information — the seek
 * pill during a picture scrub. A bar drag keeps it up even after the controls' timer has run out,
 * because the finger is on it.
 *
 * The undo offer used to hide the bar too, and that read as a fault: seeking made the bar vanish
 * for four seconds, which is the moment the reader most wants to see where the playhead is. The
 * chip sits above the bar instead, and the bar stays.
 */
internal fun progressBarVisible(
    controlsVisible: Boolean,
    barDragging: Boolean,
    seekPillVisible: Boolean,
): Boolean = (controlsVisible || barDragging) && !seekPillVisible
