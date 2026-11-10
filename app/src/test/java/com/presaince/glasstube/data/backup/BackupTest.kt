/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import com.presaince.glasstube.data.db.HistoryEntity
import com.presaince.glasstube.data.db.NoteEntity
import com.presaince.glasstube.data.db.ProgressEntity
import com.presaince.glasstube.data.importer.ImportedSubscription
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the file carries, and the one rule a restore obeys twice: the same file restored again
 * adds nothing. Both are pure, so both are pinned here rather than on a phone.
 */
class BackupTest {

    private val notes = listOf(
        NoteEntity(id = 7, videoId = "abc", atSec = 91, text = "here", createdAt = 1_000, kind = "deck"),
        NoteEntity(id = 9, videoId = "abc", atSec = 12, text = "earlier", createdAt = 900),
    )

    private val history = listOf(
        HistoryEntity(
            id = 3,
            videoId = "abc",
            title = "A video",
            channelName = "A channel",
            openedAt = 5_000,
            watchedAt = 9_000,
            thumbUrl = null,
        ),
    )

    private fun backup(): JSONObject = JSONObject(
        Backup.json(
            settings = JSONObject().put("theme", "glass"),
            subscriptions = listOf(ImportedSubscription(url = "https://youtube.com/c/one", name = "one")),
            recipes = emptyList(),
            notes = notes,
            history = history,
            progress = progress,
        ),
    )

    private val progress = listOf(
        ProgressEntity(
            videoId = "abc",
            title = "A video",
            positionMs = 120_000,
            durationMs = 600_000,
            updatedAt = 7_000,
            thumbUrl = null,
        ),
    )

    @Test
    fun `where a video stopped is in the file`() {
        val row = backup().getJSONArray("progress").getJSONObject(0)

        assertEquals("abc", row.getString("videoId"))
        assertEquals("A video", row.getString("title"))
        assertEquals(120_000, row.getLong("positionMs"))
        assertEquals(600_000, row.getLong("durationMs"))
        assertEquals(7_000, row.getLong("updatedAt"))
        assertTrue(row.isNull("thumbUrl"))
    }

    @Test
    fun `notes and plays are in the file`() {
        val root = backup()
        assertEquals(2, root.getJSONArray("notes").length())
        assertEquals(1, root.getJSONArray("history").length())

        val note = root.getJSONArray("notes").getJSONObject(0)
        assertEquals("abc", note.getString("videoId"))
        assertEquals(91, note.getInt("atSec"))
        assertEquals("here", note.getString("text"))
        assertEquals("deck", note.getString("kind"))

        val play = root.getJSONArray("history").getJSONObject(0)
        assertEquals("A video", play.getString("title"))
        assertEquals(5_000, play.getLong("openedAt"))
        assertEquals(9_000, play.getLong("watchedAt"))
        assertTrue(play.isNull("thumbUrl"))
    }

    @Test
    fun `row ids stay on the phone they came from`() {
        val note = backup().getJSONArray("notes").getJSONObject(0)
        val play = backup().getJSONArray("history").getJSONObject(0)
        assertFalse(note.has("id"))
        assertFalse(play.has("id"))
    }

    @Test
    fun `restoring the same file twice adds nothing the second time`() {
        val existing = notes.mapTo(HashSet()) { Backup.noteKey(it) }
        val added = Backup.fresh(existing, notes) { Backup.noteKey(it) }
        assertTrue(added.isEmpty())
    }

    @Test
    fun `a note that is not on the phone yet is the only one added`() {
        val existing = setOf(Backup.noteKey(notes[0]))
        val added = Backup.fresh(existing, notes) { Backup.noteKey(it) }
        assertEquals(1, added.size)
        assertEquals(12, added.single().atSec)
    }
}
