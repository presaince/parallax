/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

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
}
