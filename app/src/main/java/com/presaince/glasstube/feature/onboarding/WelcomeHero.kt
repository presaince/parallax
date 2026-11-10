/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.onboarding

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.presaince.glasstube.R

/**
 * The welcome picture is a real capture of the feed in a phone frame, not cards drawn from
 * rectangles. A drawn field was twelve anonymous squares; this is the product, with its bottom
 * navigation, date chips and mini player already visible.
 *
 * The frame floats by a few dp while the app's animations are on. The screenshot stays still: the
 * motion is there to separate the frame from the page, not to animate the content the reader is
 * trying to recognise.
 */
@Composable
internal fun WelcomeHero(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animations = remember {
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE)
            .getBoolean("animations_enabled", true)
    }
    val float = if (!animations) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "welcome")
        val drift by transition.animateFloat(
            initialValue = -0.5f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "welcome-float",
        )
        drift
    }

    val ramp = listOf(
        MaterialTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "GlassTube",
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.linearGradient(ramp),
                ),
            )
            Text(
                text = "No ads, better features, no tracking.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        PhoneFrame(
            float = float,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(9f / 20f),
        )
    }
}

@Composable
private fun PhoneFrame(float: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = float * 5.dp.toPx()
                rotationZ = -1.5f
            }
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(3.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_feed),
            contentDescription = "The GlassTube feed",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(19.dp)),
        )
    }
}
