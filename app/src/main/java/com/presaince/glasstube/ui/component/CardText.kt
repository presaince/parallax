/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.presaince.glasstube.ui.theme.accentTextColour

/**
 * The type scale for every card in the app. Five parts, one size each, so a card cannot disagree
 * with the card beside it: the player page had three section headers at 13sp and three at 15sp,
 * and the mini-player's title was a size under every other card's.
 *
 * Sizes come from `ui/theme/Theme.kt`, which lifts the Material defaults a step for a phone read
 * at arm's length. Nothing here sets a size of its own — it only chooses which of the five a part
 * of a card is, so changing the theme changes the cards with it.
 *
 * | Part   | Style        | Size |
 * |--------|--------------|------|
 * | header | `titleSmall` | 15sp |
 * | title  | `titleSmall` | 15sp |
 * | meta   | `bodySmall`  | 13sp |
 * | body   | `bodyMedium` | 15sp |
 * | action | `labelSmall` | 12sp |
 */
object CardText {
    /** The title on a section card: Description, Chapters, Comments, and the rest. */
    val header: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleSmall

    /** A header's ink. Every section is named in the accent, whatever the theme. */
    val headerInk: Color
        @Composable get() = accentTextColour()

    /** The name of the thing the card is about: a video, a channel, a recipe. */
    val title: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleSmall

    /** The facts under a title: channel, age, views, a count, an author's name. */
    val meta: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodySmall

    /** What the card actually says: a comment, a description line, a note. */
    val body: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyMedium

    /** A control's own words: Reply, Undo, "3 replies". */
    val action: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelSmall
}
