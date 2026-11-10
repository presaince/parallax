/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

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
        get() = when (name) {
            Accents.SYSTEM -> "System"
            "cyan" -> "Presaince cyan"
            else -> accentHueNames[((darkHue / 20f).toInt() % accentHueNames.size).coerceAtLeast(0)]
        }
}

private val accentHueNames = listOf(
    "Red", "Vermilion", "Orange", "Amber", "Lime", "Green", "Emerald", "Spring", "Teal",
    "Aqua", "Sky", "Blue", "Indigo", "Violet", "Purple", "Magenta", "Pink", "Crimson",
)

object Accents {
    /** The stored name of the accent that follows the phone's own wallpaper colour. */
    const val SYSTEM = "system"

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

    /**
     * The phone's own colour. Only the hue is borrowed — saturation and lightness stay ours, so a
     * washed-out wallpaper still gives an accent that reads, and the contrast floor that the whole
     * theme is measured against holds for every hue the same way.
     */
    fun system(context: Context): Accent =
        systemAccentOf(dynamicDarkColorScheme(context).primary)
}

/** HSV hue of a colour in degrees, 0..360. Pure, so the system accent is testable off the phone. */
internal fun hueOf(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val delta = max - min
    // A grey or near-grey primary has no hue to borrow. The brand hue is the honest answer:
    // inventing a colour from nothing would be a lie about what the phone chose.
    if (delta <= 0.0001f) return Accents.cyan.darkHue
    val raw = when (max) {
        color.red -> ((color.green - color.blue) / delta) % 6f
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    }
    return (raw * 60f + 360f) % 360f
}

/** The accent built from a phone colour: the same wheel maths, one hue from the wallpaper. */
internal fun systemAccentOf(primary: Color): Accent {
    val hue = hueOf(primary)
    return Accent(name = Accents.SYSTEM, darkHue = hue, lightHue = hue)
}


private const val PREFS = "glasstube"
private const val KEY_THEME = "theme_mode"
private const val KEY_ACCENT = "accent"
private const val KEY_SOLID = "solid_surfaces"
private const val KEY_CONTRAST = "contrast_policy"
private const val KEY_GLASS_BASE = "glass_base_mode"
private const val KEY_ACCENT_DAILY = "accent_daily"
private const val KEY_ACCENT_SHUFFLE = "accent_shuffle"
private const val KEY_ACCENT_DAY = "accent_day"

/** How often the accent moves. Off is the default: a colour that changes by itself is a choice. */
enum class ShuffleCadence { OFF, DAILY, HOURLY, ON_OPEN }

fun readThemeMode(context: Context): ThemeMode = runCatching {
    ThemeMode.valueOf(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.GLASS.name)
            ?: ThemeMode.GLASS.name,
    )
}.getOrDefault(ThemeMode.GLASS)

fun writeThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, mode.name)
        .apply()
}

fun readAccent(context: Context): Accent {
    val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_ACCENT, Accents.cyan.name)
    // "System" is a name, not a colour: the hue is read from the phone each time, so a new
    // wallpaper palette is picked up on the next launch without re-choosing anything.
    return if (stored == Accents.SYSTEM) Accents.system(context) else Accents.byName(stored)
}

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

/** Readable text by default: AA is the floor, Off is the reader who prefers the exact look. */
fun readContrast(context: Context): ContrastPolicy = ContrastPolicy.byName(
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CONTRAST, ContrastPolicy.AA.stored),
)

fun writeContrast(context: Context, policy: ContrastPolicy) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CONTRAST, policy.stored)
        .apply()
}

// Glass level is a continuous 0..1 fill, like the volume/brightness pills. Default 0.42.
// Off is picking another theme; the tap turns Glass on at the remembered level.
// Alpha: new key so no stale value from the earlier (broken) builds survives.
private const val KEY_GLASS_LEVEL = "glass_level_v2"

fun readGlassLevel(context: Context): Float =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getFloat(KEY_GLASS_LEVEL, 0.42f)
        .coerceIn(0f, 1f)

fun writeGlassLevel(context: Context, level: Float) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_GLASS_LEVEL, level.coerceIn(0f, 1f))
        .apply()
}

fun readGlassBase(context: Context): ThemeMode = runCatching {
    ThemeMode.valueOf(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GLASS_BASE, ThemeMode.SYSTEM.name)
            ?: ThemeMode.SYSTEM.name,
    )
}.getOrDefault(ThemeMode.SYSTEM)

fun writeGlassBase(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GLASS_BASE, mode.name)
        .apply()
}

/** A new accent each day: remembered as a switch, and as the date it last moved. */fun readAccentDaily(context: Context): Boolean =
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

/**
 * True when the resolved theme is dark. A fresh install is Glass on the phone's own light or
 * dark: the first frame of the wizard already looks like the app, and no one is handed a theme
 * they did not choose.
 */
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

/** The two ends a label on an accent fill can take. One of them always clears 4.5:1. */
private val InkOnAccent = Color(0xFF00121A)

/**
 * The label colour for an accent fill: whichever of ink and white reads better on that exact hue.
 * Picked by ratio rather than by scheme, because the accent wheel carries amber through to teal,
 * and white on those fills is a 3.3:1 pair.
 */
internal fun accentInkOn(fill: Color): Color =
    if (contrastRatio(Color.White, fill) >= contrastRatio(InkOnAccent, fill)) {
        Color.White
    } else {
        InkOnAccent
    }

internal fun darkScheme(accent: Accent) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accentInkOn(accent.darkPrimary),
    secondary = accent.darkSecondary,
    background = Color.Black,
    onBackground = Color(0xFFE8EDF2),
    surface = Tile,
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFFB4BAC2),
    error = Color(0xFFFF8A80),
)

internal fun lightScheme(accent: Accent) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accentInkOn(accent.lightPrimary),
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
internal fun glassScheme(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Float = 0.42f,
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
    // 0 is almost no glass (cards nearly solid), 1 is full glass (clearest). The fill rises
    // with the level, so an empty pill means solid and a full pill means glass.
    return base.copy(
        background = Color.Transparent,
        surface = base.surface.copy(alpha = glassSurfaceAlpha(dark, solid, level)),
        surfaceVariant = base.surfaceVariant.copy(alpha = glassVariantAlpha(dark, solid, level)),
        outlineVariant = base.outlineVariant.copy(alpha = 0.55f),
    )
}

/** Opaque fill of a glass card at a dial position. 1 is solid, 0 is the clearest this theme goes. */
internal fun glassSurfaceAlpha(dark: Boolean, solid: Boolean, level: Float): Float {
    val solidAlpha = if (dark) 0.88f else 0.94f
    if (solid) return solidAlpha
    val glassyAlpha = if (dark) 0.30f else 0.54f
    return solidAlpha - level.coerceIn(0f, 1f) * (solidAlpha - glassyAlpha)
}

/** Same for the variant fill, which carries chips and the navigation bar. */
internal fun glassVariantAlpha(dark: Boolean, solid: Boolean, level: Float): Float {
    val solidAlpha = if (dark) 0.72f else 0.84f
    if (solid) return solidAlpha
    val glassyAlpha = if (dark) 0.18f else 0.30f
    return (solidAlpha - level.coerceIn(0f, 1f) * (solidAlpha - glassyAlpha)).coerceIn(0f, 1f)
}

/** How strong the aurora is. Solid only turns it down; it stays behind every screen. */
internal fun glassPeak(dark: Boolean, solid: Boolean, scale: Float = 1f): Float {
    val base = when {
        solid && dark -> 0.22f
        solid -> 0.14f
        dark -> 0.52f
        else -> 0.34f
    }
    return base * scale.coerceIn(0f, 1f)
}

/**
 * The three aurora blobs as (centre, spread, colour). The drawing and the contrast measurement read
 * the same list, so a card cannot be measured against a backdrop that is not on screen.
 */
internal fun glassBlobs(accent: Accent, dark: Boolean): List<Triple<Offset, Float, Color>> {
    val primary = accent.darkPrimary
    val warm = if (dark) accent.darkPrimary.shifted(52f) else accent.lightPrimary.shifted(52f)
    val cool = if (dark) accent.darkPrimary.shifted(-46f) else accent.lightPrimary.shifted(-46f)
    return listOf(
        Triple(Offset(0.18f, 0.16f), 0.62f, primary),
        Triple(Offset(0.88f, 0.42f), 0.55f, warm),
        Triple(Offset(0.34f, 0.92f), 0.60f, cool),
    )
}

/** The page colour the aurora sits on, before any blob. */
internal fun glassBase(dark: Boolean): Color = if (dark) Color(0xFF07070B) else Color(0xFFEDF2F7)

/**
 * Every colour that can be behind a card at this accent and dial position: the base plus each blob
 * at its peak. The worst pair over these is the number the settings card reports, because it is the
 * worst the reader can actually land on.
 */
internal fun glassBackdropColours(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    scale: Float = 1f,
): List<Color> {
    val base = glassBase(dark)
    val peak = glassPeak(dark, solid, scale)
    return listOf(base) + glassBlobs(accent, dark).map { (_, _, colour) ->
        colour.copy(alpha = peak).over(base)
    }
}

/** The same list, composited through the card fill: what the text actually sits on. */
internal fun glassSurfaceColours(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Float,
    scale: Float = 1f,
): List<Color> {
    val fill = if (dark) Tile else Color.White
    val alpha = glassSurfaceAlpha(dark, solid, level)
    return glassBackdropColours(accent, dark, solid, scale).map { backdrop ->
        fill.copy(alpha = alpha).over(backdrop)
    }
}

/** The variant fill: chips, the navigation bar, and the rows of a settings card. */
internal fun glassVariantColours(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Float,
    scale: Float = 1f,
): List<Color> {
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    val alpha = glassVariantAlpha(dark, solid, level)
    return glassBackdropColours(accent, dark, solid, scale).map { backdrop ->
        base.surfaceVariant.copy(alpha = alpha).over(backdrop)
    }
}

/**
 * How much of the aurora survives a contrast policy. The page behind the cards is the aurora
 * itself, so its strength is the one thing that sets the floor for text sitting straight on the
 * page. Dimming the glow is the honest lever: it changes no hue and no layout.
 *
 * Scans down from full strength to [MIN_AURORA], and takes the brightest that passes. Off keeps
 * the aurora at full strength, because Off is the look the reader chose.
 */
internal fun auroraScale(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    policy: ContrastPolicy,
): Float {
    if (!policy.enforced) return 1f
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    var scale = 1f
    while (scale >= MIN_AURORA - 1e-4f) {
        val page = glassBackdropColours(accent, dark, solid, scale)
        val body = ensureContrast(base.onSurface, page, policy.ratio)
        val secondary = ensureContrast(base.onSurfaceVariant, page, policy.ratio)
        val ink = ensureContrast(base.primary, page, policy.ratio)
        val passes = worstContrast(body, page) >= policy.ratio &&
            worstContrast(secondary, page) >= policy.ratio &&
            worstContrast(ink, page) >= policy.ratio
        if (passes) return scale
        scale -= 0.05f
    }
    return MIN_AURORA
}

/** The dimmest the aurora goes before the theme stops being glass at all. */
internal const val MIN_AURORA = 0.35f

/**
 * Every surface text can land on, in order: the page, the cards, and the variant fill. The settings
 * card reports the worst pair over this list, because that is what the reader can hit.
 */
internal fun glassTextSurfaces(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Float,
    scale: Float,
): List<Color> = glassBackdropColours(accent, dark, solid, scale) +
    glassSurfaceColours(accent, dark, solid, level, scale) +
    glassVariantColours(accent, dark, solid, level, scale)

/**
 * The same list for any theme: opaque colours in Dark and Light, composited ones in Glass. Every
 * caller that asks "does this text read" goes through here, so the answer cannot depend on which
 * screen asked.
 */
internal fun textSurfaces(
    glass: Boolean,
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    level: Float,
    scale: Float = 1f,
): List<Color> = if (glass) {
    glassTextSurfaces(accent, dark, solid, level, scale)
} else {
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    listOf(base.background, base.surfaceVariant, base.surface)
}

/**
 * The clearest dial position that still lets the derived text shades clear [policy]. Text contrast
 * rises as the fill thickens, so this is a search for the last passing level, not a formula.
 *
 * The page behind the cards does not move with the dial, so it is excluded here: [auroraScale]
 * owns that half of the question. Off means no cap.
 */
internal fun maxGlassLevel(
    accent: Accent,
    dark: Boolean,
    solid: Boolean,
    policy: ContrastPolicy,
): Float {
    if (!policy.enforced) return 1f
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    val scale = auroraScale(accent, dark, solid, policy)
    var last = 0f
    var level = 0f
    while (level <= 1f + 1e-4f) {
        val surfaces = glassSurfaceColours(accent, dark, solid, level, scale) +
            glassVariantColours(accent, dark, solid, level, scale)
        val body = ensureContrast(base.onSurface, surfaces, policy.ratio)
        val secondary = ensureContrast(base.onSurfaceVariant, surfaces, policy.ratio)
        val ink = ensureContrast(base.primary, surfaces, policy.ratio)
        val passes = worstContrast(body, surfaces) >= policy.ratio &&
            worstContrast(secondary, surfaces) >= policy.ratio &&
            worstContrast(ink, surfaces) >= policy.ratio
        if (passes) last = level
        level += 0.02f
    }
    return last
}

/** The colour accent text should use: the picked hue, walked until it reads. */
internal fun accentInk(
    primary: Color,
    surfaces: List<Color>,
    policy: ContrastPolicy,
): Color = if (policy.enforced) {
    ensureContrast(primary, surfaces, policy.ratio)
} else {
    primary
}

/**
 * Whether this accent keeps every text pair above [policy] at the fill the reader is looking at.
 * The shuffle asks this before it rolls: a colour that changes by itself must not be the one that
 * breaks the floor the reader set, because the number they would need to check it is on a screen
 * they are not standing on.
 *
 * Off means every accent holds, by definition.
 */
internal fun accentHolds(
    accent: Accent,
    policy: ContrastPolicy,
    glass: Boolean,
    dark: Boolean,
    solid: Boolean,
    level: Float,
): Boolean {
    if (!policy.enforced) return true
    val base = if (dark) darkScheme(accent) else lightScheme(accent)
    val scale = if (glass) auroraScale(accent, dark, solid, policy) else 1f
    val surfaces = textSurfaces(glass, accent, dark, solid, level, scale)
    val body = ensureContrast(base.onSurface, surfaces, policy.ratio)
    val secondary = ensureContrast(base.onSurfaceVariant, surfaces, policy.ratio)
    val ink = accentInk(base.primary, surfaces, policy)
    return worstContrast(body, surfaces) >= policy.ratio &&
        worstContrast(secondary, surfaces) >= policy.ratio &&
        worstContrast(ink, surfaces) >= policy.ratio
}


/**
 * One step up from the Material defaults. The app reads dense at 11–14 sp on a phone, and the
 * smallest labels are the ones people actually squint at. The line heights move with the sizes so
 * a paragraph does not tighten when the text grows.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        titleLarge = base.titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
    )
}

@Composable
fun GlassTubeTheme(
    mode: ThemeMode,
    accent: Accent = Accents.cyan,
    solidSurfaces: Boolean = false,
    glassDark: Boolean? = null,
    glassLevel: Float = 0.42f,
    contrast: ContrastPolicy = ContrastPolicy.AA,
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
    val baseScheme = when {
        glass -> glassScheme(accent, dark, solidSurfaces, glassLevel)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    // Contrast is measured against what the reader sees: the fill composited over the aurora, not
    // the ideal token. Without this, glass reads as opaque and every number is a lie.
    // The page behind the cards does not move with the dial, so its strength is remembered against
    // the things that do change it, not against the level the finger is dragging.
    val aurora = if (glass) {
        remember(accent, dark, solidSurfaces, contrast) {
            auroraScale(accent, dark, solidSurfaces, contrast)
        }
    } else {
        1f
    }
    val surfaces = textSurfaces(glass, accent, dark, solidSurfaces, glassLevel, aurora)
    val scheme = if (contrast.enforced) {
        baseScheme.copy(
            onSurfaceVariant = ensureContrast(baseScheme.onSurfaceVariant, surfaces, contrast.ratio),
        )
    } else {
        baseScheme
    }
    val ink = accentInk(scheme.primary, surfaces, contrast)

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
    ) {
        // One stable Box around the app: only its background changes with the theme. Swapping the
        // structure (content vs Box(content)) would remount the whole navigation and lose the
        // screen the user was standing on when they picked a theme.
        val glassBackdrop = if (glass) {
            GlassBackdropSpec(
                accent = accent,
                dark = dark,
                solid = solidSurfaces,
                scale = aurora,
            )
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
            CompositionLocalProvider(
                LocalGlassBackdrop provides glassBackdrop,
                LocalAccentInk provides ink,
                LocalSurfaces provides surfaces,
            ) {
                content()
            }
        }
    }
}

/** Accent text: the chosen hue, walked until it reads on the current surface. Fills keep primary. */
val LocalAccentInk = staticCompositionLocalOf { Color.Unspecified }

/** Every surface a screen can sit on, already composited. Empty before any theme has been applied. */
val LocalSurfaces = staticCompositionLocalOf<List<Color>> { emptyList() }

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
private data class GlassBackdropSpec(
    val accent: Accent,
    val dark: Boolean,
    val solid: Boolean,
    val scale: Float,
)

private fun DrawScope.drawGlassBackdrop(spec: GlassBackdropSpec) {
    drawRect(glassBase(spec.dark))
    val peak = glassPeak(spec.dark, spec.solid, spec.scale)
    // Same three blobs the contrast measurement reads, so a card cannot be checked against a
    // backdrop that is not on screen.
    glassBlobs(spec.accent, spec.dark).forEach { (fraction, spread, colour) ->
        val centre = Offset(size.width * fraction.x, size.height * fraction.y)
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
