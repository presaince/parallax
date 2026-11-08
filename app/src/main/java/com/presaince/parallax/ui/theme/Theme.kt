/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.toArgb

/** Presaince cyan. The gradient is reserved for the wordmark, splash and About screen. */
val Cyan = Color(0xFF4CC2E8)
val CyanSoft = Color(0xFFA5E4F5)
val Tile = Color(0xFF101014)

enum class ThemeMode { SYSTEM, DARK, LIGHT, GLASS }

/**
 * Glass is the one theme that is not simply light or dark: it is a transparency, and what shows
 * through it can be either. It takes its light or dark from the last ordinary theme that was
 * chosen — pick Dark, then Glass, and the glass is dark; pick Light first and it is light. Three
 * chips under the picker were three choices too many for a style that already had an answer.
 */
val GLASS_BASE_MODES = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)

/** The light-ness a Glass base mode resolves to, or null when it should ask the phone. */
fun glassDarkFor(base: ThemeMode): Boolean? = when (base) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    else -> null
}

/**
 * Accent choices: a 7×3 field of hues, so a person can pick by eye rather than by name. The first
 * is Presaince cyan and stays the default; the stored value is the name, so old settings keep
 * working.
 */
data class Accent(
    val name: String,
    val darkHue: Float,
    val lightHue: Float,
) {
    val darkPrimary: Color get() = Color.hsl(darkHue, 0.62f, 0.72f)
    val darkSecondary: Color get() = Color.hsl(darkHue, 0.55f, 0.84f)
    val lightPrimary: Color get() = Color.hsl(lightHue, 0.78f, 0.32f)
    val lightSecondary: Color get() = Color.hsl(lightHue, 0.55f, 0.44f)

    /** What the swatch is called when a screen has to say it in words. */
    val label: String
        get() = if (name == "cyan") {
            "Presaince cyan"
        } else {
            accentHueNames[((darkHue / 20f).toInt() % accentHueNames.size).coerceAtLeast(0)]
        }
}

private val accentHueNames = listOf(
    "Red", "Vermilion", "Orange", "Amber", "Lime", "Green", "Emerald", "Spring", "Teal",
    "Aqua", "Sky", "Blue", "Indigo", "Violet", "Purple", "Magenta", "Pink", "Crimson",
)

object Accents {
    val cyan = Accent("cyan", darkHue = 196f, lightHue = 196f)

    /**
     * 18 hues at 20° steps, an evenly spaced wheel so no two swatches sit close enough to be
     * mistaken for each other. The wheel starts at 20°, a deep orange: the picker reads warm to
     * cool, and the first swatch is the one a reader expects to be orange rather than amber or,
     * as it once was, blue. 20° also keeps the 200° slot for the brand cyan.
     */
    val all: List<Accent> = buildList {
        (0 until 18).forEach { step ->
            val hue = ((20f + step * 20f) % 360f)
            // Cyan is the brand hue and is not on the 20° grid; it takes the slot nearest it.
            if (hue == 200f) {
                add(cyan)
            } else {
                add(Accent(name = "h${hue.toInt()}", darkHue = hue, lightHue = hue))
            }
        }
    }

    fun byName(name: String?): Accent = all.firstOrNull { it.name == name } ?: cyan
}


private const val PREFS = "parallax"
private const val KEY_THEME = "theme_mode"
private const val KEY_ACCENT = "accent"
private const val KEY_SOLID = "solid_surfaces"
private const val KEY_GLASS_BASE = "glass_base_mode"
private const val KEY_ACCENT_DAILY = "accent_daily"
private const val KEY_ACCENT_SHUFFLE = "accent_shuffle"
private const val KEY_ACCENT_DAY = "accent_day"

/** How often the accent moves. Off is the default: a colour that changes by itself is a choice. */
enum class ShuffleCadence { OFF, DAILY, HOURLY, ON_OPEN }

fun readThemeMode(context: Context): ThemeMode = runCatching {
    ThemeMode.valueOf(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.DARK.name)
            ?: ThemeMode.DARK.name,
    )
}.getOrDefault(ThemeMode.DARK)

fun writeThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, mode.name)
        .apply()
}

fun readAccent(context: Context): Accent = Accents.byName(
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_ACCENT, Accents.cyan.name),
)

fun writeAccent(context: Context, accent: Accent) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ACCENT, accent.name)
        .apply()
}

fun readSolidSurfaces(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SOLID, false)

fun writeSolidSurfaces(context: Context, solid: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SOLID, solid)
        .apply()
}

// Eight levels for the Glass slider: the same shape, a dial instead of two halves.
private const val KEY_GLASS_LEVEL = "glass_level"

fun readGlassLevel(context: Context): Int =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_GLASS_LEVEL, 3)
        .coerceIn(0, 7)

fun writeGlassLevel(context: Context, level: Int) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_GLASS_LEVEL, level.coerceIn(0, 7))
        .apply()
}

fun readGlassBase(context: Context): ThemeMode = runCatching {
    ThemeMode.valueOf(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GLASS_BASE, ThemeMode.DARK.name)
            ?: ThemeMode.DARK.name,
    )
}.getOrDefault(ThemeMode.DARK)

fun writeGlassBase(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GLASS_BASE, mode.name)
        .apply()
}

/** A new accent each day: remembered as a switch, and as the date it last moved. */
fun readAccentDaily(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ACCENT_DAILY, false)

fun writeAccentDaily(context: Context, daily: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ACCENT_DAILY, daily)
        .apply()
}

/**
 * The cadence, with the old daily switch read as a migration: an install that had "shuffle daily"
 * on keeps shuffling daily until its owner picks something else.
 */
fun readShuffleCadence(context: Context): ShuffleCadence {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_ACCENT_SHUFFLE, null)
    if (stored != null) {
        return runCatching { ShuffleCadence.valueOf(stored) }.getOrDefault(ShuffleCadence.OFF)
    }
    return if (prefs.getBoolean(KEY_ACCENT_DAILY, false)) {
        ShuffleCadence.DAILY
    } else {
        ShuffleCadence.OFF
    }
}

fun writeShuffleCadence(context: Context, cadence: ShuffleCadence) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ACCENT_SHUFFLE, cadence.name)
        .putBoolean(KEY_ACCENT_DAILY, cadence != ShuffleCadence.OFF)
        .apply()
}

fun readAccentDay(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_ACCENT_DAY, null)

fun writeAccentDay(context: Context, day: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ACCENT_DAY, day)
        .apply()
}

/** True when the resolved theme is dark; the app is dark by default, as the brand sets it. */
fun isDarkTheme(
    mode: ThemeMode,
    context: Context,
    glassDark: Boolean? = null,
): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM ->
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    ThemeMode.GLASS -> glassDark ?: (
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        )
}

private fun darkScheme(accent: Accent) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF00121A),
    secondary = accent.darkSecondary,
    background = Color.Black,
    onBackground = Color(0xFFE8EDF2),
    surface = Tile,
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFFB4BAC2),
    error = Color(0xFFFF8A80),
)

private fun lightScheme(accent: Accent) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    secondary = accent.lightSecondary,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF101014),
    surface = Color.White,
    onSurface = Color(0xFF101014),
    surfaceVariant = Color(0xFFE6EBF0),
    onSurfaceVariant = Color(0xFF4A5560),
    error = Color(0xFFB3261E),
)

/** oklch to sRGB, for the brand's light ramp, which the portfolio defines in oklch. */
fun oklch(l: Double, c: Double, h: Double): Color {
    val radians = Math.toRadians(h)
    val a = c * kotlin.math.cos(radians)
    val b = c * kotlin.math.sin(radians)

    val lPrime = l + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = l - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = l - 0.0894841775 * a - 1.2914855480 * b

    val lc = lPrime * lPrime * lPrime
    val mc = mPrime * mPrime * mPrime
    val sc = sPrime * sPrime * sPrime

    fun gamma(value: Double): Double {
        val clamped = value.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) 12.92 * clamped
        else 1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055
    }

    val r = gamma(4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc)
    val g = gamma(-1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc)
    val bl = gamma(-0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc)

    return Color(
        red = r.toFloat().coerceIn(0f, 1f),
        green = g.toFloat().coerceIn(0f, 1f),
        blue = bl.toFloat().coerceIn(0f, 1f),
    )
}

/**
 * Glass: the same scheme with translucent surfaces over a static accent glow. The backdrop is a
 * gradient, not the video or a scrolling list — that is the one kind of backdrop this can honestly
 * blur/tint, and the reason the theme has a solid-surfaces escape hatch.
 */
private fun glassScheme(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Int = 3,
): ColorScheme {
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    // Solid is Glass with the dial turned down, not Glass replaced by Dark or Light. The aurora
    // stays behind the page; only the cards close up, so the theme keeps its identity instead of
    // becoming a second copy of the ordinary schemes.
    //
    // Glass has to be visibly glass. A surface that is 40% opaque over an aurora that is only 26%
    // strong composites to the same near-black as a closed surface, which is why the two settings
    // used to look identical on a real screen: the transparency was real but there was nothing
    // behind it to see. The fill is thinner and the backdrop much louder.
    // Level 0 is the clearest glass, level 7 nearly solid.
    val t = level.coerceIn(0, 7) / 7f
    val surfaceAlpha = if (solid) {
        if (dark) 0.88f else 0.94f
    } else {
        val glassy = if (dark) 0.30f else 0.54f
        glassy + t * ((if (dark) 0.62f else 0.44f) - glassy)
    }
    val variantAlpha = (if (solid) {
        if (dark) 0.72f else 0.84f
    } else {
        (if (dark) 0.18f else 0.30f) + t * 0.42f
    }).coerceIn(0f, 1f)
    return base.copy(
        background = Color.Transparent,
        surface = base.surface.copy(alpha = surfaceAlpha),
        surfaceVariant = base.surfaceVariant.copy(alpha = variantAlpha),
        outlineVariant = base.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
fun ParallaxTheme(
    mode: ThemeMode,
    accent: Accent = Accents.cyan,
    solidSurfaces: Boolean = false,
    glassDark: Boolean? = null,
    glassLevel: Int = 3,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.GLASS -> glassDark ?: systemDark
    }
    val glass = mode == ThemeMode.GLASS
    val scheme = when {
        glass -> glassScheme(accent, dark, solidSurfaces, glassLevel)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }

    MaterialTheme(
        colorScheme = scheme,
    ) {
        // One stable Box around the app: only its background changes with the theme. Swapping the
        // structure (content vs Box(content)) would remount the whole navigation and lose the
        // screen the user was standing on when they picked a theme.
        val glassBackdrop = if (glass) {
            GlassBackdropSpec(accent = accent, dark = dark, solid = solidSurfaces)
        } else {
            null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (glassBackdrop != null) {
                        Modifier.drawBehind { drawGlassBackdrop(glassBackdrop) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
                content()
            }
        }
    }
}

/** What the root is drawing, so a screen stacked on top can draw the same thing and stay opaque. */
private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropSpec?> { null }

/**
 * An opaque floor for a screen drawn over another one. Under layered navigation the screen below
 * stays composed, and the Glass theme's transparent background means its cards would show through
 * the page above it. This paints the same backdrop the root does: the aurora for Glass, the theme
 * background otherwise.
 */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val glass = LocalGlassBackdrop.current
    Box(
        modifier = modifier
            .then(
                if (glass != null) {
                    Modifier.drawBehind { drawGlassBackdrop(glass) }
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                },
            ),
    )
}

/**
 * Glass is a pane, not a light bulb: the backdrop is a slow aurora of three accent-adjacent hues,
 * not one radial glow in the middle. It is drawn once per frame from constants — no animation, no
 * blur, nothing to tick — because the skill's cost model says a moving backdrop is not glass.
 *
 * `solid` only turns the aurora down. Solid is the same theme with the dial near zero, not a
 * different backdrop, so switching between the two keeps the accent and the layout identical.
 */
private data class GlassBackdropSpec(val accent: Accent, val dark: Boolean, val solid: Boolean)

private fun DrawScope.drawGlassBackdrop(spec: GlassBackdropSpec) {
    val base = if (spec.dark) Color(0xFF07070B) else Color(0xFFEDF2F7)
    drawRect(base)
    val primary = spec.accent.darkPrimary.hueShifted(0f)
    val warm = if (spec.dark) {
        spec.accent.darkPrimary.hueShifted(52f)
    } else {
        spec.accent.lightPrimary.hueShifted(52f)
    }
    val cool = if (spec.dark) {
        spec.accent.darkPrimary.hueShifted(-46f)
    } else {
        spec.accent.lightPrimary.hueShifted(-46f)
    }
    val blobs = listOf(
        Triple(Offset(size.width * 0.18f, size.height * 0.16f), 0.62f, primary),
        Triple(Offset(size.width * 0.88f, size.height * 0.42f), 0.55f, warm),
        Triple(Offset(size.width * 0.34f, size.height * 0.92f), 0.60f, cool),
    )
    // Strong enough that a thin card over it reads as glass rather than as flat dark.
    val peak = when {
        spec.solid && spec.dark -> 0.22f
        spec.solid -> 0.14f
        spec.dark -> 0.52f
        else -> 0.34f
    }
    blobs.forEach { (centre, spread, colour) ->
        val radius = size.maxDimension * spread
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colour.copy(alpha = peak),
                    Color.Transparent,
                ),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * The colour at a fraction of its own alpha. `copy(alpha = x)` replaces the alpha, so a tile built
 * with a fixed one looks the same in Glass and in Solid; multiplying keeps the theme's transparency
 * and only takes some of it.
 */
fun Color.faded(fraction: Float): Color = copy(alpha = alpha * fraction)

/** The same colour, some degrees around the wheel: the backdrop's other two hues. */
private fun Color.hueShifted(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
