/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

/**
 * The progress bar belongs to the controls, so it fades with them: the picture has no bar until a
 * tap asks for one. It also steps aside for the two things that carry the same information — the
 * seek pill during a picture scrub and the undo offer after a seek — so they never stack at the
 * bottom. A bar drag keeps it up even after the controls' timer has run out, because the finger is
 * on it.
 */
internal fun progressBarVisible(
    controlsVisible: Boolean,
    barDragging: Boolean,
    seekPillVisible: Boolean,
    undoVisible: Boolean,
): Boolean =
    (controlsVisible || barDragging) && !seekPillVisible && !undoVisible
