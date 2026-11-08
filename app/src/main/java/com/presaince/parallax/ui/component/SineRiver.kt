/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.presaince.parallax.ui.theme.Accents
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * The app's one motion motif: three sine layers crossing in the accent's neighbouring hues, like a
 * river. Used wherever the app waits, so waiting has a shape instead of a spinner.
 *
 * With [interactive] the water responds to a finger: the layer under it is pulled toward the touch,
 * and the pull fades after release. It is a stir, not a physics engine — no water simulation, no
 * particles, nothing that costs a frame.
 */
@Composable
fun SineRiver(
    modifier: Modifier = Modifier,
    alpha: Float = 0.4f,
    speedMillis: Int = 3200,
    interactive: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "river")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(speedMillis, easing = LinearEasing)),
        label = "phase",
    )
    val accent = MaterialTheme.colorScheme.primary
    // The secondaries are the two accents nearest the live one on the wheel, taken from the
    // palette the reader can actually choose. A fixed ±14° looked fine on most accents and muddy
    // on the rest; this way every colour gets the neighbours it was designed next to.
    val hues = remember(accent) {
        val neighbours = Accents.all
            .map { it to hueDistance(accent, it.darkPrimary) }
            .filter { it.second > 8f }
            .sortedBy { it.second }
            .take(2)
            .map { it.first.darkPrimary }
        when (neighbours.size) {
            2 -> listOf(neighbours[0], accent, neighbours[1])
            1 -> listOf(neighbours[0], accent, accent.shiftHue(14f))
            else -> listOf(accent.shiftHue(-14f), accent, accent.shiftHue(14f))
        }
    }

    var touch by remember { mutableStateOf<Offset?>(null) }
    val stir = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = if (interactive) {
            modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The river is its own gesture: without consuming, a horizontal drag belongs
                    // to the pager underneath and the app swipes to another tab while you stir.
                    down.consume()
                    touch = down.position
                    scope.launch { stir.animateTo(0.45f, tween(90)) }
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val moved = (change.position - last).getDistance()
                        last = change.position
                        touch = change.position
                        if (moved > 0.5f) {
                            scope.launch {
                                stir.snapTo((stir.value + moved / 55f).coerceAtMost(1f))
                            }
                        }
                        if (!change.pressed) break
                    }
                    touch = null
                    scope.launch { stir.animateTo(0f, tween(1200)) }
                }
            }
        } else {
            modifier
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val finger = touch
            val pull = stir.value
            repeat(3) { layer ->
                val path = Path()
                val amplitude = size.height * (0.10f + 0.04f * layer)
                val frequency = (2.0 * PI / size.width.coerceAtLeast(1f)).toFloat() *
                    (1.1f + layer * 0.45f)
                val shift = phase + layer * 1.7f
                var x = 0f
                while (x <= size.width) {
                    var y = midY(layer, size.height) + sin(x * frequency + shift) * amplitude
                    if (finger != null && pull > 0.01f) {
                        // A soft Gaussian around the finger: the water leans toward the touch and
                        // springs back when the stir decays.
                        val d = (x - finger.x) / (size.width * 0.16f)
                        val influence = exp(-d * d)
                        y += (finger.y - y) * influence * pull * 0.55f
                    }
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 3f
                }
                drawPath(
                    path = path,
                    color = hues[layer].copy(alpha = alpha * (1f - layer * 0.18f)),
                    style = Stroke(width = 2.2f + layer * 0.9f),
                )
            }
            if (finger != null && pull > 0.02f) {
                drawCircle(
                    color = accent.copy(alpha = 0.10f * pull),
                    radius = size.width * 0.16f,
                    center = finger,
                )
            }
        }
    }
}

private fun midY(layer: Int, height: Float): Float =
    height / 2f + (layer - 1) * height * 0.18f

/** The same colour, a few degrees around the wheel. */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** How far apart two colours are on the wheel, in degrees, the short way round. */
private fun hueDistance(a: Color, b: Color): Float {
    val first = FloatArray(3)
    val second = FloatArray(3)
    android.graphics.Color.colorToHSV(a.toArgb(), first)
    android.graphics.Color.colorToHSV(b.toArgb(), second)
    val raw = kotlin.math.abs(first[0] - second[0])
    return if (raw > 180f) 360f - raw else raw
}
