/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker no longer asks the reader what they are holding. These pin that the file is read by
 * what is in it, and that the shapes which share a key — a backup has subscriptions, a recipe has
 * a version — are told apart in the right order.
 */
class BackupReaderTest {

    @Test
    fun `a combined backup is recognised before the subscriptions inside it`() {
        val json = JSONObject()
            .put("app", "parallax")
            .put("kind", "backup")
            .put("version", 1)
            .put("subscriptions", JSONObject().put("subscriptions", JSONArray()))
            .put("settings", JSONObject())
            .put("recipes", JSONArray())

        assertTrue(BackupReader.read(json.toString()) is BackupFile.Combined)
    }

    @Test
    fun `a settings file on its own is still a settings file`() {
        val json = JSONObject()
            .put("app", "parallax")
            .put("version", 2)
            .put("appearance", JSONObject())

        assertTrue(BackupReader.read(json.toString()) is BackupFile.Settings)
    }

    @Test
    fun `a subscription list is read in either wrapper`() {
        val newPipe = JSONObject()
            .put("app_version", "0.1.0")
            .put("subscriptions", JSONArray())
        assertTrue(BackupReader.read(newPipe.toString()) is BackupFile.Subscriptions)

        val bareArray = """[{"url":"https://www.youtube.com/@a","name":"A"}]"""
        assertTrue(BackupReader.read(bareArray) is BackupFile.Subscriptions)
    }

    @Test
    fun `a takeout spreadsheet is read without being JSON`() {
        val csv = "Channel Id,Channel Url\nabc,https://www.youtube.com/channel/abc\n"
        assertTrue(BackupReader.read(csv) is BackupFile.Subscriptions)
    }

    @Test
    fun `a recipe is not mistaken for a backup`() {
        val recipe = JSONObject()
            .put("version", 1)
            .put("name", "Test")
            .put("from", JSONObject())
            .put("walk", JSONObject())

        assertTrue(BackupReader.read(recipe.toString()) is BackupFile.Recipe)
    }

    @Test
    fun `an empty or foreign file says why`() {
        assertTrue(BackupReader.read("   ") is BackupFile.Unknown)
        assertTrue(BackupReader.read("hello") is BackupFile.Unknown)
        assertTrue(BackupReader.read("""{"hello":1}""") is BackupFile.Unknown)
    }
}
