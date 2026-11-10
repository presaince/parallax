/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstPickTest {

    private fun recipe(name: String) = Recipe(
        name = name,
        seed = Seed(kind = "search", count = 10, query = "x"),
        filters = Filters(),
        score = Score(),
        walk = Walk(),
    )

    @Test
    fun `the first pick is Short when the built-ins are loaded`() {
        val loaded = listOf(recipe("Long"), recipe("Short"), recipe("Fresh"), recipe("Deep"))
        assertEquals("Short", firstPickName(loaded))
    }

    @Test
    fun `a renamed built-in falls back to the first recipe rather than a menu`() {
        assertEquals("Deep", firstPickName(listOf(recipe("Deep"))))
        assertNull(firstPickName(emptyList()))
    }
}
