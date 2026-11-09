/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One selected-chip look for the whole app: the accent, not Material's secondary container. The
 * fill stays light so a row of chips remains readable, and the label carries the colour. The label
 * is the accent's walkable text shade, so a bright accent still reads on its own 18% wash.
 */
@Composable
fun parallaxFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    selectedLabelColor = accentTextColour(),
    selectedLeadingIconColor = accentTextColour(),
)

/** The accent as text: the picked hue, walked until it clears the current contrast policy. */
@Composable
fun accentTextColour(): Color {
    val ink = LocalAccentInk.current
    return if (ink == Color.Unspecified) MaterialTheme.colorScheme.primary else ink
}
