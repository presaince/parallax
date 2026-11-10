/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * One fill language for the whole app: a vertical capsule whose fill is the level, animated
 * 90ms so the finger feels the value move. Content (icon or text) rides centred on the fill.
 */
@Composable
fun LevelPill(
    level: Float,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 58.dp,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    trackColor: Color = Color(0xE6101014),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "level",
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(trackColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(shown)
                .background(fillColor),
        )
        content()
    }
}

/**
 * A level as a person reads it: whole percent, never more than 100, never below 0. Pure so the
 * rounding is testable without a screen.
 */
fun levelPercent(level: Float): String = "${(level.coerceIn(0f, 1f) * 100).roundToInt()}%"
