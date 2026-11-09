/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.presaince.parallax.R
import com.presaince.parallax.ui.theme.accentTextColour

/** One bottom destination. Kept dumb so navigation stays in `ui/nav`. */
data class TabItem(val label: String, val icon: ImageVector)

/**
 * The app shell: top bar, menu, bottom navigation, insets. Every screen renders inside it, so
 * nothing sits under the status bar and the navigation is in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParallaxScaffold(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    tabs: List<TabItem>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBug: () -> Unit,
    /** Search is a library action, so only the library tabs offer it. */
    onSearch: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    titleContent: (@Composable () -> Unit)? = null,
    miniPlayer: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (onSearch != null) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = onOpenBug) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bug),
                            contentDescription = "Bug report",
                        )
                    }
                    // The Pburger: the brand mark, and the way into Settings.
                    IconButton(onClick = onOpenSettings) {
                        Image(
                            painter = painterResource(R.drawable.ic_pburger),
                            contentDescription = "Settings",
                            modifier = Modifier.size(26.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                )
            }
        },
        bottomBar = {
            if (miniPlayer != null || tabs.isNotEmpty()) {
                Column {
                    // Above the bar, so it is reachable without covering the destinations.
                    miniPlayer?.invoke()
                    if (tabs.isNotEmpty()) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = index == selectedTab,
                            onClick = { onSelectTab(index) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentTextColour(),
                                selectedTextColor = accentTextColour(),
                                indicatorColor = MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                            }
                        }
                    }
                }
            }
        },
        content = { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                content(innerPadding)
            }
        },
    )
}
