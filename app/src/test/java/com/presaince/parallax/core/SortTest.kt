/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One comparator feeds the feed, history, library, channel and search. The rules that matter are
 * the ones a second copy would get wrong: a missing value is the smallest, and A-Z ignores case.
 */
class SortTest {

    private data class Row(
        val name: String,
        val date: Long? = null,
        val likes: Long? = null,
        val views: Long? = null,
        val duration: Long = 0,
    )

    private val rows = listOf(
        Row("b", date = 200, likes = 10, views = 1_000, duration = 60),
        Row("A", date = 100, likes = null, views = 5_000, duration = 300),
        Row("c", date = null, likes = 50, views = null, duration = 10),
    )

    private fun names(items: List<Row>) = items.map { it.name }

    @Test
    fun `descending is the default direction for values`() {
        val sorted = sortList(rows, SortState(SortOption.DATE), date = { it.date })
        assertEquals(listOf("b", "A", "c"), names(sorted))
    }

    @Test
    fun `ascending flips the same comparator`() {
        val sorted = sortList(rows, SortState(SortOption.DATE, ascending = true), date = { it.date })
        assertEquals(listOf("c", "A", "b"), names(sorted))
    }

    @Test
    fun `a missing value sorts as the smallest`() {
        // Descending: the unknown row is last, so it can never outrank a real value.
        assertEquals(
            listOf("c", "b", "A"),
            names(sortList(rows, SortState(SortOption.LIKES), likes = { it.likes })),
        )
        // Ascending: unknown first, which is the same rule read the other way.
        assertEquals(
            listOf("A", "b", "c"),
            names(sortList(rows, SortState(SortOption.LIKES, ascending = true), likes = { it.likes })),
        )
    }

    @Test
    fun `views use the same missing-value rule`() {
        assertEquals(
            listOf("A", "b", "c"),
            names(sortList(rows, SortState(SortOption.VIEWS), views = { it.views })),
        )
    }

    @Test
    fun `titles ignore case and start A-Z`() {
        assertEquals(SortState(SortOption.TITLE, ascending = true), defaultSort(SortOption.TITLE))
        assertEquals(
            listOf("A", "b", "c"),
            names(sortList(rows, defaultSort(SortOption.TITLE), title = { it.name })),
        )
    }

    @Test
    fun `duration never has a missing case and sorts descending by default`() {
        assertEquals(
            listOf("A", "b", "c"),
            names(sortList(rows, SortState(SortOption.DURATION), duration = { it.duration })),
        )
    }

    @Test
    fun `every non-title option starts at its largest value`() {
        SortOption.entries
            .filter { it != SortOption.TITLE }
            .forEach { assertEquals(it.name, false, defaultSort(it).ascending) }
    }

    @Test
    fun `toggling only flips the direction`() {
        val state = SortState(SortOption.LIKES)
        assertEquals(SortState(SortOption.LIKES, ascending = true), state.toggled())
        assertEquals(state, state.toggled().toggled())
    }
}
