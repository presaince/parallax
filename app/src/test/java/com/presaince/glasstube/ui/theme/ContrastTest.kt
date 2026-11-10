/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The arithmetic the theme and the settings card both stand on. */
class ContrastTest {

    @Test
    fun black_on_white_is_the_maximum_ratio() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.05f)
    }

    @Test
    fun a_ratio_is_the_same_read_in_either_order() {
        val ink = Color(0xFF4A5560)
        val paper = Color(0xFFEDF2F7)
        assertEquals(contrastRatio(ink, paper), contrastRatio(paper, ink), 0.001f)
    }

    @Test
    fun a_pair_that_already_passes_is_not_moved() {
        val ink = Color(0xFF101014)
        val paper = Color(0xFFFFFFFF)
        assertEquals(ink, ensureContrast(ink, listOf(paper), 4.5f))
    }

    @Test
    fun a_walk_never_rotates_the_hue() {
        val picked = Color.hsl(340f, 0.78f, 0.32f)
        val card = listOf(Color(0xFFE9D8DF), Color(0xFFF2E4EA))
        val walked = ensureContrast(picked, card, 4.5f)
        val before = picked.toHsl().first
        val after = walked.toHsl().first
        assertTrue("hue moved by ${abs(after - before)}", abs(after - before) < 1.5f)
    }

    @Test
    fun a_walk_reaches_the_target_when_one_exists() {
        val picked = Color.hsl(60f, 0.78f, 0.32f)
        val card = listOf(Color(0xFFF4EBEB))
        val walked = ensureContrast(picked, card, 4.5f)
        assertTrue(worstContrast(walked, card) >= 4.5f)
    }

    @Test
    fun a_walk_stops_at_the_shift_bound_when_the_target_is_impossible() {
        val picked = Color(0xFF808080)
        val walked = ensureContrast(picked, listOf(Color(0xFF7E7E7E)), 21f)
        val moved = abs(walked.toHsl().third - picked.toHsl().third)
        assertTrue("lightness moved by $moved", moved <= 0.201f)
    }

    @Test
    fun worst_contrast_reports_the_hardest_of_the_surfaces() {
        val ink = Color.White
        val easy = Color.Black
        val hard = Color(0xFFBFBFBF)
        assertEquals(contrastRatio(ink, hard), worstContrast(ink, listOf(easy, hard)), 0.001f)
    }

    @Test
    fun compositing_a_translucent_fill_moves_it_towards_the_backdrop() {
        val fill = Color(0x80FFFFFF)
        val backdrop = Color.Black
        val seen = fill.over(backdrop)
        assertTrue(seen.relativeLuminance() < 1f)
        assertTrue(seen.relativeLuminance() > 0f)
    }
}
