/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSectionTest {

    @Test
    fun `an empty pref is the default page`() {
        assertEquals(DefaultSectionOrder, resolveSectionOrder(emptyList()))
        assertEquals(DefaultSectionOrder, resolveSectionOrder(listOf("", " ")))
    }

    @Test
    fun `the description is first until the reader moves it`() {
        assertEquals(PlayerSection.Description, DefaultSectionOrder.first())
    }

    @Test
    fun `a stored order is kept, name for name`() {
        val stored = listOf("Comments", "Description", "UpNext")
        val order = resolveSectionOrder(stored)
        assertEquals(
            listOf(
                PlayerSection.Comments,
                PlayerSection.Description,
                PlayerSection.UpNext,
                // Everything the pref did not mention follows, in its default order.
                PlayerSection.Chapters,
                PlayerSection.Sponsor,
                PlayerSection.Notes,
            ),
            order,
        )
    }

    @Test
    fun `a name from another build is dropped and cannot lose a section`() {
        val order = resolveSectionOrder(listOf("Playlists", "Notes"))
        assertEquals(6, order.size)
        assertEquals(PlayerSection.Notes, order.first())
        assertEquals(DefaultSectionOrder.toSet(), order.toSet())
    }

    @Test
    fun `a duplicated name keeps its first place`() {
        val order = resolveSectionOrder(listOf("Notes", "Notes", "Comments"))
        assertEquals(6, order.size)
        assertEquals(PlayerSection.Notes, order.first())
        assertEquals(PlayerSection.Comments, order[1])
    }

    @Test
    fun `the pref round trip is lossless`() {
        val order = listOf(
            PlayerSection.UpNext,
            PlayerSection.Comments,
            PlayerSection.Sponsor,
            PlayerSection.Notes,
            PlayerSection.Chapters,
            PlayerSection.Description,
        )
        val stored = sectionOrderPref(order).split(",")
        assertEquals(order, resolveSectionOrder(stored))
    }
}
