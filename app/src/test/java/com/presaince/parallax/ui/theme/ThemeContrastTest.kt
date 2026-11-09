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

    private fun resolve(
        accent: Accent,
        dark: Boolean,
        level: Float,
        policy: ContrastPolicy,
        glass: Boolean = true,
    ): Resolved {
        val base = if (dark) darkScheme(accent) else lightScheme(accent)
        val scale = if (glass) auroraScale(accent, dark, solid = false, policy = policy) else 1f
        val surfaces = textSurfaces(glass, accent, dark, solid = false, level = level, scale = scale)
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
    fun every_accent_the_shuffle_can_pick_holds_the_policy() {
        for (accent in accents) {
            for (dark in modes) {
                for (policy in listOf(ContrastPolicy.AA, ContrastPolicy.AAA)) {
                    for (level in listOf(0f, 0.42f, 0.9f, 1f)) {
                        if (!accentHolds(accent, policy, glass = true, dark, solid = false, level)) continue
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
                    }
                }
            }
        }
    }

    @Test
    fun the_shuffle_always_has_somewhere_to_go_at_aa() {
        for (accent in accents) {
            for (dark in modes) {
                val pool = accents.filter {
                    it.name != accent.name &&
                        accentHolds(it, ContrastPolicy.AA, glass = true, dark, solid = false, level = 0.42f)
                }
                assertTrue("${accent.name} dark=$dark left no accent to roll to", pool.isNotEmpty())
            }
        }
    }

    @Test
    fun off_lets_the_shuffle_pick_anything() {
        for (accent in accents) {
            for (dark in modes) {
                assertTrue(accentHolds(accent, ContrastPolicy.OFF, glass = true, dark, false, 1f))
            }
        }
    }

    @Test
    fun light_theme_needs_the_setting_for_a_warm_accent() {
        // The case that proves the card is not Glass-only: amber as text on a white card is 3.3:1
        // raw. Off leaves it there; Standard walks it to 4.5.
        val amber = Accents.byName("h60")
        val off = resolve(amber, dark = false, level = 0.42f, policy = ContrastPolicy.OFF, glass = false)
        val standard = resolve(amber, dark = false, level = 0.42f, policy = ContrastPolicy.AA, glass = false)
        assertTrue("raw amber reads at ${off.ink}", off.ink < 4.5f)
        assertTrue("walked amber reads at ${standard.ink}", standard.ink >= 4.5f)
    }

    @Test
    fun dark_theme_already_clears_the_top_setting() {
        for (accent in accents) {
            val off = resolve(accent, dark = true, level = 0.42f, policy = ContrastPolicy.OFF, glass = false)
            assertTrue("${accent.name} body", off.body >= 7f)
            assertTrue("${accent.name} secondary", off.secondary >= 7f)
        }
    }

    @Test
    fun the_shuffle_guard_reads_the_theme_it_is_in() {
        for (accent in accents) {
            val holdsInLight = accentHolds(
                accent,
                ContrastPolicy.AA,
                glass = false,
                dark = false,
                solid = false,
                level = 0.42f,
            )
            if (!holdsInLight) continue
            val r = resolve(accent, dark = false, level = 0.42f, policy = ContrastPolicy.AA, glass = false)
            assertTrue("${accent.name} in light", r.ink >= 4.5f)
            assertTrue("${accent.name} in light", r.body >= 4.5f)
            assertTrue("${accent.name} in light", r.secondary >= 4.5f)
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
