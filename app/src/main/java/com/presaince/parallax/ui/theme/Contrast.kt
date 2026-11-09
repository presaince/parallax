/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How hard the theme works to keep text readable. Off is today's look exactly: the accent, the
 * glass fill and the text shades are used as chosen. AA and AAA derive shades from the same hue
 * until the pair clears the WCAG ratio.
 *
 * Nothing here ever swaps the hue: an accent the reader picked stays that accent. Only lightness
 * moves, and only for text and the fill behind it.
 */
enum class ContrastPolicy(val stored: String, val ratio: Float, val label: String) {
    OFF("off", 1f, "Off"),
    AA("aa", 4.5f, "Standard"),
    AAA("aaa", 7f, "High");

    /** True when shades may be derived; Off leaves every colour as it was. */
    val enforced: Boolean get() = this != OFF

    val target: String get() = if (this == OFF) "As chosen" else "${trim(ratio)}:1"

    companion object {
        val all: List<ContrastPolicy> = listOf(OFF, AA, AAA)

        fun byName(name: String?): ContrastPolicy =
            all.firstOrNull { it.stored == name } ?: AA
    }
}

private fun trim(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/**
 * WCAG 2.2 relative luminance of one colour. Alpha is ignored: callers composite first, because a
 * translucent surface's contrast is a property of what the reader actually sees.
 */
fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f
        else ((value + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** The WCAG contrast ratio of two colours, 1..21. Symmetric. */
fun contrastRatio(first: Color, second: Color): Float {
    val a = first.relativeLuminance()
    val b = second.relativeLuminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/** The worst ratio this text colour reaches over any of the surfaces it can land on. */
fun worstContrast(foreground: Color, surfaces: List<Color>): Float =
    surfaces.minOfOrNull { contrastRatio(foreground, it) } ?: contrastRatio(foreground, Color.Black)

/** sRGB compositing of a translucent layer over what is behind it. */
fun Color.over(background: Color): Color = compositeOver(background)

/** Hue, saturation and lightness of an sRGB colour, each 0..1 except hue in degrees. */
internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    val lightness = (high + low) / 2f
    if (delta < 1e-6f) return Triple(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (high) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return Triple((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
}

/** The same colour, walked around the wheel. Kept pure so a JVM test can hold the backdrop still. */
internal fun Color.shifted(degrees: Float): Color {
    val (hue, saturation, lightness) = toHsl()
    return Color.hsl((hue + degrees + 360f) % 360f, saturation, lightness)
}

/**
 * [foreground] moved along its own lightness until it clears [target] against every surface it can
 * sit on. Returns the least-shifted colour that passes, so a passing pair is returned untouched.
 *
 * [maxShift] bounds the walk: past about a fifth of the lightness range a secondary text colour has
 * become primary text, and the hierarchy is gone. If nothing within the bound passes, the best
 * candidate is returned and the caller decides what to do — the theme clamps the glass fill, the
 * settings card says so in words.
 */
fun ensureContrast(
    foreground: Color,
    surfaces: List<Color>,
    target: Float,
    maxShift: Float = 0.20f,
): Color {
    if (target <= 1f) return foreground
    if (worstContrast(foreground, surfaces) >= target) return foreground
    val (hue, saturation, lightness) = foreground.toHsl()
    var best = foreground
    var bestRatio = worstContrast(foreground, surfaces)
    var step = 0.01f
    while (step <= maxShift + 1e-4f) {
        for (direction in floatArrayOf(-1f, 1f)) {
            val candidate = Color.hsl(
                hue,
                saturation,
                (lightness + direction * step).coerceIn(0f, 1f),
                foreground.alpha,
            )
            val ratio = worstContrast(candidate, surfaces)
            if (ratio >= target) return candidate
            if (ratio > bestRatio) {
                best = candidate
                bestRatio = ratio
            }
        }
        step += 0.01f
    }
    return best
}
