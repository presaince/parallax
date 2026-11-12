/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The card a section header sits in, on the page under the picture (`114914`).
 *
 * One spec, shared, because the sections had four different containers: the body of My notes had
 * its own surface at 14dp and a stronger fill, comments had none, and Up next had a card *inside*
 * a card. Held apart, they drift — and the reader sees a ragged left edge rather than four
 * sections.
 *
 * The rule the page follows: the card is the header, the body flows under it on the page surface
 * at 24dp (8dp of margin plus 16dp of content). Comment rows are keyed lazy items, so a card
 * around all of them is not possible without either a seam between items or losing their keys.
 */
@Composable
fun Modifier.sectionCard(): Modifier = this
    .padding(horizontal = 8.dp, vertical = 4.dp)
    .clip(RoundedCornerShape(16.dp))
    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
