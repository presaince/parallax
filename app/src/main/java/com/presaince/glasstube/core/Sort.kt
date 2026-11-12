/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.presaince.glasstube.R
import com.presaince.glasstube.ui.theme.glassTubeFilterChipColors
import com.presaince.glasstube.ui.theme.accentTextColour

/** The sorts a video list can offer. Likes are only known for videos the app has extracted. */
enum class SortOption(val label: String) {
    DATE("Date"),
    LIKES("Likes"),
    WATCHES("Watches"),
    PROGRESS("Progress"),
    DURATION("Duration"),
    VIEWS("Views"),
    TITLE("A-Z"),
}

data class SortState(
    val option: SortOption = SortOption.DATE,
    val ascending: Boolean = false,
) {
    fun toggled(): SortState = copy(ascending = !ascending)
}

/** A fresh state for an option: newest/most first, except titles which start A-Z. */
fun defaultSort(option: SortOption): SortState =
    SortState(option = option, ascending = option == SortOption.TITLE)

/**
 * One comparator for every list, so "oldest first" means the same thing on the feed, in history
 * and on a channel page. A missing value sorts as the smallest, so unknown likes never outrank a
 * video with real ones.
 */
fun <T> sortList(
    items: List<T>,
    state: SortState,
    date: (T) -> Long? = { null },
    likes: (T) -> Long? = { null },
    watches: (T) -> Int? = { null },
    progress: (T) -> Float? = { null },
    duration: (T) -> Long = { 0 },
    views: (T) -> Long? = { null },
    title: (T) -> String = { "" },
): List<T> {
    val comparator: Comparator<T> = when (state.option) {
        SortOption.DATE -> compareBy { date(it) ?: Long.MIN_VALUE }
        SortOption.LIKES -> compareBy { likes(it) ?: Long.MIN_VALUE }
        SortOption.WATCHES -> compareBy { watches(it) ?: Int.MIN_VALUE }
        SortOption.PROGRESS -> compareBy { progress(it) ?: -1f }
        SortOption.DURATION -> compareBy { duration(it) }
        SortOption.VIEWS -> compareBy { views(it) ?: Long.MIN_VALUE }
        SortOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { title(it) }
    }
    return items.sortedWith(if (state.ascending) comparator else comparator.reversed())
}

/**
 * The control itself: one word and a direction, not a row of chips. A chip row is a second
 * navigation bar sitting inside a list; the word says what the list is sorted by, the arrow says
 * which way, and a tap opens the six choices. The old chips are kept for the in-player lists,
 * where the row has a width budget and no list header to hide in.
 */
@Composable
fun SortControl(
    state: SortState,
    onState: (SortState) -> Unit,
    options: List<SortOption> = SortOption.entries,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { open = true }
                .heightIn(min = 40.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.option.label,
                style = MaterialTheme.typography.labelLarge,
                color = accentTextColour(),
                // The feed's header gives this cell what the three mode buttons leave; a word
                // that wrapped there would make the header two lines again.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(
                    if (state.ascending) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
                ),
                contentDescription = if (state.ascending) "Ascending" else "Descending",
                tint = accentTextColour(),
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val selected = option == state.option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        onState(
                            if (selected) state.toggled() else defaultSort(option),
                        )
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(if (state.ascending) "Descending" else "Ascending")
                },
                onClick = {
                    onState(state.toggled())
                    open = false
                },
            )
        }
    }
}

/** The chip row, for the places that need every option visible at once. */
@Composable
fun SortRow(
    state: SortState,
    onState: (SortState) -> Unit,
    options: List<SortOption> = SortOption.entries,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onState(state.toggled()) }) {
            Icon(
                painter = painterResource(
                    if (state.ascending) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
                ),
                contentDescription = if (state.ascending) "Ascending" else "Descending",
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == state.option,
                    onClick = {
                        onState(
                            if (option == state.option) state.toggled() else defaultSort(option),
                        )
                    },
                    label = { Text(option.label) },
                    colors = glassTubeFilterChipColors(),
                )
            }
        }
    }
}
