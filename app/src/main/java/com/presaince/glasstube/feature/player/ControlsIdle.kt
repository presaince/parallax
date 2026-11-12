/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

/**
 * The popups the picture can have up, one at a time. They are anchored to the controls, so the
 * controls cannot leave while one is open: the anchor goes and Compose dismisses the menu with it,
 * which is what closed the CC list mid-pick (`113229`'s neighbour, reported 2026-12-11).
 *
 * A single value rather than a boolean per popup, because "two menus open" is not a state the
 * picture has, and every new probe of that state would have to be remembered in the idle rule.
 */
enum class PlayerOverlay { None, Speed, Quality, Captions }

/**
 * Whether the picture should put its controls away after [com.presaince.glasstube.feature.player.PlayerScreen]'s
 * timeout. A paused picture keeps them, because there is nothing to watch behind them. An open
 * popup keeps them, because the popup lives inside them.
 */
fun shouldHideControls(visible: Boolean, playing: Boolean, overlay: PlayerOverlay): Boolean =
    visible && playing && overlay == PlayerOverlay.None
