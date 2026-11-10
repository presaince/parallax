/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * The app's own wordmark. It follows the accent, because GlassTube is a product with its own colour
 * choice. The Presaince wordmark on the About screen stays on the brand ramp: that one is the
 * company mark, not a preference.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    val ramp = listOf(
        MaterialTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "GlassTube",
        style = MaterialTheme.typography.titleLarge.copy(brush = Brush.linearGradient(ramp)),
        modifier = modifier,
    )
}
