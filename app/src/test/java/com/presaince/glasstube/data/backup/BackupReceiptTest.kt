/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An export says what it wrote, in the same shape the import says what it restored. The sentence
 * is the only evidence the reader gets that the file is not empty, so the join and the plurals are
 * pinned here rather than left to string concatenation at the call site.
 */
class BackupReceiptTest {

    @Test
    fun `three parts read as a list with a final and`() {
        assertEquals(
            "Saved 38 subscriptions, 34 settings and 2 recipes",
            Backup.receipt(subscriptions = 38, settings = 34, recipes = 2),
        )
    }

    @Test
    fun `a part with nothing in it is left out`() {
        assertEquals(
            "Saved 3 subscriptions and 1 setting",
            Backup.receipt(subscriptions = 3, settings = 1, recipes = 0),
        )
        assertEquals(
            "Saved 1 recipe",
            Backup.receipt(subscriptions = 0, settings = 0, recipes = 1),
        )
    }

    @Test
    fun `an empty file still says something true`() {
        assertEquals(
            "Backup saved",
            Backup.receipt(subscriptions = 0, settings = 0, recipes = 0),
        )
    }

    @Test
    fun `notes, plays and resume points are counted like everything else`() {
        assertEquals(
            "Saved 38 subscriptions, 34 settings, 2 recipes, 12 notes, 40 plays and 7 resume points",
            Backup.receipt(
                subscriptions = 38,
                settings = 34,
                recipes = 2,
                notes = 12,
                history = 40,
                progress = 7,
            ),
        )
        assertEquals(
            "Saved 1 note",
            Backup.receipt(subscriptions = 0, settings = 0, recipes = 0, notes = 1),
        )
        assertEquals(
            "Saved 1 resume point",
            Backup.receipt(subscriptions = 0, settings = 0, recipes = 0, progress = 1),
        )
    }
}
