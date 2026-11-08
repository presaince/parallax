/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * One control, every long list: it appears once the list has actually travelled, and returns to
 * the top in one tap. It is deliberately not a second navigation bar — no label, no count, and it
 * hides again as soon as the first item is visible.
 */
@Composable
fun ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 600
        }
    }
    val scope = rememberCoroutineScope()
    ScrollToTopButton(
        visible = visible,
        onClick = { scope.launch { listState.animateScrollToItem(0) } },
        modifier = modifier,
    )
}

@Composable
fun ScrollToTopButton(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 2 || gridState.firstVisibleItemScrollOffset > 600
        }
    }
    val scope = rememberCoroutineScope()
    ScrollToTopButton(
        visible = visible,
        onClick = { scope.launch { gridState.animateScrollToItem(0) } },
        modifier = modifier,
    )
}

@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Back to top",
            )
        }
    }
}
