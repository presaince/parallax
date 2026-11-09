/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Data offers what the import can actually read. The merge is where a stale remembered file could
 * hide a real one, so it is pinned: folder first, newest first, one row per URI, junk filtered out.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFilesTest {

    private fun file(name: String, modified: Long = 0L, folder: Boolean = true) = FoundFile(
        uri = Uri.parse("content://test/$name"),
        name = name,
        modified = modified,
        inFolder = folder,
    )

    @Test
    fun `only the three shapes are offered`() {
        assertTrue(BackupFiles.visible("parallax-backup.json"))
        assertTrue(BackupFiles.visible("newpipe.ZIP"))
        assertTrue(BackupFiles.visible("subscriptions.csv"))
        assertFalse(BackupFiles.visible("photo.jpg"))
        assertFalse(BackupFiles.visible("notes.txt"))
    }

    @Test
    fun `the folder comes first, newest first, and a duplicate is offered once`() {
        val folder = listOf(file("old.json", modified = 1), file("new.json", modified = 9))
        val known = listOf(
            file("new.json", folder = false),
            file("mine.json", folder = false),
        )

        val merged = BackupFiles.merge(folder, known)

        assertEquals(listOf("new.json", "old.json", "mine.json"), merged.map { it.name })
        assertTrue(merged.first().inFolder)
        assertFalse(merged.last().inFolder)
    }

    @Test
    fun `a folder of photos is not a folder of backups`() {
        val merged = BackupFiles.merge(
            folder = listOf(file("backup.json"), file("photo.jpg")),
            known = emptyList(),
        )

        assertEquals(listOf("backup.json"), merged.map { it.name })
    }

    @Test
    fun `the list is capped so the page stays a page`() {
        val many = (1..12).map { file("backup$it.json", modified = it.toLong()) }

        assertEquals(8, BackupFiles.merge(many, emptyList()).size)
    }
}
