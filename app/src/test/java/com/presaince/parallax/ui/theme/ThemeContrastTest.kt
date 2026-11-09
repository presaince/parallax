/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
/**
 * The promise the Contrast card makes, checked for every accent the wheel offers: at a chosen
 * policy, every text pair clears the ratio, and the accent keeps its hue while it is walked.
 */
class ThemeContrastTest {

    private val accents = Accents.all
    private val modes = listOf(true, false)

    private data class Resolved(
        val body: Float,
        val secondary: Float,
        val ink: Float,
        val inkColour: androidx.compose.ui.graphics.Color,
        val primary: androidx.compose.ui.graphics.Color,
    )

    private fun resolve(accent: Accent, dark: Boolean, level: Float, policy: ContrastPolicy): Resolved {
        val base = if (dark) darkScheme(accent) else lightScheme(accent)
        val scale = auroraScale(accent, dark, solid = false, policy = policy)
        val surfaces = glassTextSurfaces(accent, dark, solid = false, level = level, scale = scale)
        val bodyColour = ensureContrast(base.onSurface, surfaces, policy.ratio)
        val secondaryColour = ensureContrast(base.onSurfaceVariant, surfaces, policy.ratio)
        val ink = accentInk(base.primary, surfaces, policy)
        return Resolved(
            body = worstContrast(bodyColour, surfaces),
            secondary = worstContrast(secondaryColour, surfaces),
            ink = worstContrast(ink, surfaces),
            inkColour = ink,
            primary = base.primary,
        )
    }

    @Test
    fun aa_holds_for_every_accent_at_the_default_glass_level() {
        for (accent in accents) {
            for (dark in modes) {
                val r = resolve(accent, dark, 0.42f, ContrastPolicy.AA)
                assertTrue("${accent.name} dark=$dark body", r.body >= 4.5f)
                assertTrue("${accent.name} dark=$dark secondary", r.secondary >= 4.5f)
                assertTrue("${accent.name} dark=$dark accent text", r.ink >= 4.5f)
            }
        }
    }

    @Test
    fun every_accent_holds_up_to_the_level_cap_it_is_given() {
        for (accent in accents) {
            for (dark in modes) {
                for (policy in listOf(ContrastPolicy.AA, ContrastPolicy.AAA)) {
                    val cap = maxGlassLevel(accent, dark, solid = false, policy = policy)
                    var level = 0f
                    while (level <= cap + 1e-4f) {
                        val r = resolve(accent, dark, level, policy)
                        assertTrue(
                            "${accent.name} dark=$dark ${policy.label} at $level body",
                            r.body >= policy.ratio,
                        )
                        assertTrue(
                            "${accent.name} dark=$dark ${policy.label} at $level secondary",
                            r.secondary >= policy.ratio,
                        )
                        assertTrue(
                            "${accent.name} dark=$dark ${policy.label} at $level accent text",
                            r.ink >= policy.ratio,
                        )
                        level += 0.05f
                    }
                }
            }
        }
    }

    @Test
    fun a_stricter_policy_never_allows_more_glass() {
        for (accent in accents) {
            for (dark in modes) {
                val aa = maxGlassLevel(accent, dark, solid = false, policy = ContrastPolicy.AA)
                val aaa = maxGlassLevel(accent, dark, solid = false, policy = ContrastPolicy.AAA)
                assertTrue("${accent.name} dark=$dark", aaa <= aa + 1e-4f)
            }
        }
    }

    @Test
    fun the_walked_accent_keeps_its_hue() {
        for (accent in accents) {
            for (dark in modes) {
                for (policy in listOf(ContrastPolicy.AA, ContrastPolicy.AAA)) {
                    val r = resolve(accent, dark, 0.42f, policy)
                    val before = r.primary.toHsl().first
                    val walked = r.inkColour.toHsl()
                    // A walk that bottoms out at white or black has no hue left to compare; it only
                    // happens when the pair was unreachable in the first place.
                    if (walked.second < 0.02f) continue
                    assertTrue(
                        "${accent.name} dark=$dark ${policy.label} hue moved by " +
                            "${abs(walked.first - before)}",
                        abs(walked.first - before) < 2f,
                    )
                }
            }
        }
    }

    @Test
    fun a_label_on_an_accent_fill_always_reads() {
        for (accent in accents) {
            for (dark in modes) {
                val scheme = if (dark) darkScheme(accent) else lightScheme(accent)
                assertTrue(
                    "${accent.name} dark=$dark onPrimary " +
                        "${contrastRatio(scheme.onPrimary, scheme.primary)}",
                    contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5f,
                )
            }
        }
    }

    @Test
    fun off_returns_the_accent_untouched() {
        for (accent in accents) {
            for (dark in modes) {
                val r = resolve(accent, dark, 0.42f, ContrastPolicy.OFF)
                assertEquals(r.primary, r.inkColour)
            }
        }
    }

    @Test
    fun the_translucent_fill_never_reads_brighter_than_its_base() {
        // A dark fill over a dark aurora must stay a dark surface; if this inverts, the token and the
        // compositing disagree and every ratio in the card is meaningless.
        val surfaces = glassSurfaceColours(Accents.cyan, dark = true, solid = false, level = 0.42f)
        for (surface in surfaces) {
            assertTrue(surface.relativeLuminance() < 0.2f)
        }
    }
}
