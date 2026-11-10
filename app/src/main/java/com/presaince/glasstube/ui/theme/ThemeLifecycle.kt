/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The theme controller with its foreground wiring in one place. The first resume on cold start
 * and every alt+tab return both land in [ThemeController.onForeground], so the activity never
 * needs to know the accent answers to the lifecycle.
 */
@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current
    val theme = remember { ThemeController(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            theme.onForeground()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) theme.onForeground()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return theme
}
