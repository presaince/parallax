/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.presaince.glasstube.R
import com.presaince.glasstube.data.source.ContentRegion
import com.presaince.glasstube.data.source.SYSTEM_REGION
import com.presaince.glasstube.ui.theme.accentTextColour
import com.presaince.glasstube.ui.theme.faded

/**
 * The countries as a flag grid. Shared by Settings and the wizard so a region looks and behaves
 * the same in both: three cards across, a tap selects or removes, the first pick is the main one.
 *
 * System is not a country and so is not a flag: it gets its own full-width row above the grid,
 * because it is a different kind of answer — ask as the phone, send no country at all.
 */
@Composable
fun RegionGrid(
    regions: List<ContentRegion>,
    chosen: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        regions.firstOrNull { it.code == SYSTEM_REGION }?.let { region ->
            SystemRow(
                selected = region.code in chosen,
                main = chosen.firstOrNull() == region.code,
                onClick = { onToggle(region.code) },
            )
        }
        regions.filter { it.code != SYSTEM_REGION }
            .sortedBy { it.name.lowercase() }
            .chunked(3)
            .forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chunk.forEach { region ->
                        val index = chosen.indexOf(region.code)
                        val selected = index >= 0
                        val main = index == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp)
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            if (main) 2.dp else 1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(10.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onToggle(region.code) }
                                .semantics {
                                    this.selected = selected
                                    contentDescription = if (main) {
                                        "${region.name}, main region"
                                    } else {
                                        region.name
                                    }
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            FlagBackground(
                                code = region.code,
                                modifier = Modifier.matchParentSize(),
                                alpha = if (selected) 0.62f else 0.38f,
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                                            ),
                                        ),
                                    ),
                            )
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    accentTextColour()
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(start = 14.dp),
                            )
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
    }
}

/** The phone as a region: a globe, a name, and the one sentence the row needs to explain itself. */
@Composable
private fun SystemRow(selected: Boolean, main: Boolean, onClick: () -> Unit) {
    val ink = if (selected) accentTextColour() else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.faded(0.35f))
            .then(
                if (selected) {
                    Modifier.border(
                        if (main) 2.dp else 1.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (main) {
                    "System, follows the phone, main region"
                } else {
                    "System, follows the phone"
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_public),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "System",
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
            )
            Text(
                text = "Follows the phone's own country",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
